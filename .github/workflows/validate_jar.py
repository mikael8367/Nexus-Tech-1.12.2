#!/usr/bin/env python3
"""Offline structural validation of the Nexus Tech 1.12.2 distribution.

Usage: python validate_jar.py --jar file.jar --source extracted-source --version 1.0
No code from the JAR is executed and no network access is required.
"""
import argparse
import json
import re
import struct
import sys
import zipfile
import zlib
from pathlib import Path

MODID = 'nexustech'
MC = '1.12.2'
FORGE = '1.12.2-14.23.5.2860'
PNG = b'\x89PNG\r\n\x1a\n'
RESOURCE = re.compile(r'^(?:[a-z0-9_.-]+:)?[a-z0-9_./-]+$')


def java_constants(data):
    """Read string ConstantValue fields and constant-pool UTF-8s, not executable code."""
    if len(data) < 10 or data[:4] != b'\xca\xfe\xba\xbe':
        raise ValueError('invalid class header')
    pos = 8
    def u1():
        nonlocal pos
        value = data[pos]; pos += 1; return value
    def u2():
        nonlocal pos
        value = struct.unpack_from('>H', data, pos)[0]; pos += 2; return value
    def u4():
        nonlocal pos
        value = struct.unpack_from('>I', data, pos)[0]; pos += 4; return value
    count = u2(); cp = [None] * count; i = 1
    while i < count:
        tag = u1()
        if tag == 1:
            n = u2(); raw = data[pos:pos+n]; pos += n
            if len(raw) != n: raise ValueError('truncated constant pool')
            cp[i] = (tag, raw.decode('utf-8', errors='replace'))
        elif tag in (3, 4): pos += 4
        elif tag in (5, 6): pos += 8; i += 1
        elif tag in (7, 8, 16, 19, 20): cp[i] = (tag, u2())
        elif tag in (9, 10, 11, 12, 17, 18): pos += 4
        elif tag == 15: pos += 3
        else: raise ValueError('unsupported constant pool tag')
        if pos > len(data): raise ValueError('truncated constant pool')
        i += 1
    def utf(index):
        if index <= 0 or index >= len(cp) or cp[index] is None or cp[index][0] != 1:
            raise ValueError('invalid constant pool index')
        return cp[index][1]
    u2(); u2(); u2()
    for _ in range(u2()): u2()
    fields = {}
    for _ in range(u2()):
        u2(); name = utf(u2()); u2()
        for _ in range(u2()):
            attribute = utf(u2()); length = u4()
            if pos + length > len(data): raise ValueError('truncated field')
            if attribute == 'ConstantValue' and length == 2:
                index = u2()
                if not (0 < index < len(cp)) or cp[index] is None: raise ValueError('invalid field constant')
                if cp[index][0] == 8: fields[name] = utf(cp[index][1])
            else: pos += length
    return fields, {entry[1] for entry in cp if entry and entry[0] == 1}


