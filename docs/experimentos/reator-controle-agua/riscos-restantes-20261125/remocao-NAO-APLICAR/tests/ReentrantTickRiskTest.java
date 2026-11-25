package com.mikael8367.nexustech.nuclear;
public class ReentrantTickRiskTest {
 public static void main(String[] args){
  FakeTile t=new FakeTile();t.fuelAvailable=true;final int[] notifications={0};
  t.onDirty=()->{notifications[0]++;if(!t.fuelConverting)t.removing=true;};
  t.update();
  boolean survives=!t.removing || t.fuelAvailable || t.fuelTicks==0;
  System.out.println((survives?"PASS ":"FAIL ")+"actual extracted update: later dirty callback removes controller; remaining slot="+t.fuelAvailable+" fuelTicks="+t.fuelTicks+" energy="+t.energy+" notifications="+notifications[0]+". Removal/finish modeled, not real Forge.");
  if(!survives)System.exit(1);
 }
}
