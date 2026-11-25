from pathlib import Path
import ast
root=Path(__file__).resolve().parents[1]
s=(root/'src/com/mikael8367/nexustech/nuclear/NuclearController.java').read_text()
tree=ast.parse((root/'tests/generate.py').read_text())
scaffold=next(ast.literal_eval(x.value) for x in tree.body if isinstance(x,ast.Assign) and any(isinstance(t,ast.Name) and t.id=='scaffold' for t in x.targets))
scaffold=scaffold.replace('void tripCoolantGuard(){enabled=false;scrammed=true;production=0;setIndicator(false);markDirty();}','')
api=s[s.index(' public boolean autoTemperatureEnabled()'):s.index(' // Opt-in for old worlds')]
trip=s[s.index(' void setCoolantGuard(boolean value)'):s.index(' private boolean reserved, updating')]
start=s[s.index(' private boolean startSafe(){'):s.index(' /** A structural interruption')]
tick=s[s.index(' @Override public void update(){'):s.index(' private void setIndicator(boolean active)')].replace('@Override public void update()', 'public void update()')
out=root/'tests/transaction-generated/FakeTile.java';out.parent.mkdir(parents=True,exist_ok=True);out.write_text(scaffold+api+trip+start+tick+'\n}\n')
print('Extracted actual API, coolant setter and trip, start, update; world/inventory/tank/indicator are stubs.')
