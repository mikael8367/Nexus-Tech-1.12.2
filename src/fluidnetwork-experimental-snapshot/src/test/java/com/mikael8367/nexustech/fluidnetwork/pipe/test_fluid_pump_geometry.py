"""Static checks for the experimental Forge 1.12.2 pump/pipe models.

Does not render Minecraft, exercise culling in-game, or enable the fluid network.
"""
import itertools
import json
import unittest
from pathlib import Path

ROOT = next(p for p in Path(__file__).resolve().parents
            if (p / 'src/fluidnetwork-staged-resources').is_dir())
ASSETS = ROOT / 'src/fluidnetwork-staged-resources/assets/nexustech'
MODELS = ASSETS / 'models/block'
DIRS = {'west': (0, 0), 'east': (0, 1), 'down': (1, 0),
        'up': (1, 1), 'north': (2, 0), 'south': (2, 1)}
OPPOSITE = {'west': 'east', 'east': 'west', 'down': 'up',
            'up': 'down', 'north': 'south', 'south': 'north'}
CLOCKWISE = {'north': 'east', 'east': 'south', 'south': 'west',
             'west': 'north', 'up': 'up', 'down': 'down'}


def load(path):
    return json.loads(path.read_text(encoding='utf-8'))


def rotate(element, turns):
    lo, hi = list(element['from']), list(element['to'])
    faces = dict(element['faces'])
    for _ in range(turns):
        lo, hi = [16-hi[2], lo[1], lo[0]], [16-lo[2], hi[1], hi[0]]
        faces = {CLOCKWISE[f]: data for f, data in faces.items()}
    return {'from': lo, 'to': hi, 'faces': faces}


def face(element, direction):
    axis, high = DIRS[direction]
    other = [i for i in range(3) if i != axis]
    return (element['to' if high else 'from'][axis],
            tuple((element['from'][i], element['to'][i]) for i in other))


def overlap(a, b):
    return all(max(x[0], y[0]) < min(x[1], y[1]) for x, y in zip(a, b))


def connected(a, b):
    widths = [min(a['to'][i], b['to'][i]) - max(a['from'][i], b['from'][i])
              for i in range(3)]
    return all(w >= 0 for w in widths) and sum(w == 0 for w in widths) <= 1


class PumpGeometryTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.pump = load(MODELS / 'fluid_pump.json')
        cls.states = load(ASSETS / 'blockstates/fluid_pump.json')['variants']
        cls.pipes = {d: load(MODELS / ('fluid_pipe_' + d + '.json')) for d in DIRS}

    def test_blockstate_rotations(self):
        self.assertEqual(set(self.states), {'facing=north', 'facing=east',
                                            'facing=south', 'facing=west'})
        for direction, degrees in [('north', 0), ('east', 90), ('south', 180), ('west', 270)]:
            state = self.states['facing=' + direction]
            self.assertEqual(state['model'], 'nexustech:fluid_pump')
            self.assertEqual(state.get('y', 0), degrees)
            self.assertNotIn('x', state)

    def test_valid_geometry_textures_and_no_conditional_culling(self):
        textures = self.pump['textures']
        self.assertEqual(self.pump['parent'], 'minecraft:block/block')
        self.assertEqual(textures, {'particle': 'nexustech:blocks/fluid_pump_side',
                                    'side': 'nexustech:blocks/fluid_pump_side',
                                    'front': 'nexustech:blocks/fluid_pump_front'})
        self.assertEqual(len(self.pump['elements']), 10)
        for e in self.pump['elements']:
            self.assertNotIn('rotation', e)
            for lo, hi in zip(e['from'], e['to']):
                self.assertLessEqual(0, lo)
                self.assertLess(lo, hi)
                self.assertLessEqual(hi, 16)
            for d, f in e['faces'].items():
                self.assertIn(d, DIRS)
                self.assertIn(f['texture'], ('#side', '#front'))
                self.assertNotIn('cullface', f)
                self.assertEqual(len(f['uv']), 4)
                self.assertTrue(all(0 <= n <= 16 for n in f['uv']))

    def test_preserve_existing_details_and_new_inward_faces_omitted(self):
        elements = self.pump['elements']
        expected = [([2,2,2],[14,14,14]), ([4,4,0],[12,12,2.5]),
                    ([1,0.5,3],[15,2.5,13]), ([1,4,4],[2.5,12,12]),
                    ([13.5,4,4],[15,12,12]), ([6,6,13.5],[10,10,16]),
                    ([6,13.5,6],[10,16,10]), ([0,6,6],[1,10,10]),
                    ([15,6,6],[16,10,10]), ([6,0,6],[10,0.5,10])]
        self.assertEqual([(e['from'], e['to']) for e in elements], expected)
        self.assertEqual(elements[1]['faces']['north']['texture'], '#front')
        for e, inner in zip(elements[7:], ('east', 'west', 'up')):
            self.assertNotIn(inner, e['faces'])
            self.assertEqual(set(e['faces']), set(DIRS) - {inner})

    def test_no_coplanar_overlapping_faces_in_any_rotation(self):
        for turns in range(4):
            es = [rotate(e, turns) for e in self.pump['elements']]
            for (i, a), (j, b) in itertools.combinations(enumerate(es), 2):
                for da in a['faces']:
                    for db in b['faces']:
                        if DIRS[da][0] != DIRS[db][0]:
                            continue
                        plane_a, rect_a = face(a, da)
                        plane_b, rect_b = face(b, db)
                        self.assertFalse(plane_a == plane_b and overlap(rect_a, rect_b),
                                         (turns, i, da, j, db))

    def test_all_rotations_six_matching_connected_ports(self):
        for turns in range(4):
            es = [rotate(e, turns) for e in self.pump['elements']]
            # All elements must join the body through a positive-area face or volume.
            reached = {0}
            while True:
                new = {j for i in reached for j in range(len(es)) if connected(es[i], es[j])}
                if new <= reached:
                    break
                reached |= new
            self.assertEqual(reached, set(range(len(es))), turns)
            for d, (axis, high) in DIRS.items():
                boundary = 16 if high else 0
                ports = [face(e, d)[1] for e in es if d in e['faces'] and face(e, d)[0] == boundary]
                # Front is wider (decorative 8x8); its central 4x4 remains aligned.
                self.assertTrue(any(all(lo <= 6 and hi >= 10 for lo, hi in rect)
                                    for rect in ports), (turns, d, ports))
                pipe_dir = OPPOSITE[d]
                pipe = self.pipes[pipe_dir]['elements'][0]
                pipe_boundary = 0 if high else 16
                self.assertIn(pipe_dir, pipe['faces'])
                self.assertNotIn('cullface', pipe['faces'][pipe_dir])
                plane, rect = face(pipe, pipe_dir)
                self.assertEqual((plane, rect), (pipe_boundary, ((6,10), (6,10))))
                # With both uncullable caps present, the interface has coincident
                # opposing faces; this known in-game z-fighting risk is not hidden.
                self.assertTrue(any(overlap(p, rect) for p in ports), (turns, d))


if __name__ == '__main__':
    unittest.main()
