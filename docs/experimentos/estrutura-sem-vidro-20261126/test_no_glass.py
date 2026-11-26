#!/usr/bin/env python3
"""Independent NBT and source-vs-copy audit. No Forge, no JAR, no network."""
import base64, collections, gzip, hashlib, pathlib, struct, sys
P=pathlib.Path(__file__).resolve().parent
EXPECTED='5333f61ae7f814e0632e03909b219af91e89480b8b78adaabbb9db35b4cdaf22'

def read_nbt(raw):
    b=gzip.decompress(raw); i=0
    def take(n):
        nonlocal i
        assert n>=0 and i+n<=len(b), 'truncated'
        out=b[i:i+n];i+=n;return out
    def num(fmt): return struct.unpack('>'+fmt,take(struct.calcsize(fmt)))[0]
    def string(): return take(num('H')).decode('utf8')
    assert num('B')==10 and string()=='Schematic'
    tags={}
    while True:
        t=num('B')
        if not t: break
        name=string(); assert name not in tags, 'duplicate tag'
        if t==2: v=num('H')
        elif t==3: v=num('i')
        elif t==7: v=take(num('i'))
        elif t==8: v=string()
        elif t==9: v=(num('B'),num('i')); assert v==(10,0), 'nonempty or wrong list'
        else: raise AssertionError(('unexpected type',t))
        tags[name]=(t,v)
    assert i==len(b), 'trailing data'
    return tags

def validate(t):
    assert set(t)=={'Blocks','Data','Materials','Entities','TileEntities','Width','Length','Height','WEOffsetX','WEOffsetY','WEOffsetZ','WEOriginX','WEOriginY','WEOriginZ'}
    for k in ('Width','Length','Height'): assert t[k][0]==2
    assert tuple(t[k][1] for k in ('Width','Height','Length'))==(10,8,8)
    assert t['Materials']==(8,'Alpha') and t['Entities']==(9,(10,0)) and t['TileEntities']==(9,(10,0))
    assert t['Blocks'][0]==t['Data'][0]==7 and len(t['Blocks'][1])==len(t['Data'][1])==640
    allowed={(0,0),(1,0),(1,1),(1,5),(1,6),(2,0),(3,0),(13,0),(15,0)}
    palette=collections.Counter(zip(t['Blocks'][1],t['Data'][1]))
    assert set(palette)==allowed
    return palette

def main():
    raw=base64.b64decode((P/'sem_vidro.schematic.b64').read_bytes(),validate=False)
    assert hashlib.sha256(raw).hexdigest()==EXPECTED
    tags=read_nbt(raw); palette=validate(tags)
    if len(sys.argv)>1:
        before=read_nbt(pathlib.Path(sys.argv[1]).read_bytes())
        assert before.keys()==tags.keys()
        for k in tags:
            if k!='Blocks': assert before[k]==tags[k],k
        a,b=before['Blocks'][1],tags['Blocks'][1]
        diff=[(i,a[i],b[i]) for i in range(640) if a[i]!=b[i]]
        assert len(diff)==1 and diff[0][1:]==(20,0),diff
        i=diff[0][0]; print('Original comparison: only glass -> air at index',i,'(x,y,z)=',(i%10,i//80,(i//10)%8))
    (P/'reference.bin').write_bytes(tags['Blocks'][1]+tags['Data'][1])
    print('PASS: SHA-256, NBT, dimensions, 640 IDs and metadata, empty entities and tiles, no glass; palette:',dict(sorted(palette.items())))
if __name__=='__main__':main()
