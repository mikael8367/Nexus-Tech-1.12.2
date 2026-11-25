import json
import subprocess
import sys
from pathlib import Path
import validate_jar as v
import test_validate_jar as base

class MainLogoTests(base.ValidatorTests):
    def source_logo(self, value=None, *, missing=False):
        path = self.resources / 'mcmod.info'
        metadata = json.loads(path.read_text(encoding='utf-8'))
        if missing:
            metadata[0].pop('logoFile', None)
        else:
            metadata[0]['logoFile'] = value
        self.put(path, json.dumps(metadata))

    def test_empty_logo_when_both_declare_empty(self):
        self.source_logo(''); self.update_meta(logoFile='')
        del self.files['logo.png']; self.pack()
        self.assertEqual(self.check(), [])

    def test_empty_logo_still_validates_other_metadata(self):
        self.source_logo(''); self.update_meta(logoFile='', version='wrong')
        self.assert_error('mcmod.info version')

    def test_empty_jar_logo_rejects_nonempty_source(self):
        self.update_meta(logoFile=''); self.assert_error('logoFile differs from source')

    def test_nonempty_jar_logo_rejects_empty_source(self):
        self.source_logo(''); self.assert_error('logoFile differs from source')

    def test_empty_logo_rejects_missing_source_field(self):
        self.source_logo(missing=True); self.update_meta(logoFile='')
        self.assert_error('logoFile differs from source')

    def test_nonempty_logo_rejects_missing_source_field(self):
        self.source_logo(missing=True); self.assert_error('logoFile differs from source')

    def test_missing_jar_logo_field_rejected_even_with_empty_source(self):
        self.source_logo(''); self.meta.pop('logoFile')
        self.update_json('mcmod.info', [self.meta])
        self.assert_error('logoFile missing or invalid')

    def test_both_missing_logo_fields_rejected(self):
        self.source_logo(missing=True); self.meta.pop('logoFile')
        self.update_json('mcmod.info', [self.meta])
        self.assert_error('logoFile missing or invalid')

    def test_jar_logo_wrong_type_rejected(self):
        for value in (None, False, 0, [], {}):
            with self.subTest(value=value):
                self.source_logo(''); self.update_meta(logoFile=value)
                self.assert_error('logoFile missing or invalid')

    def test_source_logo_wrong_type_rejected(self):
        for value in (None, False, 0, [], {}):
            with self.subTest(value=value):
                self.source_logo(value); self.update_meta(logoFile='')
                self.assert_error('logoFile differs from source')
                self.update_meta(logoFile='logo.png')
                self.assert_error('logoFile differs from source')

    def test_nonempty_logo_disagreement_rejected_even_if_file_valid(self):
        self.source_logo('other.png'); self.assert_error('logoFile differs from source')

    def test_matching_invalid_logo_paths_rejected(self):
        for path in ('/logo.png', '../logo.png', 'a/../logo.png', 'logo.jpg', 'logo\\\\file.png', 'logo file.png'):
            with self.subTest(path=path):
                self.source_logo(path); self.update_meta(logoFile=path)
                self.assert_error('logoFile missing or invalid')

    def test_matching_nested_logo_path_valid(self):
        self.source_logo('images/logo.png'); self.update_meta(logoFile='images/logo.png')
        self.files['images/logo.png'] = v.PNG + b'logo'; self.pack()
        self.assertEqual(self.check(), [])

    def test_matching_nested_logo_missing_file_rejected(self):
        self.source_logo('images/logo.png'); self.update_meta(logoFile='images/logo.png')
        self.assert_error('missing images/logo.png')

    def test_matching_nested_logo_empty_file_rejected(self):
        self.source_logo('images/logo.png'); self.update_meta(logoFile='images/logo.png')
        self.files['images/logo.png'] = b''; self.pack()
        self.assert_error('empty images/logo.png')

    def test_matching_nested_logo_invalid_png_rejected(self):
        self.source_logo('images/logo.png'); self.update_meta(logoFile='images/logo.png')
        self.files['images/logo.png'] = b'not png'; self.pack()
        self.assert_error('logo is not a PNG')

    def test_empty_logo_cli_success(self):
        self.source_logo(''); self.update_meta(logoFile='')
        del self.files['logo.png']; self.pack()
        result = subprocess.run([sys.executable, str(Path(v.__file__)), '--jar', str(self.jar), '--source', str(self.source), '--version', '1.0'], capture_output=True, text=True)
        self.assertEqual(result.returncode, 0, result.stderr)

