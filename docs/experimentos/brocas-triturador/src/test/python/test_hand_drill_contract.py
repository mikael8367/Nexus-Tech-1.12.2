import json
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
ASSETS = ROOT / 'src/main/resources/assets/nexustech'
SRC = ROOT / 'src/main/java/com/mikael8367/nexustech/techtools'

class HandDrillContract(unittest.TestCase):
    def test_recipes_models_and_names(self):
        registry = (SRC / 'TechToolsRegistry.java').read_text()
        client = (SRC / 'TechToolsClient.java').read_text()
        for size, metal in ((3, 'copper'), (5, 'tin'), (10, 'silver')):
            name = 'hand_drill_' + str(size)
            self.assertIn('drill("%s", %s)' % (name, size), registry)
            self.assertIn('TechToolsRegistry.DRILL_' + str(size), client)
            recipe = json.loads((ASSETS / 'recipes' / (name + '.json')).read_text())
            self.assertEqual(recipe['result']['item'], 'nexustech:' + name)
            self.assertEqual(recipe['key']['F']['item'], 'nexustech:techitem_' + metal + '_frame')
            for row in recipe['pattern']:
                for symbol in row.replace(' ', ''):
                    item = recipe['key'][symbol]['item']
                    if item.startswith('nexustech:'):
                        self.assertTrue((ASSETS / 'recipes' / (item.split(':')[1] + '.json')).exists(), item)
            model = json.loads((ASSETS / 'models/item' / (name + '.json')).read_text())
            self.assertGreaterEqual(len(model['elements']), 5)
            for texture in model['textures'].values():
                self.assertTrue((ROOT / 'src/main/texture_sources/assets/nexustech/textures' / (texture.split(':')[1] + '.png.b64')).exists())
            for lang in ('pt_br', 'en_us'):
                text = (ASSETS / 'lang' / (lang + '.lang')).read_text()
                self.assertEqual(text.count('item.' + name + '.name='), 1)
                self.assertEqual(text.count('tooltip.nexustech.hand_drill.disabled='), 1)
    def test_fail_closed_and_safety_gates(self):
        q = (SRC / 'HandDrillQueue.java').read_text()
        for gate in ('ENABLED = false', 'PLAYER_BUDGET = 8, SERVER_BUDGET = 64',
                     'if (!ENABLED || p.capabilities.isCreativeMode) return;',
                     'isBlockLoaded(pos)', 'getWorldBorder().contains(pos)',
                     'isBlockModifiable(p, pos)', 'p.canPlayerEdit(pos',
                     'hasTileEntity(state)', 'getTileEntity(pos) != null',
                     'ForgeHooks.canHarvestBlock', 'ToolEnergy.allowed',
                     'loadedRectangle', 'rayTraceBlocks', 'tryHarvestBlock(pos)',
                     'p.getHeldItemMainhand() != j.stack'):
            self.assertIn(gate, q)
        self.assertLess(q.index('ForgeHooks.canHarvestBlock'), q.index('tryHarvestBlock(pos)'))
        self.assertNotIn('setBlockToAir', q)
        self.assertNotIn('destroyBlock(', q)

if __name__ == '__main__':
    unittest.main()
