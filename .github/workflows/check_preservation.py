#!/usr/bin/env python3
"""Check a built JAR against the project inputs and compiled main classes.

Usage: python .github/workflows/check_preservation.py PROJECT_ROOT OUTPUT.jar
This checks preservation, not Minecraft/Forge runtime behavior or class contents.
Texture mapping follows build.gradle's generateTextureResources task: source files
under src/main/texture_sources ending in .png.b64 become JAR paths relative to
that directory with the final .b64 removed, containing Base64-decoded bytes.
"""
import argparse
import base64
import binascii
from collections import Counter
import re
from pathlib import Path
import sys
import zipfile


def validate(project_root, jar_path):
    """Return (errors, counts). Class files are checked by name, not bytes."""
    root = Path(project_root)
    jar_path = Path(jar_path)
    resources = root / 'src/main/resources'
    textures = root / 'src/main/texture_sources'
    classes = root / 'build/classes/java/main'
    errors = []
    counts = {'resources': 0, 'textures': 0, 'classes': 0}

    for label, path in [('resources', resources), ('texture sources', textures),
                        ('compiled main classes', classes)]:
        if not path.is_dir():
            errors.append(f'Missing {label} directory: {path}')

    if not (resources / 'mcmod.info').is_file():
        errors.append('Missing source resource: mcmod.info')

    if resources.is_dir():
        resource_files = sorted(p for p in resources.rglob('*') if p.is_file())
        counts['resources'] = len(resource_files)
    else:
        resource_files = []
    if textures.is_dir():
        texture_files = sorted(p for p in textures.rglob('*.png.b64') if p.is_file())
        counts['textures'] = len(texture_files)
        if not texture_files:
            errors.append('No encoded PNG texture sources found')
    else:
        texture_files = []
    if classes.is_dir():
        class_files = sorted(p for p in classes.rglob('*.class') if p.is_file())
        counts['classes'] = len(class_files)
        if not class_files:
            errors.append('No compiled main classes found')
    else:
        class_files = []

    try:
        with zipfile.ZipFile(jar_path) as jar:
            infos = jar.infolist()
            names = Counter(info.filename for info in infos)
            for name, count in sorted(names.items()):
                if count > 1:
                    errors.append(f'Duplicate ZIP entry: {name} ({count} copies)')
            files = {info.filename for info in infos if not info.is_dir()}
            bad = jar.testzip()
            if bad is not None:
                errors.append(f'ZIP CRC integrity failure: {bad}')

            def check_entry(name, expected=None, kind='resource'):
                if name not in files:
                    errors.append(f'Missing {kind} in JAR: {name}')
                elif names[name] == 1 and expected is not None and jar.read(name) != expected:
                    errors.append(f'Changed {kind} in JAR: {name}')

            for path in resource_files:
                name = path.relative_to(resources).as_posix()
                # processResources expands only the root mcmod.info.
                check_entry(name, None if name == 'mcmod.info' else path.read_bytes())
            for path in texture_files:
                name = path.relative_to(textures).as_posix()[:-4]
                try:
                    # Gradle's Base64.decoder.decode(source.getText('UTF-8').trim()).
                    encoded = path.read_text(encoding='utf-8').strip(''.join(map(chr, range(33))))
                    # Java's basic decoder accepts unpadded Base64 but rejects
                    # excess or incomplete padding, unlike Python in some cases.
                    if '=' in encoded:
                        if not re.fullmatch(r'(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)', encoded):
                            raise ValueError('invalid Base64 padding')
                    else:
                        encoded += '=' * (-len(encoded) % 4)
                    data = base64.b64decode(encoded, validate=True)
                except (UnicodeError, ValueError, binascii.Error) as exc:
                    errors.append(f'Invalid Base64 texture {path}: {exc}')
                    continue
                check_entry(name, data, 'texture')
            for path in class_files:
                check_entry(path.relative_to(classes).as_posix(), kind='class')
    except (OSError, zipfile.BadZipFile, zipfile.LargeZipFile, RuntimeError, EOFError) as exc:
        errors.append(f'Cannot read JAR or its entries: {exc}')
    return errors, counts


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('project_root', type=Path)
    parser.add_argument('jar', type=Path)
    args = parser.parse_args(argv)
    try:
        errors, counts = validate(args.project_root, args.jar)
    except OSError as exc:
        print(f'ERROR: Cannot read project inputs: {exc}', file=sys.stderr)
        return 1
    if errors:
        for error in errors:
            print(f'ERROR: {error}', file=sys.stderr)
        print('FAIL: preservation check', file=sys.stderr)
        return 1
    print('PASS: preservation check; '
          f"{counts['resources']} resources, {counts['textures']} decoded textures, "
          f"{counts['classes']} main classes checked")
    return 0


if __name__ == '__main__':
    sys.exit(main())
