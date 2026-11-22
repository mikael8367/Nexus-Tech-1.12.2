package com.mikael8367.nexustech.crusher;

import java.lang.reflect.Field;
import java.util.Random;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.passive.EntityCow;
import net.minecraft.init.Bootstrap;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import net.minecraft.world.WorldProviderSurface;
import net.minecraft.world.chunk.IChunkProvider;
import sun.misc.Unsafe;

/** Calls real Forge-mapped base death ticks in a minimal world, not a server. */
public final class CrusherVanillaXpPathTest {
    static final class TestWorld extends World {
        GameRules rules;
        int xp;
        TestWorld() { super(null, null, null, null, false); }
        protected IChunkProvider createChunkProvider() { return null; }
        protected boolean isChunkLoaded(int x, int z, boolean allowEmpty) { return true; }
        public GameRules getGameRules() { return rules; }
        public boolean spawnEntity(Entity entity) {
            if (entity instanceof EntityXPOrb) xp += ((EntityXPOrb) entity).getXpValue();
            return true;
        }
        public void spawnParticle(EnumParticleTypes p, double x, double y, double z,
                                  double a, double b, double c, int... params) {}
    }
    static final class TestCow extends EntityCow {
        TestCow(World world) { super(world); }
        void tickDeath() { onDeathUpdate(); }
        void recentlyHit(int n) { recentlyHit = n; }
    }
    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    static TestWorld world(boolean remote, boolean loot) throws Exception {
        Field f = Unsafe.class.getDeclaredField("theUnsafe"); f.setAccessible(true);
        Unsafe u = (Unsafe) f.get(null);
        TestWorld w = (TestWorld) u.allocateInstance(TestWorld.class);
        u.putBoolean(w, u.objectFieldOffset(World.class.getField("isRemote")), remote);
        u.putObject(w, u.objectFieldOffset(World.class.getField("provider")), new WorldProviderSurface());
        u.putObject(w, u.objectFieldOffset(World.class.getField("rand")), new Random(1));
        w.rules = new GameRules();
        w.rules.setOrCreateGameRule("doMobLoot", loot ? "true" : "false");
        return w;
    }
    static int death(boolean remote, boolean loot, int hit, boolean child) throws Exception {
        TestWorld w = world(remote, loot);
        TestCow cow = new TestCow(w); cow.recentlyHit(hit);
        if (child) cow.setGrowingAge(-100);
        for (int i = 0; i < 19; i++) cow.tickDeath();
        check(w.xp == 0, "XP must be deferred until tick 20");
        cow.tickDeath();
        int result = w.xp;
        cow.tickDeath();
        check(w.xp == result, "no second base-path drop on tick 21");
        return result;
    }
    public static void main(String[] args) throws Exception {
        Bootstrap.register();
        check(death(false, true, 0, false) == 0, "no recent player hit");
        int animalXp = death(false, true, 1, false);
        check(animalXp >= 1 && animalXp <= 3, "recent player hit uses cow's random XP path: " + animalXp);
        check(death(false, false, 1, false) == 0, "doMobLoot false");
        check(death(true, true, 1, false) == 0, "client");
        check(death(false, true, 1, true) == 0, "baby");
        System.out.println("PASS real mapped base onDeathUpdate: 5 gate scenarios, tick 19/20/21");
    }
}
