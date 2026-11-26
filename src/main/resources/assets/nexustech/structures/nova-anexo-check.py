#!/usr/bin/env python3
"""Auditoria apenas deste anexo; não executa Minecraft nem ativa worldgen."""
import gzip, io, hashlib, struct, sys, collections
from pathlib import Path

SOURCE_SHA = '89f384a40f8c62632beaa02dec61685784f9940105c4e13e488b86b31068b335'
MAX_COMPRESSED = 100_000
MAX_NBT = 1_000_000
GLASS = {20, 95, 102, 160}

def ungzip(path):
    p = Path(path)
    assert p.stat().st_size <= MAX_COMPRESSED
    raw = p.read_bytes()
    assert len(raw) <= MAX_COMPRESSED
    with gzip.GzipFile(fileobj=io.BytesIO(raw)) as f:
        data = f.read(MAX_NBT + 1)
    assert len(data) <= MAX_NBT
    return raw, data

class Reader:
    def __init__(self, data): self.data, self.pos, self.offsets = data, 0, {}
    def take(self, n):
        assert 0 <= n <= MAX_NBT and self.pos + n <= len(self.data)
        x = self.data[self.pos:self.pos+n]; self.pos += n; return x
    def number(self, form): return struct.unpack('>'+form, self.take(struct.calcsize(form)))[0]
    def text(self): return self.take(self.number('H')).decode('utf-8')
    def item(self, tag, depth=0, path=''):
        assert depth < 32
        if tag in (1,2,3,4,5,6): return self.number({1:'b',2:'h',3:'i',4:'q',5:'f',6:'d'}[tag])
        if tag == 8: return self.text()
        if tag in (7,11,12):
            n=self.number('i'); size=n*{7:1,11:4,12:8}[tag]
            assert 0 <= n <= MAX_NBT and size <= MAX_NBT
            self.offsets[path] = (self.pos, size)
            return self.take(size)
        if tag == 9:
            subtype=self.number('B'); n=self.number('i'); assert 0 <= n <= 100000
            assert subtype != 0 or n == 0
            return (subtype, [self.item(subtype,depth+1) for _ in range(n)])
        if tag == 10:
            result={}
            while True:
                t=self.number('B')
                if t==0: return result
                name=self.text(); assert name not in result
                result[name]=(t,self.item(t,depth+1,name))
        raise ValueError('tipo NBT desconhecido: %s'%tag)
    def root(self):
        assert self.number('B')==10 and self.text()=='Schematic'
        obj=self.item(10); assert self.pos == len(self.data)
        return obj

def inspect(data):
    r=Reader(data); o=r.root()
    assert o['Materials']==(8,'Alpha')
    w,h,l=[o[k][1] for k in ('Width','Height','Length')]
    assert 0 < w <= 128 and 0 < h <= 128 and 0 < l <= 128 and w*h*l <= 500000
    assert o['Blocks'][0]==7 and o['Data'][0]==7
    b=o['Blocks'][1]; d=o['Data'][1]
    assert len(b)==len(d)==w*h*l
    # Este anexo só usa IDs vanilla e não tem nibble AddBlocks.
    assert 'AddBlocks' not in o
    return r,o,b,d,(w,h,l)

def main():
    if not __debug__:
        raise RuntimeError('Nao execute com -O: a auditoria exige assertions ativas')
    assert len(sys.argv)==4 and sys.argv[1] in ('make','check')
    action,src,dst=sys.argv[1:]
    raw,old=ungzip(src)
    assert hashlib.sha256(raw).hexdigest()==SOURCE_SHA
    r,o,b,d,dims=inspect(old)
    assert dims==(11,14,11)
    assert o['TileEntities']==(9,(10,[])) and o['Entities']==(9,(10,[]))
    counts=collections.Counter(zip(b,d)); print('dimensoes',dims,'IDs/meta',sorted(counts.items()))
    where=[i for i,x in enumerate(b) if x in GLASS]
    assert len(where)==1 and counts[(20,0)]==1
    expected=bytearray(old); pos,n=r.offsets['Blocks']; assert n==len(b)
    for i in where: expected[pos+i]=0
    if action=='make':
        # Nunca sobrescreve o original nem qualquer arquivo já existente.
        with Path(dst).open('xb') as f:
            f.write(gzip.compress(expected,mtime=0))
    out,new=ungzip(dst)
    # Comparação integral independente de valores: todo o fluxo NBT descompactado
    # deve ser idêntico ao original exceto os bytes de Blocks exatos do vidro.
    assert new==expected
    rr,oo,bb,dd,dim2=inspect(new)
    assert dims==dim2 and dd==d and len(bb)==len(b)
    assert all((y==0 if i in where else y==x) for i,(x,y) in enumerate(zip(b,bb)))
    assert not any(x in GLASS for x in bb)
    assert o['TileEntities']==oo['TileEntities'] and o['Entities']==oo['Entities']
    assert {k:v for k,v in o.items() if k!='Blocks'}=={k:v for k,v in oo.items() if k!='Blocks'}
    for i in where:
        x=i%dims[0]; z=(i//dims[0])%dims[2]; y=i//(dims[0]*dims[2]); print('removido ID/meta',b[i],d[i],'posicao x,y,z',x,y,z)
    print('entidades 0; tile entities 0; SHA256 arquivo tratado',hashlib.sha256(out).hexdigest(),'bytes',len(out)); print('PASS: comparacao integral NBT e dados')

if __name__=='__main__': main()
