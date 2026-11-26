#!/usr/bin/env python3
"""Offline only: compile the Nova schematic parser/test as .class files; never launch a server."""
import argparse, base64, hashlib, os, pathlib, subprocess, sys, tempfile

FORGE_SHA = 'a2121e061c9162992a206d6e3192bf33691263c1083d6942c1bfd89d43e1f7e4'
ORIGINAL_SHA = 'eb23b1ec67c9ea74a974eb09fcbe7fcfe175f122b2a12aeb0b15513a740655cd'
RESOURCE_SHA = 'cf585cc66defdf94eb60c3fb17a313061937f5833da5dbc7703cd2dc4bfa1576'
PKG = 'com.mikael8367.nexustech.worldgen.'

def digest(path):
    h = hashlib.sha256()
    with open(path, 'rb') as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b''): h.update(chunk)
    return h.hexdigest()

def checked(path, expected):
    if not path.is_file(): raise RuntimeError('missing file: ' + str(path))
    got = digest(path)
    print('sha256', got, path, flush=True)
    if expected and got != expected: raise RuntimeError('unexpected SHA-256: ' + str(path))

def run(args, cwd):
    print('+', ' '.join(map(str, args)), flush=True)
    subprocess.run(list(map(str, args)), cwd=cwd, check=True, timeout=120)

def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--repo', type=pathlib.Path, required=True)
    p.add_argument('--java-home', type=pathlib.Path, required=True)
    p.add_argument('--forge', type=pathlib.Path, required=True, help='existing mapped snapshot_20171003 2860 jar, exact SHA required')
    p.add_argument('--dep', type=pathlib.Path, action='append', default=[], help='existing dependency jar; repeat in deliberate classpath order')
    p.add_argument('--original', type=pathlib.Path, required=True)
    p.add_argument('--output-parent', type=pathlib.Path, required=True, help='directory for newly-created output; no cleanup')
    a = p.parse_args()
    repo = a.repo.resolve(); forge = a.forge.resolve(); original = a.original.resolve()
    deps = [d.resolve() for d in a.dep]
    java = (a.java_home / 'bin/java').resolve(); javac = (a.java_home / 'bin/javac').resolve()
    for j in [forge] + deps:
        if j.suffix.lower() != '.jar': raise RuntimeError('dependency must be existing jar: ' + str(j))
        checked(j, FORGE_SHA if j == forge else None)
    checked(original, ORIGINAL_SHA)
    package = 'com/mikael8367/nexustech/worldgen'
    src = repo / 'src/main/java' / package / 'NovaSchematic.java'
    tests = [repo / 'src/test/java' / package / n for n in ['NovaSchematicTest.java', 'CompareSchematic.java']]
    res = repo / 'src/main/resources'
    b64 = res / 'assets/nexustech/structures/nova_structure_sem_vidro.schematic.b64'
    for f in [src] + tests + [b64]: checked(f, None)
    if hashlib.sha256(base64.b64decode(b64.read_bytes(), validate=False)).hexdigest() != RESOURCE_SHA:
        raise RuntimeError('unexpected decoded resource SHA-256')
    version = subprocess.check_output([str(java), '-version'], stderr=subprocess.STDOUT, text=True)
    print(version, flush=True)
    if 'version "1.8.' not in version: raise RuntimeError('Java 8 is required')
    if not a.output_parent.is_dir(): raise RuntimeError('output parent must already exist')
    out = pathlib.Path(tempfile.mkdtemp(prefix='nova-offline-', dir=a.output_parent.resolve()))
    classes = out / 'classes'; classes.mkdir()
    cp = os.pathsep.join(map(str, [forge] + deps))
    print('output:', out, flush=True)
    run([javac, '-J-Xmx64m', '-proc:none', '-source', '8', '-target', '8', '-encoding', 'UTF-8', '-cp', cp, '-d', classes, src] + tests, out)
    for forbidden in ['net/minecraft', 'net/minecraftforge']:
        if (classes / forbidden).exists(): raise RuntimeError('forbidden stubs in compiled output')
    runtime_cp = os.pathsep.join(map(str, [classes, res, forge] + deps))
    run([java, '-Xmx48m', '-cp', runtime_cp, PKG + 'NovaSchematicTest', original], out)
    run([java, '-Xmx48m', '-cp', runtime_cp, PKG + 'CompareSchematic', original, b64], out)
    print('PASS: OFFLINE PARSER TESTS ONLY; no Forge server, mod load, placement, or world generation.', flush=True)

if __name__ == '__main__':
    try: main()
    except (OSError, RuntimeError, ValueError, subprocess.SubprocessError) as e:
        print('FAIL:', e, file=sys.stderr); sys.exit(1)
