package com.mikael8367.nexustech.nuclear;
public class IndependentReviewTest {
 static int pass,fail; interface Case{void run();}
 static void ck(boolean v,String m){if(!v)throw new AssertionError(m);}
 static void run(String n,Case c){try{c.run();pass++;System.out.println("PASS "+n);}catch(Throwable e){fail++;System.out.println("FAIL "+n+": "+e);}}
 static FakeTile base(){FakeTile t=new FakeTile();t.enabled=true;t.fuelTicks=10;t.tank.amount=10;t.indicator=true;t.production=200;return t;}
 public static void main(String[] args){
  run("running no fuel shuts down notifies",()->{FakeTile t=base();t.fuelTicks=0;t.fuelAvailable=false;int[] d={0};t.onDirty=()->d[0]++;t.setEnabled(true);ck(!t.enabled&&!t.indicator&&t.production==0&&d[0]>0,"off indicator and dirty required");});
  run("automatic enabled dry running immediately trips",()->{FakeTile t=base();t.tank.amount=0;int[] d={0};t.onDirty=()->d[0]++;ck(t.setAutoTemperatureEnabled(true),"enable failed");ck(t.autoTemperatureEnabled()&&!t.enabled&&t.scrammed&&!t.indicator&&t.production==0&&d[0]>0,"immediate SCRAM required");});
  run("auto changes blocked in stop indicator callback",()->{FakeTile t=base();boolean[] r={true,true};int old=t.autoTemperatureTarget();t.onIndicator=()->{r[0]=t.setAutoTemperatureEnabled(true);r[1]=t.setAutoTemperatureTarget(500);};t.setEnabled(false);ck(!r[0]&&!r[1]&&!t.autoTemperatureEnabled()&&t.autoTemperatureTarget()==old&&!t.enabled,"reentrant auto modification");});
  run("auto changes blocked in stop dirty callback",()->{FakeTile t=base();boolean[] r={true,true};int old=t.autoTemperatureTarget();t.onDirty=()->{r[0]=t.setAutoTemperatureEnabled(true);r[1]=t.setAutoTemperatureTarget(500);};t.setEnabled(false);ck(!r[0]&&!r[1]&&!t.autoTemperatureEnabled()&&t.autoTemperatureTarget()==old&&!t.enabled,"reentrant auto modification");});
  run("auto SCRAM callback cannot restart or reconfigure",()->{FakeTile t=base();t.tank.amount=0;boolean[] r={true,true};t.onIndicator=()->{t.tank.amount=10;t.setEnabled(true);r[0]=t.setAutoTemperatureEnabled(false);r[1]=t.setAutoTemperatureTarget(500);};t.setAutoTemperatureEnabled(true);ck(!t.enabled&&t.scrammed&&!t.indicator&&t.autoTemperatureEnabled()&&!r[0]&&!r[1],"reentrant auto trip");});
  run("automatic safe running stays running",()->{FakeTile t=base();ck(t.setAutoTemperatureEnabled(true)&&t.enabled&&!t.scrammed&&t.indicator,"safe activation changed state");});
  run("automatic dry offline does not trip",()->{FakeTile t=base();t.enabled=false;t.tank.amount=0;ck(t.setAutoTemperatureEnabled(true)&&!t.scrammed&&!t.enabled,"offline trip");});
  run("manual restart after automatic trip and refill",()->{FakeTile t=base();t.tank.amount=0;t.setAutoTemperatureEnabled(true);t.tank.amount=10;t.setEnabled(true);ck(t.enabled&&!t.scrammed,"manual restart failed");});
  run("repair blocked during stopping",()->{FakeTile t=base();net.minecraft.nbt.NBTTagCompound n=new net.minecraft.nbt.NBTTagCompound();n.setInteger("AutoTarget",1001);n.setBoolean("AutoThermal",true);t.autoThermal.read(n);boolean[] r={true};t.onIndicator=()->r[0]=t.repairAutoTemperatureConfig(300);t.setEnabled(false);ck(!r[0]&&t.autoTemperatureInvalid(),"repair ran during stop");});
  run("trip indicator exception leaves stopped guard released",()->{FakeTile t=base();t.tank.amount=0;t.onIndicator=()->{throw new RuntimeException("indicator");};try{t.setAutoTemperatureEnabled(true);throw new AssertionError("missing exception");}catch(RuntimeException expected){}ck(!t.enabled&&t.scrammed&&t.production==0&&!t.stopping,"unsafe after exception");});
  System.out.println("RESULT passed="+pass+" failed="+fail);if(fail!=0)System.exit(1);
 }
}
