"""Static checks for staged 1.12.2 pipe assets; does not activate or render them.

Run: python3 -m unittest discover -s src/test/java/com/mikael8367/nexustech/fluidnetwork/pipe -p 'test_fluid_pipe_models.py'
"""
import itertools
import json
from pathlib import Path
import re
import struct
import unittest

ROOT = Path(__file__).resolve().parents[8]
ASSETS = ROOT / 'src/fluidnetwork-staged-resources/assets/nexustech'
SOURCE = ROOT / 'src/main/java/com/mikael8367/nexustech/fluidnetwork/pipe/FluidPipeBlock.java'
SIDES = ('north', 'south', 'east', 'west', 'up', 'down')
OPPOSITE = {'north': 'south', 'south': 'north', 'east': 'west',
            'west': 'east', 'up': 'down', 'down': 'up'}
BOXES = {
    'north': ([6, 6, 0], [10, 10, 4]),
    'south': ([6, 6, 12], [10, 10, 16]),
    'east': ([12, 6, 6], [16, 10, 10]),
    'west': ([0, 6, 6], [4, 10, 10]),
    'up': ([6, 12, 6], [10, 16, 10]),
    'down': ([6, 0, 6], [10, 4, 10]),
}


def no_duplicates(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError('duplicate JSON key: ' + key)
        result[key] = value
    return result


def load(relative):
    return json.loads((ASSETS / relative).read_text(encoding='utf-8'),
                      object_pairs_hook=no_duplicates)


class FluidPipeModels(unittest.TestCase):
    def test_java_declares_six_named_boolean_properties(self):
        text = SOURCE.read_text(encoding='utf-8')
        for side in SIDES:
            self.assertRegex(text, rf'PropertyBool\s+{side.upper()}\s*=\s*PropertyBool\.create\("{side}"\)')

    def test_multipart_all_64_masks(self):
        parts = load('blockstates/fluid_pipe.json')['multipart']
        self.assertEqual(len(parts), 7)
        self.assertEqual(parts[0], {'apply': {'model': 'nexustech:fluid_pipe'}})
        by_side = {}
        for part in parts[1:]:
            self.assertEqual(set(part), {'when', 'apply'})
            self.assertEqual(len(part['when']), 1)
            side, value = next(iter(part['when'].items()))
            self.assertIn(side, SIDES)
            self.assertNotIn(side, by_side)
            self.assertIs(type(value), str)  # vanilla 1.12 multipart property syntax
            self.assertEqual(value, 'true')
            self.assertEqual(part['apply'], {'model': 'nexustech:fluid_pipe_' + side})
            by_side[side] = part
        self.assertEqual(set(by_side), set(SIDES))
        for mask in itertools.product((False, True), repeat=6):
            state = dict(zip(SIDES, mask))
            resolved = [p['apply']['model'] for p in parts
                        if 'when' not in p or all(str(state[k]).lower() == v for k, v in p['when'].items())]
            expected = ['nexustech:fluid_pipe'] + ['nexustech:fluid_pipe_' + s for s in SIDES if state[s]]
            self.assertEqual(resolved, expected, state)
            for model in resolved:
                self.assertTrue((ASSETS / 'models/block' / (model.split(':')[1] + '.json')).is_file())

    def test_geometry_faces_and_texture_references(self):
        names = [''] + ['_' + side for side in SIDES]
        for suffix in names:
            with self.subTest(model=suffix):
                model = load('models/block/fluid_pipe' + suffix + '.json')
                self.assertEqual(model['parent'], 'minecraft:block/block')
                self.assertEqual(model['textures'], {'particle': 'nexustech:blocks/fluid_pipe', 'all': 'nexustech:blocks/fluid_pipe'})
                self.assertEqual(len(model['elements']), 1)
                e = model['elements'][0]
                expected = BOXES[suffix[1:]] if suffix else ([4, 4, 4], [12, 12, 12])
                self.assertEqual((e['from'], e['to']), expected)
                for a, b in zip(e['from'], e['to']):
                    self.assertTrue(0 <= a < b <= 16)
                expected_faces = set(SIDES) - ({OPPOSITE[suffix[1:]]} if suffix else set())
                self.assertEqual(set(e['faces']), expected_faces)
                # No cullface on inset hub faces; arms currently keep their boundary caps.
                for face in e['faces'].values():
                    self.assertEqual(face, {'texture': '#all'})

    def test_item_parent_and_png_header(self):
        self.assertEqual(load('models/item/fluid_pipe.json'), {'parent': 'nexustech:block/fluid_pipe'})
        data = (ASSETS / 'textures/blocks/fluid_pipe.png').read_bytes()
        self.assertEqual(data[:8], b'\x89PNG\r\n\x1a\n')
        self.assertEqual(data[12:16], b'IHDR')
        self.assertEqual(struct.unpack('>II', data[16:24]), (16, 16))


if __name__ == '__main__':
    unittest.main()
