// Derived test: below critical only; original 999 expectation remains untouched.
package com.mikael8367.nexustech.nuclear;
import net.minecraft.nbt.NBTTagCompound;
public class AutoThermalBelowCriticalTest {
 static int checks;
 static void ok(boolean v,String s){checks++;if(!v)throw new AssertionError(s);}
 static FakeTile tile(int temp,int target,int water,boolean guard){FakeTile t=new FakeTile();t.temperature=temp;t.fuelTicks=500000;t.tank.amount=water;t.coolantGuard=guard;ok(t.setAutoTemperatureTarget(target),"target");ok(t.setAutoTemperatureEnabled(true),"enable");return t;}
 static void tick(FakeTile t){t.update();t.energy-=Math.min(t.energy,200);}
 public static void main(String[] args){
  for(int initial:new int[]{0,1}){FakeTile t=tile(initial,400,8000,true);int low=9999,high=0,used=0;for(int i=0;i<10000;i++){if(t.tank.amount<100)t.tank.amount=8000;int w=t.tank.amount;tick(t);used+=w-t.tank.amount;ok(t.enabled&&!t.scrammed&&t.production==200,"hold operation "+i);if(i>=500){low=Math.min(low,t.temperature);high=Math.max(high,t.temperature);}}ok(low>=398&&high<=401,"hold range "+low+" "+high);System.out.println("PASS target 400 from "+initial+": 10000 ticks, post-settle range "+low+".."+high+", generated 200 FE/t, water used="+used+" mB; externally replenished stub");}
  for(int target:new int[]{0,1,2,3,399,400,899,900})for(int initial:new int[]{0,996}){FakeTile t=tile(initial,target,8000,true);for(int i=0;i<1500;i++){tick(t);ok(!t.scrammed&&t.enabled&&t.production==200,"bounds");}ok(Math.abs(t.temperature-target)<=2,"target range "+target+" "+t.temperature);}
  System.out.println("PASS cold/overheat, target boundaries 0..900, 24000 ticks");
  FakeTile crit=tile(1000,400,100,true);crit.update();ok(crit.scrammed&&!crit.enabled&&crit.energy==0,"critical");
  FakeTile last=tile(400,400,1,true);last.update();ok(last.scrammed&&!last.enabled&&last.energy==200&&last.production==200&&last.tank.amount==0,"last mB trips and reports actual generation");
  FakeTile dry=tile(0,400,0,false);dry.update();ok(dry.scrammed&&!dry.enabled&&dry.energy==0,"auto water enforced even guard disabled");
  FakeTile nofuel=tile(400,400,100,true);nofuel.fuelTicks=0;nofuel.update();ok(!nofuel.enabled&&nofuel.production==0&&nofuel.tank.amount==100,"fuel exhausted");
  FakeTile full=tile(400,400,100,true);full.energy=FakeTile.CAPACITY;full.update();ok(full.temperature==399&&full.fuelTicks==500000&&full.production==0&&full.tank.amount==100,"battery full");
  FakeTile broken=tile(400,400,100,true);broken.structure=0;broken.update();ok(broken.scrammed&&!broken.enabled&&broken.energy==0,"structure loss");
  FakeTile unloaded=tile(400,400,100,true);unloaded.structure=-1;unloaded.update();ok(unloaded.scrammed&&!unloaded.enabled&&unloaded.temperature==400,"unloaded structure pause");
  System.out.println("PASS critical SCRAM, last mB telemetry and trip, no water regardless of guard setting, no fuel, full FE, structure");
  FakeTile dryLegacy=new FakeTile();dryLegacy.temperature=20;dryLegacy.fuelTicks=10;dryLegacy.update();ok(dryLegacy.temperature==22&&dryLegacy.energy==200,"legacy dry");
  FakeTile wetLegacy=new FakeTile();wetLegacy.temperature=20;wetLegacy.fuelTicks=10;wetLegacy.tank.amount=5;wetLegacy.update();ok(wetLegacy.temperature==18&&wetLegacy.energy==200&&wetLegacy.tank.amount==4,"legacy wet");
  FakeTile invalid=tile(400,400,10,true);ok(!invalid.setAutoTemperatureTarget(-1)&&!invalid.setAutoTemperatureTarget(901),"invalid target");invalid.world.serverThread=false;ok(!invalid.setAutoTemperatureTarget(1)&&!invalid.setAutoTemperatureEnabled(false),"off thread");invalid.world.serverThread=true;invalid.tileUsable=false;ok(!invalid.setAutoTemperatureTarget(1)&&!invalid.setAutoTemperatureEnabled(false),"bad tile");invalid.tileUsable=true;invalid.world.isRemote=true;ok(!invalid.setAutoTemperatureTarget(1),"client");invalid.world.isRemote=false;invalid.activeOperations=1;ok(!invalid.setAutoTemperatureEnabled(false),"operation");
  NBTTagCompound old=new NBTTagCompound();AutoThermalControl b=new AutoThermalControl();b.read(old);ok(!b.enabled()&&!b.invalid()&&b.target()==400,"old nbt");
  NBTTagCompound n=new NBTTagCompound();n.setBoolean("AutoThermal",true);n.setInteger("AutoTarget",1001);b.read(n);ok(b.invalid()&&!b.enabled(),"range");n.setInteger("AutoTarget",400);n.setByte("AutoThermal",(byte)2);b.read(n);ok(b.invalid()&&!b.enabled(),"boolean");n.setString("AutoThermal","true");b.read(n);ok(b.invalid()&&!b.enabled(),"type");NBTTagCompound partial=new NBTTagCompound();partial.setInteger("AutoTarget",400);b.read(partial);ok(b.invalid()&&!b.enabled(),"partial");NBTTagCompound badsave=new NBTTagCompound();b.write(badsave);b.read(badsave);ok(b.invalid()&&!b.enabled(),"invalid persistent");
  FakeTile before=tile(400,400,8000,true);NBTTagCompound saved=new NBTTagCompound();before.autoThermal.write(saved);FakeTile after=new FakeTile();after.temperature=before.temperature;after.fuelTicks=before.fuelTicks;after.tank.amount=before.tank.amount;after.coolantGuard=true;after.autoThermal.read(saved);ok(after.autoTemperatureEnabled()&&after.autoTemperatureTarget()==400,"config roundtrip");for(int i=0;i<1000;i++){tick(before);tick(after);ok(before.temperature==after.temperature&&before.energy==after.energy&&before.fuelTicks==after.fuelTicks&&before.tank.amount==after.tank.amount,"reload deterministic");}
  final FakeTile cb=tile(0,400,100,true);cb.fuelTicks=0;cb.fuelAvailable=true;cb.onExtract=new Runnable(){public void run(){cb.tank.amount=0;}};cb.update();ok(cb.scrammed&&cb.energy==0,"callback water");
  final FakeTile sat=tile(0,400,100,true);sat.fuelTicks=0;sat.fuelAvailable=true;sat.onExtract=new Runnable(){public void run(){sat.energy=FakeTile.CAPACITY;}};sat.update();ok(sat.energy==FakeTile.CAPACITY&&sat.production==0,"callback energy");
  final FakeTile hotcb=tile(0,400,100,true);hotcb.fuelTicks=0;hotcb.fuelAvailable=true;hotcb.onExtract=new Runnable(){public void run(){hotcb.temperature=1000;}};hotcb.update();ok(hotcb.scrammed&&!hotcb.enabled&&hotcb.energy==0,"callback hot");
  FakeTile oneFuel=tile(0,400,100,true);oneFuel.fuelTicks=1;oneFuel.update();ok(!oneFuel.enabled&&oneFuel.energy==200&&oneFuel.fuelTicks==0,"last fuel");
  FakeTile forged=tile(400,400,100,true);forged.autoThermal.read(partial);ok(forged.autoTemperatureInvalid()&&!forged.setAutoTemperatureEnabled(true),"malformed can't enable");
  FakeTile nullWorld=new FakeTile();nullWorld.world=null;ok(!nullWorld.setAutoTemperatureTarget(1)&&!nullWorld.setAutoTemperatureEnabled(true),"null world");
  FakeTile legacyLast=new FakeTile();legacyLast.temperature=20;legacyLast.fuelTicks=10;legacyLast.tank.amount=1;legacyLast.coolantGuard=true;legacyLast.update();ok(legacyLast.scrammed&&legacyLast.energy==200&&legacyLast.production==0,"legacy last mB unchanged");
  System.out.println("PASS legacy, setters thread/tile, invalid targets, stub NBT malformed/old and config reload, callbacks; checks="+checks);
 }
}
