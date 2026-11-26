import hashlib, re, struct, sys, zipfile, subprocess
from pathlib import Path
p=Path(sys.argv[1]); z=zipfile.ZipFile(p)
assert z.testzip() is None
names=z.namelist(); assert len(names)==len(set(names)), 'duplicate entries'
assert 'com/mikael8367/nexustech/worldgen/ExperimentalStructureGenerator.class' in names
assert 'com/mikael8367/nexustech/nuclear/NuclearDisabled.class' in names
assert not any('StructureForgeHarness' in x for x in names)
classes=[x for x in names if x.endswith('.class')]; assert classes
for n in classes:
 b=z.read(n)
 assert b[:4]==b'\xca\xfe\xba\xbe' and struct.unpack('>H',b[6:8])[0]<=52, n
 if 'ExperimentalStructureGenerator' not in n:
  assert b'com/mikael8367/nexustech/worldgen/ExperimentalStructureGenerator' not in b, 'class reference '+n
 assert b'StructureForgeHarness' not in b, 'instrumented class '+n
out=subprocess.check_output(['javap','-classpath',str(p),'-c','-p','com.mikael8367.nexustech.nuclear.NuclearDisabled'],text=True)
assert re.search(r'isDisabled\(\);\s+Code:\s+0:\s+iconst_1\s+1:\s+ireturn',out), out
ore=subprocess.check_output(['javap','-classpath',str(p),'-c','-p','com.mikael8367.nexustech.ores.OreWorldGenerator'],text=True)
assert re.search(r'bipush\s+18',ore), 'ore flag 18 absent'
print('PASS ZIP; classes:',len(classes),'major<=52; no generator class reference; no harness; disabled=true; ore bipush 18')
print('SHA256:',hashlib.sha256(p.read_bytes()).hexdigest(),'size:',p.stat().st_size)