def validate(jar, source, version):
    errors = []
    def error(message): errors.append(message)
    root = Path(source)
    if not root.is_dir(): return ['source directory not found']
    if not version or not re.fullmatch(r'[A-Za-z0-9._+\-]+', version):
        return ['invalid requested version']
    java_path = root / 'src/main/java/com/mikael8367/nexustech/NexusTech.java'
    try:
        java = java_path.read_text(encoding='utf-8')
        gradle = (root / 'build.gradle').read_text(encoding='utf-8')
        source_metadata = json.loads((root / 'src/main/resources/mcmod.info').read_text(encoding='utf-8'))
    except (OSError, UnicodeError, ValueError):
        return ['cannot read source NexusTech.java, build.gradle or mcmod.info']
    # Strip comments before matching declarations, without displaying source values.
    gradle = re.sub(r'/\*.*?\*/|//[^\n]*', '', gradle, flags=re.S)
    java = re.sub(r'/\*.*?\*/|//[^\n]*', '', java, flags=re.S)
    def capture(pattern, text, label, expected):
        matches = re.findall(pattern, text, re.M)
        if len(matches) != 1 or matches[0] != expected: error(label + ' missing or does not match expected value')
    capture(r'\bVERSION\s*=\s*"([^"]+)"', java, 'NexusTech.VERSION', version)
    capture(r'\bMODID\s*=\s*"([^"]+)"', java, 'NexusTech.MODID', MODID)
    capture(r'^\s*version\s*=\s*[\'\"]([^\'\"]+)[\'\"]', gradle, 'build.gradle version', version)
    capture(r'\b(?:def\s+)?minecraftVersion\s*=\s*[\'\"]([^\'\"]+)[\'\"]', gradle, 'build.gradle Minecraft version', MC)
    capture(r'\bminecraft\s+[\'\"]net\.minecraftforge:forge:([^\'\"]+)[\'\"]', gradle, 'build.gradle Forge coordinate', FORGE)
    capture(r'acceptedMinecraftVersions\s*=\s*"([^"]+)"', java, 'NexusTech acceptedMinecraftVersions', '[' + MC + ']')
    if not isinstance(source_metadata, list): return errors + ['source mcmod.info must be a list']
    sources = [x for x in source_metadata if isinstance(x, dict) and x.get('modid') == MODID]
    if len(sources) != 1: return errors + ['source mcmod.info must contain exactly one Nexus Tech entry']
    src_meta = sources[0]
    dependency_expr = re.findall(r'\bdependencies\s*=\s*"([^"]*)"', java)
    if len(dependency_expr) > 1: error('ambiguous @Mod dependencies declaration')
    try:
        if not Path(jar).is_file() or Path(jar).stat().st_size == 0:
            return errors + ['JAR missing or empty']
        with zipfile.ZipFile(jar) as z:
            infos = z.infolist()
            files = [i for i in infos if not i.is_dir()]
            names = [i.filename for i in files]
            members = set(names)
            if not files: error('JAR has no files')
            if len(members) != len(names): error('JAR contains duplicate file entries')
            for i in files:
                if i.filename.startswith('/') or '\\' in i.filename or '..' in i.filename.split('/'):
                    error('JAR contains an unsafe entry path'); break
            broken = z.testzip()
            if broken: return errors + ['JAR CRC integrity check failed']
            def read(name):
                if name not in members: error('missing ' + name); return None
                value = z.read(name)
                if not value: error('empty ' + name); return None
                return value
            def json_file(name):
                raw = read(name)
                if raw is None: return None
                try: return json.loads(raw.decode('utf-8'))
                except (UnicodeError, ValueError): error('invalid JSON in ' + name); return None
            meta = json_file('mcmod.info')
            if not isinstance(meta, list): error('JAR mcmod.info must be a list'); meta = []
            entries = [x for x in meta if isinstance(x, dict) and x.get('modid') == MODID]
            if len(entries) != 1: error('JAR mcmod.info must contain exactly one Nexus Tech entry')
            else:
                m = entries[0]
                for field, expected in [('version', version), ('mcversion', MC), ('modid', MODID)]:
                    if m.get(field) != expected: error('mcmod.info ' + field + ' does not match expected value')
                for field in ('dependencies', 'requiredMods'):
                    expected = src_meta.get(field, [])
                    actual = m.get(field, [])
                    if not isinstance(expected, list) or not isinstance(actual, list) or actual != expected:
                        error('mcmod.info ' + field + ' differs from source or is not a list')
                logo = m.get('logoFile')
                if not isinstance(logo, str) or not re.fullmatch(r'[a-zA-Z0-9_./-]+\.png', logo) or '..' in logo.split('/') or logo.startswith('/'):
                    error('mcmod.info logoFile missing or invalid')
                else:
                    if logo != src_meta.get('logoFile'): error('mcmod.info logoFile differs from source')
                    raw = read(logo)
                    if raw is not None and not raw.startswith(PNG): error('logo is not a PNG')
            main = read('com/mikael8367/nexustech/NexusTech.class')
            if main:
                try:
                    fields, cp = java_constants(main)
                    for name, expected in [('MODID', MODID), ('VERSION', version)]:
                        if fields.get(name) != expected: error('compiled NexusTech.' + name + ' does not match')
                    if '[' + MC + ']' not in cp: error('compiled NexusTech Minecraft requirement missing')
                    if dependency_expr and dependency_expr[0] not in cp: error('compiled @Mod dependencies do not match source')
                except (ValueError, IndexError, struct.error): error('invalid compiled NexusTech.class')
            source_java = root / 'src/main/java'
            if not source_java.is_dir(): error('source Java directory missing')
            else:
                java_files = sorted(source_java.rglob('*.java'))
                if not java_files: error('no source mod classes')
                for f in java_files:
                    name = f.relative_to(source_java).with_suffix('.class').as_posix()
                    raw = read(name) if name != 'com/mikael8367/nexustech/NexusTech.class' else main
                    if raw is not None and not raw.startswith(b'\xca\xfe\xba\xbe'): error('invalid class header: ' + name)
            source_assets = root / 'src/main/resources/assets' / MODID
            for category in ('models', 'blockstates'):
                folder = source_assets / category
                source_files = sorted(folder.rglob('*.json')) if folder.is_dir() else []
                if not source_files: error('no source ' + category + ' JSON files')
                for f in source_files:
                    name = f.relative_to(root / 'src/main/resources').as_posix()
                    if name not in members: error('missing ' + name)
            def resource(ref, category, origin, block_model=False):
                if not isinstance(ref, str):
                    error('invalid resource reference in ' + origin); return
                if ref.startswith('#'): return
                if not RESOURCE.fullmatch(ref) or '..' in ref.split('/') or ref.startswith('/') or ':/' in ref:
                    error('invalid resource reference in ' + origin); return
                if ':' not in ref: return  # Minecraft's default namespace
                namespace, path = ref.split(':', 1)
                if namespace != MODID: return  # supplied by Minecraft, Forge or another mod
                if block_model and not path.startswith(('block/', 'item/')): path = 'block/' + path
                suffix = '.png' if category == 'textures' else '.json'
                target = 'assets/' + MODID + '/' + category + '/' + path + suffix
                if target not in members: error('missing referenced ' + target + ' from ' + origin)
                elif z.getinfo(target).file_size == 0: error('empty referenced ' + target)
            asset_json = sorted(x for x in members if x.startswith('assets/' + MODID + '/') and x.endswith('.json') and ('/models/' in x or '/blockstates/' in x))
            for name in asset_json:
                obj = json_file(name)
                if not isinstance(obj, dict): error('asset JSON must be an object: ' + name); continue
                if '/models/' in name:
                    if 'parent' in obj: resource(obj['parent'], 'models', name)
                    textures = obj.get('textures', {})
                    if not isinstance(textures, dict): error('invalid textures object: ' + name)
                    else:
                        for ref in textures.values(): resource(ref, 'textures', name)
                    for override in obj.get('overrides', []) if isinstance(obj.get('overrides', []), list) else []:
                        if isinstance(override, dict) and 'model' in override: resource(override['model'], 'models', name)
                else:
                    if not any(k in obj for k in ('variants', 'multipart')): error('blockstate missing variants or multipart: ' + name)
                    if 'variants' in obj and (not isinstance(obj['variants'], dict) or not obj['variants']): error('invalid or empty blockstate variants: ' + name)
                    if 'multipart' in obj and (not isinstance(obj['multipart'], list) or not obj['multipart']): error('invalid or empty blockstate multipart: ' + name)
                    def walk(value):
                        if isinstance(value, dict):
                            for key, v in value.items():
                                if key == 'model': resource(v, 'models', name, True)
                                else: walk(v)
                        elif isinstance(value, list):
                            for v in value: walk(v)
                    walk(obj)
            # Validate local PNG signatures; external or #texture aliases need not be packaged.
            for name in sorted(x for x in members if x.startswith('assets/' + MODID + '/textures/') and x.endswith('.png')):
                raw = read(name)
                if raw is not None and not raw.startswith(PNG): error('invalid PNG signature: ' + name)
    except (OSError, zipfile.BadZipFile, RuntimeError, EOFError, NotImplementedError, ValueError, zlib.error, struct.error):
        error('cannot read or validate JAR (invalid, corrupt or unsupported ZIP)')
    return errors


def main():
    p = argparse.ArgumentParser(description='Validate the Nexus Tech JAR offline')
    p.add_argument('--jar', required=True); p.add_argument('--source', required=True); p.add_argument('--version', required=True)
    a = p.parse_args()
    errors = validate(a.jar, a.source, a.version)
    if errors:
        for issue in errors: print('ERROR: ' + issue, file=sys.stderr)
        print('Validation failed (' + str(len(errors)) + ' errors).', file=sys.stderr)
        return 1
    print('Validation passed: JAR structure, metadata, source versions and local resources.')
    return 0


if __name__ == '__main__': sys.exit(main())
