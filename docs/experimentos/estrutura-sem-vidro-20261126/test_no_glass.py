#!/usr/bin/env python3
"""Independent NBT and source-vs-copy audit. No Forge, no JAR, no network."""
import base64, collections, gzip, hashlib, pathlib, struct, sys

P = pathlib.Path(__file__).resolve().parent
EXPECTED = '5333f61ae7f814e0632e03909b219af91e89480b8b78adaabbb9db35b4cdaf22'
ORIGINAL_SHA256 = '46811e619e8155ab14e6450827c9893da57befec59866d2a7726e571e8514367'
CELL_COUNT = 640


def require(condition, message):
    if not condition:
        raise ValueError(message)


def read_nbt(raw):
    b = gzip.decompress(raw)
    i = 0

    def take(n):
        nonlocal i
        require(n >= 0 and i + n <= len(b), 'truncated or negative-length NBT')
        out = b[i:i+n]
        i += n
        return out

    def num(fmt):
        return struct.unpack('>' + fmt, take(struct.calcsize(fmt)))[0]

    def string():
        return take(num('H')).decode('utf8')

    root_type = num('B')
    root_name = string()
    require(root_type == 10 and root_name == 'Schematic', 'wrong NBT root')
    tags = {}
    while True:
        t = num('B')
        if not t:
            break
        name = string()
        require(name not in tags, 'duplicate tag: ' + name)
        if t == 2:
            v = num('H')
        elif t == 3:
            v = num('i')
        elif t == 7:
            v = take(num('i'))
        elif t == 8:
            v = string()
        elif t == 9:
            v = (num('B'), num('i'))
            require(v == (10, 0), 'nonempty or wrong list: ' + name)
        else:
            raise ValueError('unexpected NBT type: ' + str(t))
        tags[name] = (t, v)
    require(i == len(b), 'trailing data')
    return tags


def validate(t):
    require(set(t) == {'Blocks','Data','Materials','Entities','TileEntities','Width','Length','Height','WEOffsetX','WEOffsetY','WEOffsetZ','WEOriginX','WEOriginY','WEOriginZ'}, 'unexpected tag set')
    for k in ('Width', 'Length', 'Height'):
        require(t[k][0] == 2, 'wrong type for ' + k)
    require(tuple(t[k][1] for k in ('Width', 'Height', 'Length')) == (10, 8, 8), 'wrong dimensions')
    require(t['Materials'] == (8, 'Alpha') and t['Entities'] == (9, (10, 0)) and t['TileEntities'] == (9, (10, 0)), 'wrong materials or entities')
    require(t['Blocks'][0] == t['Data'][0] == 7 and len(t['Blocks'][1]) == len(t['Data'][1]) == CELL_COUNT, 'wrong block/data type or length')
    allowed = {(0,0),(1,0),(1,1),(1,5),(1,6),(2,0),(3,0),(13,0),(15,0)}
    palette = collections.Counter(zip(t['Blocks'][1], t['Data'][1]))
    require(set(palette) == allowed, 'unexpected palette')
    return palette


def compare_original(original_raw, tags):
    require(hashlib.sha256(original_raw).hexdigest() == ORIGINAL_SHA256, 'original SHA-256 mismatch')
    before = read_nbt(original_raw)
    require(before.keys() == tags.keys(), 'original tag set differs')
    require(before['Blocks'][0] == 7 and len(before['Blocks'][1]) == CELL_COUNT, 'original Blocks must be a byte array of exactly 640 bytes')
    for k in tags:
        if k != 'Blocks':
            require(before[k] == tags[k], 'original differs in tag: ' + k)
    a, b = before['Blocks'][1], tags['Blocks'][1]
    require(len(b) == CELL_COUNT, 'modified Blocks must have exactly 640 bytes')
    diff = [(i, a[i], b[i]) for i in range(CELL_COUNT) if a[i] != b[i]]
    require(len(diff) == 1 and diff[0][1:] == (20, 0), 'unexpected block differences: ' + repr(diff))
    i = diff[0][0]
    print('Original comparison: only glass -> air at index', i, '(x,y,z)=', (i % 10, i // 80, (i // 10) % 8))


def preserve_reference(path, expected):
    try:
        with path.open('xb') as f:
            f.write(expected)
    except FileExistsError:
        require(path.read_bytes() == expected, 'existing reference.bin differs; preserved without changes')


def main():
    require(len(sys.argv) <= 2, 'usage: test_no_glass.py [original.schematic]')
    raw = base64.b64decode((P / 'sem_vidro.schematic.b64').read_bytes(), validate=False)
    require(hashlib.sha256(raw).hexdigest() == EXPECTED, 'modified SHA-256 mismatch')
    tags = read_nbt(raw)
    palette = validate(tags)
    if len(sys.argv) > 1:
        compare_original(pathlib.Path(sys.argv[1]).read_bytes(), tags)
    preserve_reference(P / 'reference.bin', tags['Blocks'][1] + tags['Data'][1])
    print('PASS: SHA-256, NBT, dimensions, 640 IDs and metadata, empty entities and tiles, no glass; palette:', dict(sorted(palette.items())))


if __name__ == '__main__':
    main()
