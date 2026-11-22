"""Evidence checks against Forge 1.12.2 mapped bytecode, not a game simulation.

Run with FORGE_CP and JAVAP set to a mapped Forge classpath and Java 8 javap.
These checks intentionally do not prove that a future XP implementation is safe.
"""
import os
import pathlib
import re
import subprocess
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[7]


def javap(cls):
    return subprocess.check_output(
        [os.environ['JAVAP'], '-classpath', os.environ['FORGE_CP'], '-c', '-p', cls],
        universal_newlines=True)


def method(dump, signature):
    lines = dump.splitlines()
    start = next(i for i, line in enumerate(lines) if signature in line and line.startswith('  '))
    end = next((i for i in range(start + 1, len(lines))
                if re.match(r'^  (public|protected|private) ', lines[i])), len(lines))
    return '\n'.join(lines[start:end])


class XpEvidence(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.base = javap('net.minecraft.entity.EntityLivingBase')
        cls.living = javap('net.minecraft.entity.EntityLiving')
        cls.dragon = javap('net.minecraft.entity.boss.EntityDragon')

    def test_xp_is_deferred_and_existing_player_hit_can_already_produce_it(self):
        body = method(self.base, 'void onDeathUpdate()')
        for part in ('Field deathTime:I', 'bipush        20', 'Field recentlyHit:I',
                     'Method canDropLoot:()Z', 'String doMobLoot',
                     'Method getExperiencePoints:', 'ForgeEventFactory.getExperienceDrop:',
                     'EntityXPOrb', 'World.spawnEntity:'):
            self.assertIn(part, body, part)
        self.assertLess(body.index('Field recentlyHit:I'), body.index('Method getExperiencePoints:'))
        self.assertLess(body.index('ForgeEventFactory.getExperienceDrop:'), body.index('World.spawnEntity:'))

    def test_mob_xp_event_cannot_bypass_vanilla_gates(self):
        body = method(self.base, 'void onDeathUpdate()')
        gate = body.index('Field recentlyHit:I')
        event = body.index('ForgeEventFactory.getExperienceDrop:')
        self.assertLess(gate, body.index('Method canDropLoot:'))
        self.assertLess(body.index('String doMobLoot'), event)
        # Both failing the recent-hit and gamerule gates branch past the event.
        self.assertRegex(body[gate:event], r'ifle\s+133')
        self.assertRegex(body[gate:event], r'ifeq\s+133')
        self.assertIn('protected int recentlyHit;', self.base)
        self.assertIn('protected net.minecraft.entity.player.EntityPlayer attackingPlayer;', self.base)

    def test_experience_event_cancellation_returns_zero(self):
        body = method(javap('net.minecraftforge.event.ForgeEventFactory'), 'int getExperienceDrop(')
        self.assertIn('LivingExperienceDropEvent', body)
        self.assertRegex(body, r'EventBus.post:[^\n]*\n\s*18: ifeq\s+23\n\s*21: iconst_0\n\s*22: ireturn')
        self.assertIn('LivingExperienceDropEvent.getDroppedExperience:', body)

    def test_experiment_is_unimplemented_unwired_and_cannot_spawn_extra_xp(self):
        base = ROOT / 'src/main/java/com/mikael8367/nexustech/crusher'
        experiment = (base / 'CrusherXpExperiment.java').read_text(encoding='utf-8')
        self.assertIn('static final boolean IMPLEMENTED = false;', experiment)
        self.assertNotIn('@SubscribeEvent', experiment)
        self.assertNotIn('EntityXPOrb', experiment)
        self.assertNotIn('FakePlayer', experiment)
        for source in base.glob('*.java'):
            if source.name != 'CrusherXpExperiment.java':
                self.assertNotIn('CrusherXpExperiment', source.read_text(encoding='utf-8'), source.name)

    def test_cancelled_death_can_return_before_the_dead_flag_is_set(self):
        body = method(self.base, 'void onDeath(net.minecraft.util.DamageSource)')
        self.assertLess(body.index('ForgeHooks.onLivingDeath:'), body.index('Field dead:Z'))
        self.assertIn('return', body[body.index('ForgeHooks.onLivingDeath:'):body.index('Field dead:Z')])

    def test_xp_is_mob_specific_not_a_single_constant(self):
        body = method(self.living, 'int getExperiencePoints(')
        self.assertIn('Field experienceValue:I', body)
        self.assertIn('Field inventoryArmor', body)
        self.assertIn('Field inventoryHands', body)
        self.assertIn('Random.nextInt:', body)
        self.assertIn('protected int getExperiencePoints(', self.base)
        self.assertIn('protected boolean canDropLoot()', self.base)

    def test_special_boss_has_own_death_path(self):
        body = method(self.dragon, 'void onDeathUpdate()')
        self.assertIn('Field deathTicks:I', body)
        self.assertNotIn('Field recentlyHit:I', body)

    def test_crusher_does_not_create_blind_xp_or_fake_player(self):
        base = ROOT / 'src/main/java/com/mikael8367/nexustech/crusher'
        tile = (base / 'CrusherTile.java').read_text(encoding='utf-8')
        damage = (base / 'CrusherDamage.java').read_text(encoding='utf-8')
        self.assertIn('e.attackEntityFrom(source,', tile)
        self.assertIn('new CrusherDamage(', tile)
        self.assertNotIn('EntityXPOrb', tile + damage)
        self.assertNotIn('FakePlayer', tile + damage)
        self.assertNotIn('causePlayerDamage', damage)


if __name__ == '__main__':
    unittest.main(verbosity=2)
