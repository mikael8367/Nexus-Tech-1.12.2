package com.mikael8367.nexustech.nuclear;
public class ReviewFixTest {
 static int failed, passed; interface Case {void run();} static void check(boolean b,String s){if(!b)throw new AssertionError(s);} static void run(String n,Case c){try{c.run();passed++;System.out.println("PASS "+n);}catch(Throwable e){failed++;System.out.println("FAIL "+n+": "+e);}}
 static FakeTile base(){FakeTile t=new FakeTile();t.enabled=false;t.fuelTicks=10;t.tank.amount=10;return t;}
 static void heat(final int temp, final boolean automatic){FakeTile t=base();if(automatic)t.setAutoTemperatureEnabled(true);t.temperature=temp;t.setEnabled(true);t.update();check(t.scrammed&&!t.enabled&&t.production==0&&t.energy==0&&t.fuelTicks==10,"must latch before cooling and generation at "+temp);}
 public static void main(String[] args){
 run("999 manual",()->heat(999,false));run("998 manual",()->heat(998,false));run("997 manual",()->heat(997,false));run("999 auto",()->heat(999,true));run("998 auto",()->heat(998,true));run("997 auto",()->heat(997,true));
 run("996 below threshold",()->{FakeTile t=base();t.temperature=996;t.setEnabled(true);t.update();check(!t.scrammed&&t.enabled&&t.energy==200&&t.fuelTicks==9,"996 should generate");});
 run("critical manual preexisting",()->{FakeTile t=base();t.enabled=true;t.temperature=1000;t.tank.amount=0;t.update();check(t.scrammed&&!t.enabled&&t.energy==0&&t.fuelTicks==10,"manual hot generation");});
 run("dry already running",()->{FakeTile t=base();t.coolantGuard=true;t.enabled=true;t.indicator=true;t.production=200;t.tank.amount=0;final int[] marked={0};t.onDirty=()->marked[0]++;t.setEnabled(true);check(t.scrammed&&!t.enabled&&!t.indicator&&t.production==0&&marked[0]>0,"dry trip, indicator, dirty");});
 run("stop indicator reentry",()->{FakeTile t=base();t.enabled=true;t.indicator=true;t.onIndicator=()->t.setEnabled(true);t.setEnabled(false);check(!t.enabled&&t.production==0&&!t.indicator,"reactivated in stop callback");});
 run("stop inside start",()->{FakeTile t=base();t.onBind=()->t.setEnabled(false);t.setEnabled(true);check(!t.enabled&&t.scrammed,"nested emergency stop");});
 run("stop dirty reentry",()->{FakeTile t=base();t.enabled=true;t.onDirty=()->t.setEnabled(true);t.setEnabled(false);check(!t.enabled,"dirty callback reactivated");});
 run("dry during start",()->{FakeTile t=base();t.coolantGuard=true;t.enabled=true;t.indicator=true;t.onBind=()->t.tank.amount=0;t.setEnabled(true);check(t.scrammed&&!t.enabled&&!t.indicator,"dry during bind");});
 for(final boolean auto:new boolean[]{false,true})run("fuel callback critical auto="+auto,()->{FakeTile t=base();t.fuelTicks=0;t.fuelAvailable=true;if(auto)t.setAutoTemperatureEnabled(true);else t.tank.amount=0;t.setEnabled(true);t.onExtract=()->t.temperature=1000;t.update();check(t.scrammed&&!t.enabled&&t.energy==0&&t.production==0,"fuel callback generated");});
 run("tank markDirty exception swallowed",()->{FakeTile t=base();t.setEnabled(true);final int[] n={0};t.onDirty=()->{if(n[0]++==0)throw new RuntimeException("notification");};t.update();check(t.swallowedDirty==1&&t.energy==200&&t.fuelTicks==9,"tank exception should be swallowed, not fail closed");});
 System.out.println("RESULT passed="+passed+" failed="+failed);if(failed!=0)System.exit(1);
 }
}
