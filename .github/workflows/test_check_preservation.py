import base64
import subprocess
import sys
import tempfile
import unittest
import warnings
import zipfile
from pathlib import Path

import check_preservation as check


class PreservationTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.resources = self.root / 'src/main/resources'
        self.textures = self.root / 'src/main/texture_sources'
        self.classes = self.root / 'build/classes/java/main'
        self.jar = self.root / 'out.jar'
        self.put(self.resources / 'mcmod.info', b'${version}')
        self.put(self.resources / 'assets/data.json', b'{"key": "value"}\n')
        self.put(self.resources / 'nested/mcmod.info', b'unchanged')
        self.put(self.textures / 'assets/mod/textures/x.png.b64', base64.b64encode(b'png bytes') + b'\n')
        self.put(self.classes / 'pkg/Main.class', b'compiled')
        self.put(self.classes / 'pkg/Main$Inner.class', b'compiled inner')
        self.entries = {
            'mcmod.info': b'1.0',
            'assets/data.json': b'{"key": "value"}\n',
            'nested/mcmod.info': b'unchanged',
            'assets/mod/textures/x.png': b'png bytes',
            'pkg/Main.class': b'reobfuscated',
            'pkg/Main$Inner.class': b'reobfuscated inner',
        }
        self.pack()

    @staticmethod
    def put(path, value):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(value)

    def pack(self):
        with zipfile.ZipFile(self.jar, 'w') as jar:
            for name, value in self.entries.items():
                jar.writestr(name, value)

    def errors(self):
        return check.validate(self.root, self.jar)[0]

    def expect_error(self, text):
        errors = self.errors()
        self.assertTrue(any(text in error for error in errors), errors)

    def test_valid_and_counts(self):
        errors, counts = check.validate(self.root, self.jar)
        self.assertEqual(errors, [])
        self.assertEqual(counts, {'resources': 3, 'textures': 1, 'classes': 2})

    def test_missing_resource(self):
        del self.entries['assets/data.json']; self.pack()
        self.expect_error('Missing resource in JAR: assets/data.json')

    def test_resource_must_match_exact_bytes(self):
        self.entries['assets/data.json'] = b'{"key":"value"}\n'; self.pack()
        self.expect_error('Changed resource in JAR: assets/data.json')

    def test_metadata_only_needs_to_exist(self):
        self.entries['mcmod.info'] = b'anything'; self.pack()
        self.assertEqual(self.errors(), [])
        del self.entries['mcmod.info']; self.pack()
        self.expect_error('Missing resource in JAR: mcmod.info')

    def test_nested_metadata_must_be_identical(self):
        self.entries['nested/mcmod.info'] = b'changed'; self.pack()
        self.expect_error('Changed resource in JAR: nested/mcmod.info')

    def test_source_metadata_missing(self):
        (self.resources / 'mcmod.info').unlink()
        self.expect_error('Missing source resource: mcmod.info')

    def test_texture_decoded_not_raw_and_relative_to_texture_root(self):
        self.entries['assets/mod/textures/x.png'] = base64.b64encode(b'png bytes'); self.pack()
        self.expect_error('Changed texture in JAR: assets/mod/textures/x.png')
        del self.entries['assets/mod/textures/x.png']
        self.entries['src/main/texture_sources/assets/mod/textures/x.png'] = b'png bytes'; self.pack()
        self.expect_error('Missing texture in JAR: assets/mod/textures/x.png')

    def test_invalid_base64_texture(self):
        self.put(self.textures / 'assets/mod/textures/x.png.b64', b'%%bad')
        self.expect_error('Invalid Base64 texture')

    def test_unpadded_base64_matches_gradle(self):
        self.put(self.textures / 'assets/mod/textures/x.png.b64', b'YQ')
        self.entries['assets/mod/textures/x.png'] = b'a'; self.pack()
        self.assertEqual(self.errors(), [])

    def test_incorrect_base64_padding_rejected(self):
        for value in (b'YWJj=', b'YQ=', b'YQ===', b'Y Q=='):
            with self.subTest(value=value):
                self.put(self.textures / 'assets/mod/textures/x.png.b64', value)
                self.expect_error('Invalid Base64 texture')

    def test_missing_inner_class(self):
        del self.entries['pkg/Main$Inner.class']; self.pack()
        self.expect_error('Missing class in JAR: pkg/Main$Inner.class')

    def test_duplicate_entry_even_if_bytes_identical(self):
        with warnings.catch_warnings():
            warnings.simplefilter('ignore', UserWarning)
            with zipfile.ZipFile(self.jar, 'a') as jar:
                jar.writestr('assets/data.json', self.entries['assets/data.json'])
        self.expect_error('Duplicate ZIP entry: assets/data.json')

    def test_missing_directories(self):
        errors, _ = check.validate(self.root / 'wrong', self.jar)
        self.assertTrue(any('Missing resources directory' in e for e in errors))
        self.assertTrue(any('Missing texture sources directory' in e for e in errors))
        self.assertTrue(any('Missing compiled main classes directory' in e for e in errors))

    def test_no_compiled_classes(self):
        (self.classes / 'pkg/Main.class').unlink()
        (self.classes / 'pkg/Main$Inner.class').unlink()
        self.expect_error('No compiled main classes found')

    def test_no_texture_sources(self):
        (self.textures / 'assets/mod/textures/x.png.b64').unlink()
        self.expect_error('No encoded PNG texture sources found')

    def test_invalid_jar(self):
        self.jar.write_bytes(b'not a jar')
        self.expect_error('Cannot read JAR')

    def test_crc_corruption(self):
        self.jar.write_bytes(self.jar.read_bytes().replace(b'png bytes', b'png Bytes'))
        self.expect_error('ZIP CRC integrity failure')

    def test_cli_exit_status(self):
        script = Path(check.__file__)
        result = subprocess.run([sys.executable, '-B', str(script), str(self.root), str(self.jar)], capture_output=True, text=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn('PASS:', result.stdout)
        del self.entries['pkg/Main$Inner.class']; self.pack()
        result = subprocess.run([sys.executable, '-B', str(script), str(self.root), str(self.jar)], capture_output=True, text=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('Missing class', result.stderr)


if __name__ == '__main__':
    unittest.main()
