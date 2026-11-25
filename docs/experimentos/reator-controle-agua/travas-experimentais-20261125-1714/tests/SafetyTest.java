package com.mikael8367.nexustech.nuclear;
import net.minecraft.nbt.NBTTagCompound;
public class SafetyTest {
 static int n; static void ok(boolean x,String s){n++;if(!x)throw new AssertionError(s);}
 static FakeTile base(){FakeTile t=new FakeTile();t.enabled=false;t.fuelTicks=10;t.tank.amount=10;return t;}
 static NBTTagCompound bad(){NBTTagCompound n=new NBTTagCompound();n.setInteger("AutoTarget",1001);n.setBoolean("AutoThermal",true);return n;}
 public static void main(String[] a){
  FakeTile dry=base();dry.tank.amount=0;dry.setAutoTemperatureEnabled(true);dry.setEnabled(true);ok(!dry.enabled,"auto dry start");
  FakeTile legacy=base();legacy.tank.amount=0;legacy.setEnabled(true);ok(legacy.enabled,"legacy dry start");legacy.update();ok(legacy.energy==200&&legacy.fuelTicks==9&&legacy.temperature==2,"legacy progress");
  FakeTile f=base();f.fuelTicks=0;f.setEnabled(true);ok(!f.enabled,"no fuel");
  FakeTile cap=base();cap.energy=FakeTile.CAPACITY;cap.setEnabled(true);cap.update();ok(cap.energy==FakeTile.CAPACITY&&cap.fuelTicks==10&&cap.tank.amount==10,"full battery no consumption");
  FakeTile broken=base();broken.structure=0;broken.setEnabled(true);ok(!broken.enabled,"bad structure");
  FakeTile state=base();state.energy=-1;state.setEnabled(true);ok(!state.enabled,"invalid energy");state.energy=0;state.temperature=1000;state.setEnabled(true);ok(!state.enabled,"critical start");
  FakeTile inv=base();inv.autoThermal.read(bad());inv.setEnabled(true);ok(!inv.enabled,"bad NBT blocks legacy");ok(!inv.setAutoTemperatureTarget(300),"target is not repair");inv.setAutoTemperatureEnabled(false);ok(inv.autoTemperatureInvalid(),"disable does not clear");ok(!inv.repairAutoTemperatureConfig(901),"invalid repair");ok(inv.repairAutoTemperatureConfig(300),"explicit repair");ok(!inv.autoTemperatureEnabled()&&!inv.autoTemperatureInvalid(),"repair off");inv.setEnabled(true);ok(inv.enabled,"manual start after repair");
  FakeTile old=base();old.autoThermal.read(new NBTTagCompound());old.tank.amount=0;old.setEnabled(true);ok(old.enabled,"old NBT legacy");
  final FakeTile nested=base();nested.onBind=new Runnable(){public void run(){nested.setEnabled(true);}};nested.setEnabled(true);ok(!nested.enabled&&nested.scrammed,"nested start");
  final FakeTile stop=base();stop.onBind=new Runnable(){public void run(){stop.setEnabled(false);}};stop.setEnabled(true);ok(!stop.enabled&&stop.scrammed,"nested stop");
  final FakeTile hot=base();hot.temperature=400;hot.setAutoTemperatureEnabled(true);hot.setEnabled(true);hot.onDrain=new Runnable(){public void run(){hot.temperature=1000;}};hot.update();ok(hot.scrammed&&!hot.enabled&&hot.energy==0&&hot.fuelTicks==10,"drain callback critical");
  final FakeTile drain=base();drain.temperature=400;drain.setAutoTemperatureEnabled(true);drain.setEnabled(true);drain.onDrain=new Runnable(){public void run(){drain.tank.amount=0;}};drain.update();ok(drain.scrammed&&!drain.enabled&&drain.energy==0&&drain.fuelTicks==10,"drain callback interference");
  final FakeTile stopDrain=base();stopDrain.temperature=400;stopDrain.setAutoTemperatureEnabled(true);stopDrain.setEnabled(true);stopDrain.onDrain=new Runnable(){public void run(){stopDrain.setEnabled(false);}};stopDrain.update();ok(stopDrain.scrammed&&!stopDrain.enabled&&stopDrain.energy==0,"stop during drain");
  final FakeTile boom=base();boom.temperature=400;boom.setAutoTemperatureEnabled(true);boom.setEnabled(true);boom.onDrain=new Runnable(){public void run(){throw new RuntimeException("drain callback");}};try{boom.update();throw new AssertionError("exception not propagated");}catch(RuntimeException e){}ok(boom.scrammed&&!boom.enabled&&boom.energy==0&&boom.fuelTicks==10,"exception fail closed");
  FakeTile restart=base();restart.scrammed=true;restart.temperature=401;restart.setEnabled(true);ok(!restart.enabled&&restart.scrammed,"hot restart");restart.temperature=400;restart.setEnabled(true);ok(restart.enabled&&!restart.scrammed,"manual cool restart");
  System.out.println("PASS safety cases checks="+n+"; real extracted start/update; NBT class stub, world/tank/inventory stub");
 }
}
