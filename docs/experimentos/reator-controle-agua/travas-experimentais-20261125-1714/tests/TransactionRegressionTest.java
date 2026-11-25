package com.mikael8367.nexustech.nuclear;
public class TransactionRegressionTest {
 static int fail, pass; interface Case {void run();}
 static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
 static void run(String n,Case c){try{c.run();pass++;System.out.println("PASS "+n);}catch(Throwable e){fail++;System.out.println("FAIL "+n+": "+e);}}
 static FakeTile base(){FakeTile t=new FakeTile();t.enabled=true;t.fuelTicks=10;t.tank.amount=10;t.indicator=true;t.production=200;return t;}
 public static void main(String[] args){
  run("critical already running: latch, indicator, dirty",()->{FakeTile t=base();t.temperature=1000;final int[] n={0};t.onDirty=()->n[0]++;t.setEnabled(true);check(t.scrammed&&!t.enabled&&!t.indicator&&t.production==0&&n[0]>0,"critical shutdown incomplete");});
  run("coolant trip indicator reentry cannot restart",()->{FakeTile t=base();t.tank.amount=0;t.onIndicator=()->{t.tank.amount=10;t.setEnabled(true);};t.setCoolantGuard(true);check(!t.enabled&&t.scrammed&&!t.indicator&&t.production==0,"restart during indicator callback");});
  run("coolant trip dirty reentry cannot restart or disable guard",()->{FakeTile t=base();t.tank.amount=0;final boolean[] once={false};t.onDirty=()->{if(!once[0]){once[0]=true;t.tank.amount=10;t.setCoolantGuard(false);t.setEnabled(true);}};t.setCoolantGuard(true);check(!t.enabled&&t.scrammed&&t.coolantGuard&&!t.indicator,"restart or guard change during dirty callback");});
  run("legacy no water capacity after fuel callback",()->{FakeTile t=base();t.tank.amount=0;t.fuelTicks=0;t.fuelAvailable=true;t.energy=0;t.onExtract=()->t.energy=FakeTile.CAPACITY;t.update();check(t.energy==FakeTile.CAPACITY&&t.fuelTicks==FakeTile.FUEL_TICKS&&t.production==0,"generated over capacity or consumed fuel tick");});
  run("legacy no water negative energy callback",()->{FakeTile t=base();t.tank.amount=0;t.fuelTicks=0;t.fuelAvailable=true;t.onExtract=()->t.energy=-1;t.update();check(t.energy==-1&&t.fuelTicks==FakeTile.FUEL_TICKS&&t.production==0,"generated on invalid energy");});
  run("legacy no water still generates when capacity fits",()->{FakeTile t=base();t.tank.amount=0;t.fuelTicks=0;t.fuelAvailable=true;t.energy=FakeTile.CAPACITY-FakeTile.FE_PER_TICK;t.update();check(t.energy==FakeTile.CAPACITY&&t.fuelTicks==FakeTile.FUEL_TICKS-1&&t.production==200,"valid capacity blocked");});
  run("nested stop during start still latches",()->{FakeTile t=base();t.enabled=false;t.onBind=()->t.setEnabled(false);t.setEnabled(true);check(!t.enabled&&t.scrammed,"nested stop lost");});
  run("manual restart only after trip finishes",()->{FakeTile t=base();t.tank.amount=0;t.onIndicator=()->{t.tank.amount=10;t.update();t.setEnabled(true);};t.setCoolantGuard(true);check(!t.enabled&&t.scrammed&&t.energy==0,"nested tick or restart ran");t.onIndicator=null;t.setEnabled(true);check(t.enabled&&!t.scrammed,"normal manual restart blocked");});
  run("trip callback exception keeps latch and releases guard",()->{FakeTile t=base();t.tank.amount=0;t.onIndicator=()->{throw new RuntimeException("indicator");};try{t.setCoolantGuard(true);throw new AssertionError("exception absent");}catch(RuntimeException ex){}check(t.scrammed&&!t.enabled&&t.production==0&&!t.stopping,"exception state or stuck guard");});
  run("automatic no-drain capacity after extraction",()->{FakeTile t=base();t.tank.amount=10;t.fuelTicks=0;t.fuelAvailable=true;t.temperature=500;t.setAutoTemperatureEnabled(true);t.onExtract=()->t.energy=FakeTile.CAPACITY;t.update();check(t.energy==FakeTile.CAPACITY&&t.fuelTicks==FakeTile.FUEL_TICKS&&t.production==0&&t.tank.amount==10,"auto no-drain generated");});
  System.out.println("RESULT passed="+pass+" failed="+fail);if(fail!=0)System.exit(1);
 }
}
