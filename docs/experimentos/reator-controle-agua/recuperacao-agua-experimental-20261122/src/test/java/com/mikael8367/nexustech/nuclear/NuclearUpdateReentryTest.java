package com.mikael8367.nexustech.nuclear;

import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;

/** Regression for callbacks made before and during an FE push. */
public final class NuclearUpdateReentryTest {
 static class ReentrantReceiver extends TileEntity {
  final NuclearController controller; final int stage;
  int probes, receives, accepted; boolean throwOnProbe;
  final net.minecraft.nbt.NBTTagCompound before;
  ReentrantReceiver(NuclearController c,int stage){controller=c;this.stage=stage;before=c.writeToNBT(new net.minecraft.nbt.NBTTagCompound());}
  void reenter(){
   if(++probes>1)throw new AssertionError("update reentered through callback stage "+stage);
   if(throwOnProbe)throw new AssertionError("deliberate callback failure");
   controller.readFromNBT(before); // An old snapshot must not restore FE/fuel inside a push.
   controller.update();
  }
  final IEnergyStorage sink=new IEnergyStorage(){
   public int receiveEnergy(int amount,boolean simulate){if(stage==2)reenter();if(!simulate){receives++;accepted+=amount;}return amount;}
   public int extractEnergy(int amount,boolean simulate){return 0;}
   public int getEnergyStored(){return accepted;} public int getMaxEnergyStored(){return 100000;}
   public boolean canExtract(){return false;} public boolean canReceive(){if(stage==1)reenter();return true;}
  };
  @Override public <T>T getCapability(Capability<T> cap,EnumFacing face){
   if(cap!=CapabilityEnergy.ENERGY)return super.getCapability(cap,face);
   if(stage==0)reenter();
   return CapabilityEnergy.ENERGY.cast(sink);
  }
 }
 static void callback(int stage)throws Exception{
  NuclearReactorTest.TestWorld w=NuclearReactorTest.world();
  NuclearController c=NuclearReactorTest.build(w,EnumFacing.NORTH);
  c.energy=1000;c.fuelTicks=5;c.setEnabled(true);
  ReentrantReceiver sink=new ReentrantReceiver(c,stage);
  w.put(NuclearReactorTest.P.north(),Blocks.STONE.getDefaultState(),sink);
  c.update();
  NuclearReactorTest.eq(sink.probes,1,"one callback stage "+stage);
  NuclearReactorTest.eq(c.fuelTicks,4,"only one fuel tick stage "+stage);
  NuclearReactorTest.eq(c.energy,800,"energy conserved stage "+stage);
  NuclearReactorTest.eq(sink.accepted,400,"output limited stage "+stage);
  NuclearReactorTest.eq(sink.receives,1,"one push stage "+stage);
  w.time++;sink.probes=0;c.update();
  NuclearReactorTest.eq(c.fuelTicks,3,"next tick runs stage "+stage);
  NuclearReactorTest.eq(sink.accepted,800,"next tick output stage "+stage);
 }
 static void unlockOnFailure()throws Exception{
  NuclearReactorTest.TestWorld w=NuclearReactorTest.world();
  NuclearController c=NuclearReactorTest.build(w,EnumFacing.NORTH);
  c.energy=1000;c.fuelTicks=5;c.setEnabled(true);
  ReentrantReceiver sink=new ReentrantReceiver(c,0);sink.throwOnProbe=true;
  w.put(NuclearReactorTest.P.north(),Blocks.STONE.getDefaultState(),sink);
  try{c.update();throw new AssertionError("expected callback failure");}
  catch(AssertionError ex){if(!"deliberate callback failure".equals(ex.getMessage()))throw ex;}
  sink.throwOnProbe=false;sink.probes=0;w.time++;c.update();
  NuclearReactorTest.eq(c.fuelTicks,3,"guard released after error");
  NuclearReactorTest.eq(sink.accepted,400,"subsequent output after error");
 }
 public static void main(String[] args)throws Exception{
  NuclearRecoveryTest.main(args);
  for(int stage=0;stage<3;stage++)callback(stage);
  unlockOnFailure();
  System.out.println("NuclearUpdateReentryTest: PASS");
 }
}
