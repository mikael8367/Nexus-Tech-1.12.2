#!/usr/bin/env python3
"""Static integration checks; run from any directory with Python 3 and Pillow.

These checks do not load Forge or prove that registration, recipes or GUIs work
in game. They validate local resources and the common integration entry points.
"""
import base64
import io
import json
import unittest
from pathlib import Path

from PIL import Image

ROOT = next(p for p in Path(__file__).resolve().parents if (p / 'build.gradle').exists())
ASSETS = ROOT / 'src/main/resources/assets/nexustech'
TEXTURES = ROOT / 'src/main/texture_sources/assets/nexustech/textures'
JAVA = ROOT / 'src/main/java/com/mikael8367/nexustech'


def no_duplicate_keys(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError('duplicate JSON key: ' + key)
        result[key] = value
    return result


def read_json(path):
    return json.loads(path.read_text(encoding='utf-8'), object_pairs_hook=no_duplicate_keys)


def walk(value):
    if isinstance(value, dict):
        yield value
        for child in value.values():
            yield from walk(child)
    elif isinstance(value, list):
        for child in value:
            yield from walk(child)


class IntegrationAssetsTest(unittest.TestCase):
    def test_all_json_is_well_formed_without_duplicate_keys(self):
        paths = list((ROOT / 'src/main/resources').rglob('*.json'))
        self.assertTrue(paths)
        for path in paths:
            with self.subTest(path=str(path.relative_to(ROOT))):
                read_json(path)

    def test_blockstates_resolve_local_models(self):
        for path in (ASSETS / 'blockstates').glob('*.json'):
            state = read_json(path)
            for node in walk(state):
                model = node.get('model')
                if isinstance(model, str) and model.startswith('nexustech:'):
                    model_path = model.split(':', 1)[1]
                    self.assertTrue((ASSETS / 'models/block' / (model_path + '.json')).is_file(),
                                    f'{path}: missing block model {model}')
            self.assertTrue((ASSETS / 'models/item' / path.name).is_file(),
                            f'{path}: missing block item model')

    def test_model_parents_and_encoded_textures_resolve(self):
        for path in (ASSETS / 'models').rglob('*.json'):
            model = read_json(path)
            parent = model.get('parent', '')
            if parent.startswith('nexustech:'):
                self.assertTrue((ASSETS / 'models' / (parent.split(':', 1)[1] + '.json')).is_file(),
                                f'{path}: missing parent {parent}')
            for texture in model.get('textures', {}).values():
                if texture.startswith('nexustech:'):
                    source = TEXTURES / (texture.split(':', 1)[1] + '.png.b64')
                    self.assertTrue(source.is_file(), f'{path}: missing texture {texture}')
        sources = list(TEXTURES.rglob('*.png.b64'))
        self.assertTrue(sources)
        for source in sources:
            with self.subTest(texture=str(source.relative_to(ROOT))):
                data = base64.b64decode(source.read_text(encoding='ascii').strip(), validate=True)
                with Image.open(io.BytesIO(data)) as image:
                    self.assertEqual(image.format, 'PNG')
                    image.verify()
                with Image.open(io.BytesIO(data)) as image:
                    image.load()

    def test_recipe_shapes_and_local_item_models(self):
        recipes = list((ASSETS / 'recipes').glob('*.json'))
        self.assertTrue(recipes)
        for path in recipes:
            with self.subTest(recipe=path.name):
                recipe = read_json(path)
                self.assertIn(recipe['type'], {'minecraft:crafting_shaped', 'minecraft:crafting_shapeless',
                                               'forge:ore_shaped', 'forge:ore_shapeless'})
                result = recipe['result']
                self.assertGreater(result.get('count', 1), 0)
                if 'pattern' in recipe:
                    pattern, keys = recipe['pattern'], recipe['key']
                    self.assertTrue(1 <= len(pattern) <= 3)
                    self.assertTrue(1 <= len(pattern[0]) <= 3)
                    self.assertTrue(all(len(row) == len(pattern[0]) for row in pattern))
                    self.assertEqual(set(''.join(pattern)) - {' '}, set(keys))
                else:
                    self.assertTrue(1 <= len(recipe['ingredients']) <= 9)
                for node in walk(recipe):
                    item = node.get('item')
                    if isinstance(item, str) and item.startswith('nexustech:'):
                        name = item.split(':', 1)[1]
                        self.assertTrue((ASSETS / 'models/item' / (name + '.json')).is_file(),
                                        f'{path}: missing item model for {item}')

    def test_languages_have_identical_unique_keys(self):
        languages = []
        for language in ('en_us', 'pt_br'):
            found = {}
            for line in (ASSETS / 'lang' / (language + '.lang')).read_text(encoding='utf-8').splitlines():
                if not line.strip() or line.lstrip().startswith('#'):
                    continue
                self.assertIn('=', line)
                key, value = line.split('=', 1)
                self.assertNotIn(key, found, f'{language}: duplicate {key}')
                self.assertTrue(value.strip(), f'{language}: empty {key}')
                found[key] = value
            languages.append(set(found))
        self.assertEqual(languages[0], languages[1])
        keys = languages[0]
        ore_names = set('copper tin aluminum zinc nickel silver lead uranium titanium tungsten'.split())
        for path in (ASSETS / 'blockstates').glob('*.json'):
            name = path.stem
            key = ('tile.' if name.endswith('_ore') and name[:-4] in ore_names
                   else 'tile.nexustech.') + name + '.name'
            self.assertIn(key, keys, f'missing block translation {key}')
        for name in ore_names | {'steel'}:
            self.assertIn('item.' + name + '_ingot.name', keys)
        self.assertIn('item.nexustech.nuclear_fuel.name', keys)
        for name in ('on', 'off', 'eco', 'fast'):
            self.assertIn('digital_mine.' + name, keys)
        for number in range(8):
            self.assertIn('digital_mine.status.' + str(number), keys)
        for name in ('button.on', 'button.off', 'production', 'heat', 'fuel', 'water',
                     'status.scram', 'status.unformed', 'status.on', 'status.off'):
            self.assertIn('nuclear.' + name, keys)

    def test_directional_state_coverage(self):
        directions = ('north', 'east', 'south', 'west')
        for name in ('digital_mine_basic', 'digital_mine_advanced'):
            variants = read_json(ASSETS / 'blockstates' / (name + '.json'))['variants']
            self.assertEqual(set(variants), {'facing=' + direction for direction in directions})
        for name in ('nuclear_controller', 'nuclear_casing', 'nuclear_core'):
            variants = read_json(ASSETS / 'blockstates' / (name + '.json'))['variants']
            self.assertEqual(set(variants), {'active=' + active + ',facing=' + direction
                                             for active in ('true', 'false') for direction in directions})

    def test_ore_registration_is_not_started_before_item_events(self):
        # Forge 1.12.2 dispatches preInit before RegistryEvent.Register<Item>.
        # This source check does not cover every possible indirect call.
        main = (JAVA / 'NexusTech.java').read_text(encoding='utf-8')
        preinit = main.split('void preInit(', 1)[1].split('}', 1)[0]
        self.assertNotIn('OreRegistry.preInit()', preinit,
                         'ore dictionary and smelting need registered ItemBlocks')
        self.assertNotIn('OreRegistry.init()', preinit)
        self.assertIn('OreRegistry.registerWorldGenerator()', preinit)
        init = main.split('void init(', 1)[1].split('}', 1)[0]
        self.assertIn('OreRegistry.init()', init)

    def test_single_gui_dispatch_and_preinit_hooks(self):
        main = (JAVA / 'NexusTech.java').read_text(encoding='utf-8')
        server = (JAVA / 'MachineGuiHandler.java').read_text(encoding='utf-8')
        client = (JAVA / 'ClientProxy.java').read_text(encoding='utf-8')
        self.assertEqual(main.count('registerGuiHandler('), 1)
        self.assertEqual(main.count('DigitalMineRegistry.registerTiles()'), 1)
        for code, routes in ((server, ('DigitalMineGuiHooks.server', 'NuclearGuiHooks.server')),
                             (client, ('DigitalMineClient.gui', 'NuclearClient.gui'))):
            for route in routes:
                self.assertEqual(code.count(route), 1)
            self.assertIn('id != 0', code)
        self.assertNotIn('DigitalMineClient', server)
        self.assertNotIn('NuclearClient', server)


if __name__ == '__main__':
    unittest.main()
