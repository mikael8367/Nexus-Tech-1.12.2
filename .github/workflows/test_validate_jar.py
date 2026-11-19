import json
import struct
import subprocess
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path

import validate_jar as v


def fake_class(version='1.0', modid='nexustech', mc='[1.12.2]', dependency=None):
    """A small class-file header with string ConstantValue fields."""
    pool = []
    def utf(s):
        b = s.encode(); pool.append(b'\x01' + struct.pack('>H', len(b)) + b); return len(pool)
    def string(s):
        index = utf(s); pool.append(b'\x08' + struct.pack('>H', index)); return len(pool)
    name1, name2, desc, attr = map(utf, ('MODID', 'VERSION', 'Ljava/lang/String;', 'ConstantValue'))
    val1, val2 = string(modid), string(version)
    utf(mc)
    if dependency is not None: utf(dependency)
    field = lambda name, value: struct.pack('>HHHHHIH', 0x19, name, desc, 1, attr, 2, value)
    return b'\xca\xfe\xba\xbe' + struct.pack('>HHH', 0, 52, len(pool)+1) + b''.join(pool) + struct.pack('>HHHHH', 0, 0, 0, 0, 2) + field(name1, val1) + field(name2, val2)


class ValidatorTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.jar = self.root / 'test.jar'
        self.source = self.root / 'source'
        self.java = self.source / 'src/main/java/com/mikael8367/nexustech/NexusTech.java'
        self.gradle = self.source / 'build.gradle'
        self.resources = self.source / 'src/main/resources'
        self.meta = {'modid':'nexustech', 'version':'1.0', 'mcversion':'1.12.2', 'logoFile':'logo.png', 'dependencies':[]}
        source_meta = dict(self.meta, version='${version}', mcversion='${mcversion}')
        self.put(self.java, '@Mod(modid = NexusTech.MODID, version = NexusTech.VERSION, acceptedMinecraftVersions = "[1.12.2]")\nclass NexusTech { static final String MODID = "nexustech", VERSION = "1.0"; }')
        self.put(self.gradle, "version = '1.0'\ndef minecraftVersion = '1.12.2'\ndependencies { minecraft 'net.minecraftforge:forge:1.12.2-14.23.5.2860' }\n")
        self.put(self.resources / 'mcmod.info', json.dumps([source_meta]))
        self.model = 'assets/nexustech/models/block/cube.json'
        self.state = 'assets/nexustech/blockstates/cube.json'
        self.texture = 'assets/nexustech/textures/blocks/cube.png'
        self.class_name = 'com/mikael8367/nexustech/NexusTech.class'
        model = {'parent':'block/cube_all', 'textures':{'all':'nexustech:blocks/cube'}}
        state = {'variants':{'normal':{'model':'nexustech:cube'}}}
        self.put(self.resources / self.model, json.dumps(model))
        self.put(self.resources / self.state, json.dumps(state))
        self.files = {'mcmod.info':json.dumps([self.meta]).encode(), 'logo.png':v.PNG+b'logo', self.class_name:fake_class(), self.model:json.dumps(model).encode(), self.state:json.dumps(state).encode(), self.texture:v.PNG+b'texture'}
        self.pack()

    @staticmethod
    def put(path, text):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding='utf-8')

    def pack(self):
        with zipfile.ZipFile(self.jar, 'w', compression=zipfile.ZIP_STORED) as z:
            for name, value in self.files.items(): z.writestr(name, value)

    def check(self): return v.validate(self.jar, self.source, '1.0')
    def assert_error(self, text): self.assertTrue(any(text in err for err in self.check()), self.check())
    def update_json(self, filename, value):
        self.files[filename] = json.dumps(value).encode(); self.pack()
    def update_meta(self, **values):
        self.meta.update(values); self.update_json('mcmod.info', [self.meta])

    def test_valid(self): self.assertEqual(self.check(), [])
    def test_missing_jar(self):
        self.jar.unlink(); self.assert_error('missing or empty')
    def test_empty_jar_file(self):
        self.jar.write_bytes(b''); self.assert_error('missing or empty')
    def test_invalid_zip(self):
        self.jar.write_bytes(b'not a zip'); self.assert_error('invalid, corrupt')
    def test_empty_zip(self):
        self.files = {}; self.pack(); self.assert_error('no files')
    def test_crc_corruption(self):
        raw = self.jar.read_bytes().replace(v.PNG+b'texture', v.PNG+b'texturE')
        self.jar.write_bytes(raw); self.assert_error('CRC integrity')
    def test_duplicate_entries(self):
        with zipfile.ZipFile(self.jar, 'a') as z:
            import warnings
            with warnings.catch_warnings():
                warnings.simplefilter('ignore'); z.writestr('logo.png', v.PNG)
        self.assert_error('duplicate')
    def test_metadata_invalid_json(self):
        self.files['mcmod.info'] = b'{'; self.pack(); self.assert_error('invalid JSON')
    def test_metadata_version(self):
        self.update_meta(version='2.0'); self.assert_error('mcmod.info version')
    def test_metadata_minecraft(self):
        self.update_meta(mcversion='1.20'); self.assert_error('mcmod.info mcversion')
    def test_metadata_modid(self):
        self.update_meta(modid='other'); self.assert_error('exactly one Nexus Tech')
    def test_source_version(self):
        self.put(self.java, self.java.read_text().replace('VERSION = "1.0"', 'VERSION = "2.0"')); self.assert_error('NexusTech.VERSION')
    def test_gradle_version(self):
        self.put(self.gradle, self.gradle.read_text().replace("version = '1.0'", "version = '2.0'")); self.assert_error('build.gradle version')
    def test_gradle_forge_exact(self):
        self.put(self.gradle, self.gradle.read_text().replace('14.23.5.2860', '14.23.5.2859')); self.assert_error('Forge coordinate')
    def test_gradle_minecraft(self):
        self.put(self.gradle, self.gradle.read_text().replace("minecraftVersion = '1.12.2'", "minecraftVersion = '1.12'")); self.assert_error('Minecraft version')
    def test_compiled_version(self):
        self.files[self.class_name] = fake_class(version='0.9'); self.pack(); self.assert_error('compiled NexusTech.VERSION')
    def test_compiled_modid(self):
        self.files[self.class_name] = fake_class(modid='other'); self.pack(); self.assert_error('compiled NexusTech.MODID')
    def test_compiled_mc(self):
        self.files[self.class_name] = fake_class(mc='[1.10]'); self.pack(); self.assert_error('compiled NexusTech Minecraft')
    def test_compiled_corrupt(self):
        self.files[self.class_name] = b'\xca\xfe\xba\xbe\x00'; self.pack(); self.assert_error('invalid compiled')
    def test_missing_class(self):
        del self.files[self.class_name]; self.pack(); self.assert_error('missing com/')
    def test_missing_icon(self):
        del self.files['logo.png']; self.pack(); self.assert_error('missing logo.png')
    def test_icon_signature(self):
        self.files['logo.png'] = b'bad'; self.pack(); self.assert_error('logo is not a PNG')
    def test_dependency_matches(self):
        self.update_meta(dependencies=['othermod@[1.0,)']); self.assert_error('dependencies differs')
    def test_required_mods_match(self):
        self.update_meta(requiredMods=['othermod']); self.assert_error('requiredMods differs')
    def test_compiled_annotation_dependencies(self):
        self.put(self.java, self.java.read_text().replace('acceptedMinecraftVersions', 'dependencies = "required-after:other", acceptedMinecraftVersions'))
        self.assert_error('compiled @Mod dependencies')
        self.files[self.class_name] = fake_class(dependency='required-after:other'); self.pack()
        self.assertEqual(self.check(), [])
    def test_missing_texture(self):
        del self.files[self.texture]; self.pack(); self.assert_error('missing referenced assets/nexustech/textures')
    def test_texture_signature(self):
        self.files[self.texture] = b'bad'; self.pack(); self.assert_error('invalid PNG signature')
    def test_missing_parent(self):
        self.update_json(self.model, {'parent':'nexustech:block/absent'}); self.assert_error('missing referenced assets/nexustech/models')
    def test_missing_blockstate_model(self):
        self.update_json(self.state, {'variants':{'normal':{'model':'nexustech:absent'}}}); self.assert_error('missing referenced assets/nexustech/models')
    def test_multipart_model(self):
        self.update_json(self.state, {'multipart':[{'when':{'x':'true'},'apply':[{'model':'nexustech:absent'}]}]}); self.assert_error('missing referenced')
    def test_missing_model_file(self):
        del self.files[self.model]; self.pack(); self.assert_error('missing assets/nexustech/models')
    def test_missing_blockstate_file(self):
        del self.files[self.state]; self.pack(); self.assert_error('missing assets/nexustech/blockstates')
    def test_invalid_blockstate_json(self):
        self.files[self.state] = b'{'; self.pack(); self.assert_error('invalid JSON')
    def test_empty_blockstate(self):
        self.update_json(self.state, {}); self.assert_error('blockstate missing variants')
    def test_empty_variants(self):
        self.update_json(self.state, {'variants':{}}); self.assert_error('invalid or empty blockstate variants')
    def test_bad_multipart_type(self):
        self.update_json(self.state, {'multipart':{}}); self.assert_error('invalid or empty blockstate multipart')
    def test_override_local_model(self):
        self.update_json(self.model, {'overrides':[{'model':'nexustech:item/missing'}]}); self.assert_error('missing referenced assets/nexustech/models')
    def test_missing_source(self):
        errors = v.validate(self.jar, self.root / 'missing', '1.0'); self.assertTrue(any('source directory' in x for x in errors))
    def test_deflate_corruption(self):
        with zipfile.ZipFile(self.jar, 'w', zipfile.ZIP_DEFLATED) as z:
            z.writestr('broken', b'x' * 1000)
        raw = bytearray(self.jar.read_bytes())
        # Local header + filename; invalidate first deflate block.
        offset = 30 + len('broken')
        raw[offset] = 0xff
        self.jar.write_bytes(raw)
        self.assertTrue(self.check())
    def test_external_and_aliases(self):
        self.update_json(self.model, {'parent':'forge:block/whatever', 'textures':{'a':'#alias', 'b':'minecraft:blocks/stone', 'c':'othermod:blocks/x', 'd':'blocks/stone'}})
        self.update_json(self.state, {'variants':{'normal':[{'model':'minecraft:stone'}, {'model':'forge:custom'}, {'model':'othermod:test'}]}})
        self.assertEqual(self.check(), [])
    def test_bad_resource_type(self):
        self.update_json(self.model, {'parent':123}); self.assert_error('invalid resource reference')
    def test_cli_never_echoes_version(self):
        secret = 'secret-password-value'; script = Path(v.__file__)
        result = subprocess.run([sys.executable, str(script), '--jar', str(self.jar), '--source', str(self.source), '--version', secret], capture_output=True, text=True)
        self.assertNotEqual(result.returncode, 0); self.assertNotIn(secret, result.stdout + result.stderr)
    def test_cli_success(self):
        result = subprocess.run([sys.executable, str(Path(v.__file__)), '--jar', str(self.jar), '--source', str(self.source), '--version', '1.0'], capture_output=True, text=True)
        self.assertEqual(result.returncode, 0, result.stderr)


if __name__ == '__main__': unittest.main()
