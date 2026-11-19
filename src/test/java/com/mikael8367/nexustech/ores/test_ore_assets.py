#!/usr/bin/env python3
"""Executable asset invariants; run from project root (Pillow required)."""
import base64, hashlib, io, json, unittest
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[7]
ASSETS = ROOT / 'src/main/resources/assets/nexustech'
TEXTURES = ROOT / 'src/main/texture_sources/assets/nexustech/textures'
METALS = 'copper tin aluminum zinc nickel silver lead uranium titanium tungsten'.split()

class OreAssetsTest(unittest.TestCase):
    def test_models_and_textures(self):
        hashes = set()
        for name in METALS:
            ore, ingot = name + '_ore', name + '_ingot'
            state = json.loads((ASSETS / 'blockstates' / (ore+'.json')).read_text())
            self.assertEqual(state['variants']['normal']['model'], 'nexustech:'+ore)
            model = json.loads((ASSETS / 'models/block' / (ore+'.json')).read_text())
            self.assertEqual(model['textures']['all'], 'nexustech:blocks/'+ore)
            self.assertEqual(json.loads((ASSETS / 'models/item' / (ore+'.json')).read_text())['parent'], 'nexustech:block/'+ore)
            self.assertEqual(json.loads((ASSETS / 'models/item' / (ingot+'.json')).read_text())['textures']['layer0'], 'nexustech:items/'+ingot)
        for folder, names in [('blocks',[m+'_ore' for m in METALS]), ('items',[m+'_ingot' for m in METALS]+['steel_ingot'])]:
            for name in names:
                data=base64.b64decode((TEXTURES/folder/(name+'.png.b64')).read_text().strip(), validate=True)
                image=Image.open(io.BytesIO(data)); image.load()
                self.assertEqual(image.size,(16,16)); self.assertEqual(image.format,'PNG')
                self.assertNotIn(hashlib.sha256(data).hexdigest(),hashes)
                hashes.add(hashlib.sha256(data).hexdigest())
                self.assertGreaterEqual(len(set(image.getdata())), 6)
        self.assertEqual(len(hashes),21)
    def test_steel_recipe(self):
        recipe=json.loads((ASSETS/'recipes/steel_ingot_from_iron_and_coal.json').read_text())
        self.assertEqual(recipe['type'],'forge:ore_shaped')
        self.assertEqual(''.join(recipe['pattern']).count('I'),4)
        self.assertEqual(''.join(recipe['pattern']).count('C'),1)
        self.assertEqual(recipe['key']['I']['ore'],'ingotIron')
        self.assertEqual(recipe['key']['C'],{'item':'minecraft:coal','data':0})
        self.assertEqual(recipe['result'],{'item':'nexustech:steel_ingot','count':4})
        self.assertEqual(json.loads((ASSETS/'models/item/steel_ingot.json').read_text())['textures']['layer0'],'nexustech:items/steel_ingot')

if __name__ == '__main__': unittest.main()
