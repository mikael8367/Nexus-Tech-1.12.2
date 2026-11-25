from pathlib import Path
import ast, os
root=Path(__file__).resolve().parents[1]
s=Path(os.environ['REACTOR_SOURCE']).read_text()
tree=ast.parse((root/'tests/generate.py').read_text())
scaffold=next(ast.literal_eval(x.value) for x in tree.body if isinstance(x,ast.Assign) and any(isinstance(t,ast.Name) and t.id=='scaffold' for t in x.targets))
scaffold=scaffold.replace('static class World {boolean isRemote; boolean serverThread=true;}', '''
 static class State {boolean active; State(boolean a){active=a;} Object getBlock(){return NuclearRegistry.CONTROLLER;} Boolean getValue(Object p){return active;} State withProperty(Object p,boolean b){return new State(b);}}
 static class NuclearRegistry {static final Object CONTROLLER=new Object();}
 static class NuclearBlock {static final Object ACTIVE=new Object();}
 static class World {boolean isRemote; boolean serverThread=true; boolean loaded=true, rejectSet; FakeTile current; World(FakeTile t){current=t;} boolean isBlockLoaded(Object p){return loaded;} FakeTile getTileEntity(Object p){return current;} State getBlockState(Object p){return new State(current.indicator);} boolean setBlockState(Object p,State s,int flags){if(rejectSet)return false;FakeTile target=current; target.indicator=s.active;if(target.onIndicator!=null)target.onIndicator.run();return true;}}
 Object pos=new Object();''').replace('World world=new World();','World world=new World(this);')
scaffold=scaffold.replace('void tripCoolantGuard(){enabled=false;scrammed=true;production=0;setIndicator(false);markDirty();}','')
scaffold=scaffold.replace('&& tileUsable;}', '&& tileUsable && world.isBlockLoaded(pos) && world.getTileEntity(pos)==this;}')
scaffold=scaffold.replace('void setIndicator(boolean v){if(indicator!=v){indicator=v;if(onIndicator!=null)onIndicator.run();}}','')
scaffold=scaffold.replace('void stopForStructureLoss(){if(enabled)scrammed=true;enabled=false;formed=false;production=0;}','')
scaffold=scaffold.replace('void setVisualFormed(boolean b){}','Runnable onVisual; void setVisualFormed(boolean b){if(onVisual!=null)onVisual.run();}')
api=s[s.index(' public boolean autoTemperatureEnabled()'):s.index(' // Opt-in for old worlds')]
trip=s[s.index(' void setCoolantGuard(boolean value)'):s.index(' private boolean reserved, updating')]
start=s[s.index(' private boolean startSafe(){'):s.index(' /** A structural interruption')]
indicator_start=s.index(' private void setIndicator(boolean active)') if ' private void setIndicator(boolean active)' in s else s.index(' private boolean setIndicator(boolean active)')
tick=s[s.index(' @Override public void update(){'):indicator_start].replace('@Override public void update()', 'public void update()')
indicator=s[s.index(' private void setIndicator(boolean active)') if ' private void setIndicator(boolean active)' in s else s.index(' private boolean setIndicator(boolean active)'):s.index(' private void pushOutput()')].replace('IBlockState','State')
stop=s[s.index(' private void stopForStructureLoss(){'):s.index(' @Override public void update(){')]
out=Path(os.environ['REACTOR_OUTPUT']);out.parent.mkdir(parents=True,exist_ok=True);out.write_text(scaffold+api+trip+start+stop+tick+indicator+'\n}\n')
print('Extracted actual indicator, trip, start and update; model world, tile and state are stubs.')
