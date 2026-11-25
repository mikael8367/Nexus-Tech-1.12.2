from pathlib import Path
root=Path(__file__).resolve().parents[1]
s=(root/'src/com/mikael8367/nexustech/nuclear/NuclearController.java').read_text()
assert 'autoThermal.write(n);' in s and 'autoThermal.read(n);' in s
assert '|| autoThermal.invalid();' in s
api=s[s.index(' public boolean autoTemperatureEnabled()'):s.index(' // Opt-in for old worlds')]
start=s[s.index(' private boolean startSafe(){'):s.index(' /** A structural interruption')]
tick=s[s.index(' @Override public void update(){'):s.index(' private void setIndicator(boolean active)')].replace('@Override public void update()', 'public void update()')
scaffold='''package com.mikael8367.nexustech.nuclear;
class FakeTile {
 static final int CAPACITY=1000000, FE_PER_TICK=200,FUEL_TICKS=2400,SCRAM=1000,RESET=400;
 int energy,temperature,fuelTicks,production; boolean enabled=true,scrammed,formed=true;
 boolean updating,starting,startInterrupted,stopping,reserved,filling,bucketTransfer,automationExtracting,removing,coolantGuard;
 int activeOperations; World world=new World(); Tank tank=new Tank(); Inventory inventory=new Inventory();
 java.util.List<ItemStack> recoveryOverflow=new java.util.ArrayList<>();
 final AutoThermalControl autoThermal=new AutoThermalControl();
 static class World {boolean isRemote; boolean serverThread=true;}
 boolean fuelAvailable; Runnable onExtract;
 static class ItemStack {final boolean present; ItemStack(boolean p){present=p;} boolean isEmpty(){return !present;} int getCount(){return present?1:0;}}
 class Inventory {ItemStack getStackInSlot(int slot){return new ItemStack(fuelAvailable);} ItemStack extractItem(int slot,int n,boolean sim){boolean had=fuelAvailable;if(!sim){fuelAvailable=false;if(onExtract!=null)onExtract.run();}return new ItemStack(had);}}
 Runnable onDrain,onBind,onDirty,onIndicator; boolean indicator; int swallowedDirty;
 class Tank {int amount; void drainInternal(int n,boolean sim){amount-=Math.min(n,amount);try{markDirty();}catch(RuntimeException ex){swallowedDirty++;}if(onDrain!=null)onDrain.run();}}
 boolean tileUsable=true; boolean isFuel(ItemStack s){return !s.isEmpty();} boolean hasFuel(){return fuelTicks>0 || fuelAvailable;}
 int water(){return tank.amount;} boolean usable(){return !removing && world!=null && !world.isRemote && world.serverThread && tileUsable;}
 void markDirty(){if(onDirty!=null)onDirty.run();} void tripCoolantGuard(){enabled=false;scrammed=true;production=0;setIndicator(false);markDirty();}
 boolean beginTickOperation(){return usable();} void endOperation(){} void resetOutputForWorldTick(){}
 int structure=1; int validateStructure(){return structure;} void bindStructure(){if(onBind!=null)onBind.run();} void setVisualFormed(boolean b){}
 void syncPartVisuals(boolean b){} boolean allPartsBound(){return true;} void stopForStructureLoss(){if(enabled)scrammed=true;enabled=false;formed=false;production=0;}
 void releaseStructure(){} void acceptBucket(){} void setIndicator(boolean v){if(indicator!=v){indicator=v;if(onIndicator!=null)onIndicator.run();}} void pushOutput(){}
'''
out=root/'tests/generated/FakeTile.java';out.parent.mkdir(parents=True,exist_ok=True)
out.write_text(scaffold+api+start+tick+'\n}\n')
print('Generated FakeTile from actual experimental API and update method; external world/inventory/tank/NBT are stubs.')
