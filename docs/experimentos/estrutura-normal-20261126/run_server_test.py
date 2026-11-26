import re, subprocess
from pathlib import Path

for num,marker in ((1,'STRUCTURE_FIRST_BOOT_PASS'),(2,'STRUCTURE_RELOAD_PASS')):
    path=Path('evidence/server-%d.log'%num)
    with path.open('w') as log:
        p=subprocess.Popen(['java','-Xms512M','-Xmx3G','-jar','forge-1.12.2-14.23.5.2860.jar','nogui'],cwd='server',stdin=subprocess.DEVNULL,stdout=log,stderr=subprocess.STDOUT)
        try:
            code=p.wait(timeout=720)
        except subprocess.TimeoutExpired:
            p.kill(); p.wait(); raise RuntimeError('boot %d timed out'%num)
    text=path.read_text(errors='replace')
    warnings=[line for line in text.splitlines() if re.search(r'cascad|loaded a new chunk|recursive structure generator',line,re.I)]
    Path('evidence/server-%d-warnings.txt'%num).write_text('\n'.join(warnings)+'\n')
    print('boot',num,'exit',code,'marker',marker in text,'180-state checks',text.count('STRUCTURE_180_STATES_VERIFIED'),'warnings',len(warnings))
    if code!=0 or marker not in text or 'STRUCTURE_TEST_FAILED' in text or warnings:
        raise RuntimeError('boot %d failed; inspect server log and warnings'%num)
    if text.count('STRUCTURE_180_STATES_VERIFIED')<4: raise RuntimeError('fewer than four independent state checks')
if not list(Path('server/world/region').glob('*.mca')): raise RuntimeError('no saved region files')
print('PASS: one normal seed, four quadrants, 180 states per structure before and after reload; structure-generator recursion guard and logged warnings only. Not proof of all indirect recursion, lighting, old builds or modpack compatibility.')
