from pathlib import Path
import ast, os
root=Path(__file__).resolve().parents[1]
source=Path(os.environ.get('REACTOR_SOURCE', str(root/'src/com/mikael8367/nexustech/nuclear/NuclearController.java')))
s=source.read_text()
tree=ast.parse((root/'tests/generate.py').read_text())
scaffold=next(ast.literal_eval(x.value) for x in tree.body if isinstance(x,ast.Assign) and any(isinstance(t,ast.Name) and t.id=='scaffold' for t in x.targets))
scaffold=scaffold.replace('void tripCoolantGuard(){enabled=false;scrammed=true;production=0;setIndicator(false);markDirty();}','')
api=s[s.index(' public boolean autoTemperatureEnabled()'):s.index(' // Opt-in for old worlds')]
trip=s[s.index(' void setCoolantGuard(boolean value)'):s.index(' private boolean reserved, updating')]
start=s[s.index(' private boolean startSafe(){'):s.index(' /** A structural interruption')]
indicator_start=s.index(' private void setIndicator(boolean active)') if ' private void setIndicator(boolean active)' in s else s.index(' private boolean setIndicator(boolean active)')
tick=s[s.index(' @Override public void update(){'):indicator_start].replace('@Override public void update()', 'public void update()')
if ' private boolean setIndicator(boolean active)' in s:
 scaffold=scaffold.replace('void setIndicator(boolean v){if(indicator!=v){indicator=v;if(onIndicator!=null)onIndicator.run();}}','boolean setIndicator(boolean v){if(!usable())return false;if(indicator!=v){indicator=v;if(onIndicator!=null)onIndicator.run();}return usable();}')
out=Path(os.environ.get('REACTOR_OUTPUT', str(root/'tests/reentrancy-generated/FakeTile.java')));out.parent.mkdir(parents=True,exist_ok=True);out.write_text(scaffold+api+trip+start+tick+'\n}\n')
print('Extracted actual API, coolant setter, SCRAM notifications, start, update; world/inventory/tank/indicator are stubs.')
