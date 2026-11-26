import re, subprocess, time
from pathlib import Path
log=Path('evidence/server-console.log')
with log.open('w') as out:
 p=subprocess.Popen(['java','-Xms512M','-Xmx3G','-jar','forge-1.12.2-14.23.5.2860.jar','nogui'],cwd='server',stdin=subprocess.PIPE,stdout=out,stderr=subprocess.STDOUT,text=True)
 started=False
 try:
  for _ in range(720):
   time.sleep(1)
   text=log.read_text(errors='replace')
   if re.search(r'Done \([0-9.]+s\)!',text): started=True; break
   if p.poll() is not None: break
  if not started: raise RuntimeError('Server did not reach Done in 720s')
  time.sleep(30)
  p.stdin.write('save-all\nstop\n'); p.stdin.flush(); p.wait(timeout=90)
  if p.returncode: raise RuntimeError('Server exit code '+str(p.returncode))
 finally:
  if p.poll() is None: p.kill(); p.wait()
text=log.read_text(errors='replace')
regions=list(Path('server/world/region').glob('*.mca'))
assert regions, 'No world region saved'
assert re.search(r'nexustech',text,re.I), 'No mod evidence in logs'
lines=[x for x in text.splitlines() if re.search(r'cascad|loaded a new chunk',x,re.I)]
Path('evidence/cascade-matches.txt').write_text('\n'.join(lines)+'\n')
print('Forge booted, saved; region files:',len(regions),'cascade lines:',len(lines))
assert not lines, 'Cascade warnings observed: inspect logs'
print('PASS only one seed and spawn workload; no gameplay or modpack guarantee')
