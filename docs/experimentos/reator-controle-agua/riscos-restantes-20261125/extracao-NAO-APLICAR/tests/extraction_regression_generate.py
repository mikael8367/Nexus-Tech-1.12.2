from pathlib import Path
import ast
root=Path(__file__).resolve().parents[1]
s=(root/'src/com/mikael8367/nexustech/nuclear/NuclearController.java').read_text()
tree=ast.parse((root/'tests/generate.py').read_text())
scaffold=next(ast.literal_eval(x.value) for x in tree.body if isinstance(x,ast.Assign) and any(isinstance(t,ast.Name) and t.id=='scaffold' for t in x.targets))
scaffold=scaffold.replace('automationExtracting,removing,coolantGuard;', 'automationExtracting,energyExtracting,removing,coolantGuard;')
scaffold=scaffold.replace('class Inventory {ItemStack getStackInSlot', 'class Inventory {void setWithoutNotification(int slot,ItemStack s){fuelAvailable=false;} ItemStack getStackInSlot')
scaffold=scaffold.replace('final AutoThermalControl autoThermal=', 'Inventory recoveryInventory=inventory; final AutoThermalControl autoThermal=')
scaffold=scaffold.replace('static class ItemStack {final boolean present;', 'static class ItemStack {static final ItemStack EMPTY=new ItemStack(false); void shrink(int n){} final boolean present;')
scaffold=scaffold.replace('void markDirty(){if(onDirty!=null)onDirty.run();}', 'void markDirty(){if(fuelTicks==FUEL_TICKS && onExtract!=null){Runnable r=onExtract;onExtract=null;r.run();}if(onDirty!=null)onDirty.run();}')
scaffold=scaffold.replace('void tripCoolantGuard(){enabled=false;scrammed=true;production=0;setIndicator(false);markDirty();}','')
api=s[s.index(' public boolean autoTemperatureEnabled()'):s.index(' // Opt-in for old worlds')]
trip=s[s.index(' void setCoolantGuard(boolean value)'):s.index(' private boolean reserved, updating')]
helper=s[s.index(' private boolean consumeOneFuel()'):s.index(' private boolean startSafe()')]
helper=helper.replace('org.apache.logging.log4j.LogManager.getLogger("NexusTech").error("Nuclear fuel conversion committed but dirty notification failed; persistence is uncertain",ex);','swallowedDirty++;')
start=s[s.index(' private boolean startSafe(){'):s.index(' /** A structural interruption')]
tick=s[s.index(' @Override public void update(){'):s.index(' private void setIndicator(boolean active)')].replace('@Override public void update()', 'public void update()')
out=root/'tests/extraction-regression-generated/FakeTile.java';out.parent.mkdir(parents=True,exist_ok=True);out.write_text(scaffold+api+trip+helper+start+tick+'\n}\n')
print('Extracted actual fuel helper, API, coolant trip, start, update; stub translates prior onExtract hook to conversion notification.')
