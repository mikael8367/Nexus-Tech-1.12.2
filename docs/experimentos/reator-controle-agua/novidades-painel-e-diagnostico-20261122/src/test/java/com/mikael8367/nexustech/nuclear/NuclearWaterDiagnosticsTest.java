package com.mikael8367.nexustech.nuclear;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import net.minecraft.init.Bootstrap;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.storage.MapStorage;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import sun.misc.Unsafe;

/** Standalone only: inspection does not access the world or perform transfers. */
public final class NuclearWaterDiagnosticsTest {
 static final BlockPos P=NuclearReactorTest.P;
 static void ok(boolean b,String label){if(!b)throw new AssertionError(label);}
 static void eq(long a,long b,String label){if(a!=b)throw new AssertionError(label+": "+a+" != "+b);}
 static void invalid(Runnable r){try{r.run();throw new AssertionError("expected invalid input rejection");}catch(IllegalArgumentException expected){}}
 static void snapshotAndPlans(){
  NuclearRecovery r=new NuclearRecovery();
  NuclearRecovery.WaterSnapshot empty=r.waterSnapshot(P);
  eq(empty.pendingMb,0,"empty amount");eq(empty.entryCount,0,"empty count");
  r.enqueueWater(P,8000);r.enqueueWater(P,500);r.enqueueWater(P.east(),333);
  r.setDirty(false);
  NuclearRecovery.WaterSnapshot s=r.waterSnapshot(P);
  ok(!r.isDirty(),"snapshot does not mark dirty");
  eq(s.pendingMb,8500,"position total");eq(s.entryCount,2,"position count");
  eq(r.waterSnapshot(P.east()).pendingMb,333,"neighbor isolated");
  NBTTagCompound before=r.writeToNBT(new NBTTagCompound());r.setDirty(false);
  NuclearRecovery.WaterPlan a=s.plan(0),b=s.plan(7500),c=s.plan(8000);
  eq(a.roomMb,8000,"empty tank room");eq(a.transferableMb,8000,"full capacity estimate");eq(a.remainingMb,500,"remaining estimate");
  eq(b.roomMb,500,"partial room");eq(b.transferableMb,500,"partial estimate");eq(b.remainingMb,8000,"partial remainder");
  eq(c.roomMb,0,"full tank room");eq(c.transferableMb,0,"full tank cannot receive");eq(c.remainingMb,8500,"full tank remainder");
  eq(empty.plan(0).transferableMb,0,"empty reserve");
  invalid(()->s.plan(-1));invalid(()->s.plan(Integer.MIN_VALUE));invalid(()->s.plan(8001));invalid(()->s.plan(Integer.MAX_VALUE));invalid(()->r.waterSnapshot(null));
  ok(!r.isDirty(),"inspection does not mark dirty");ok(before.equals(r.writeToNBT(new NBTTagCompound())),"inspection does not modify serialized state");
  eq(r.takeWater(P,8000),8000,"actual recovery unchanged");
  eq(s.pendingMb,8500,"snapshot remains immutable and can become stale");
  eq(r.waterSnapshot(P).pendingMb,500,"fresh snapshot after debit");
 }
 static void largeAndInvalidNbt(){
  NuclearRecovery r=new NuclearRecovery();r.enqueueWater(P,Integer.MAX_VALUE);r.enqueueWater(P,Integer.MAX_VALUE);r.enqueueWater(P,200);
  NuclearRecovery.WaterSnapshot s=r.waterSnapshot(P);long expected=2L*Integer.MAX_VALUE+200L;
  eq(s.pendingMb,expected,"long total without int saturation");eq(s.entryCount,3,"large entry count");
  eq(r.waterAt(P),Integer.MAX_VALUE,"existing int query stays saturated");
  eq(s.plan(1).transferableMb,7999,"large estimate bounded");eq(s.plan(1).remainingMb,expected-7999,"large remainder exact");
  NBTTagCompound n=r.writeToNBT(new NBTTagCompound());NBTTagList water=n.getTagList("Water",10);
  for(int amount:new int[]{0,-1,Integer.MIN_VALUE}){NBTTagCompound t=new NBTTagCompound();t.setInteger("X",P.getX());t.setInteger("Y",P.getY());t.setInteger("Z",P.getZ());t.setInteger("Amount",amount);water.appendTag(t);}
  NuclearRecovery loaded=new NuclearRecovery();loaded.readFromNBT(n);
  eq(loaded.waterSnapshot(P).pendingMb,expected,"nonpositive NBT amounts ignored by existing reader");
  eq(loaded.waterSnapshot(P).entryCount,3,"invalid records do not count");
  // Audit of existing reader: omitted coordinates default to zero; no repair is attempted.
  NBTTagCompound malformed=new NBTTagCompound();NBTTagList bad=new NBTTagList();
  NBTTagCompound missing=new NBTTagCompound();missing.setInteger("Y",64);missing.setInteger("Amount",42);bad.appendTag(missing);
  NBTTagCompound textAmount=new NBTTagCompound();textAmount.setString("Amount","1000");bad.appendTag(textAmount);
  malformed.setTag("Water",bad);NuclearRecovery odd=new NuclearRecovery();odd.readFromNBT(malformed);
  eq(odd.waterSnapshot(new BlockPos(0,64,0)).pendingMb,42,"missing coordinates default to zero in existing reader");
  eq(odd.waterSnapshot(BlockPos.ORIGIN).pendingMb,0,"non-numeric amount ignored by existing reader");
 }
 static void compareEstimateToClaim()throws Exception{
  for(int held:new int[]{0,1,4000,8000})for(int queued:new int[]{0,1,500,8000,8500}){
   NuclearWaterRecoveryTest.W w=NuclearWaterRecoveryTest.world();NuclearRecovery r=NuclearRecovery.get(w);
   if(queued>0)r.enqueueWater(P,queued);NuclearController c=NuclearWaterRecoveryTest.replace(w);
   if(held>0)eq(NuclearWaterRecoveryTest.input(c).fill(new FluidStack(FluidRegistry.WATER,held),true),held,"prefill");
   NuclearRecovery.WaterPlan plan=r.waterSnapshot(P).plan(c.water());c.reclaimWater();
   eq(c.water()-held,plan.transferableMb,"quiescent actual claim matches estimate");
   eq(r.waterAt(P),plan.remainingMb,"quiescent actual remainder matches estimate");
  }
 }
 static class CallbackWorld extends NuclearWaterRecoveryTest.W {
  boolean inspect,inCallback;int callbacks;long observed;
  @Override public void markChunkDirty(BlockPos p,TileEntity tile){
   if(!inspect)return;
   ok(!inCallback,"diagnostic triggers no nested dirty callback");inCallback=true;callbacks++;
   try{
    NuclearRecovery r=NuclearRecovery.get(this);NBTTagCompound before=r.writeToNBT(new NBTTagCompound());
    NuclearRecovery.WaterSnapshot s=r.waterSnapshot(P);observed=s.pendingMb;
    NuclearRecovery.WaterPlan plan=s.plan(((NuclearController)tile).water());
    eq(plan.transferableMb,500,"callback estimate");
    ok(before.equals(r.writeToNBT(new NBTTagCompound())),"callback inspection read-only");
   }finally{inCallback=false;}
  }
 }
 static void reentrantInspection()throws Exception{
  Field f=Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);
  CallbackWorld w=(CallbackWorld)((Unsafe)f.get(null)).allocateInstance(CallbackWorld.class);
  w.states=new HashMap<>();w.tiles=new HashMap<>();w.missing=new HashSet<>();w.storage=new MapStorage(null);
  NuclearRecovery r=NuclearRecovery.get(w);r.enqueueWater(P,500);NuclearController c=NuclearWaterRecoveryTest.replace(w);
  w.inspect=true;int filled=NuclearWaterRecoveryTest.input(c).fill(new FluidStack(FluidRegistry.WATER,1000),true);
  eq(filled,1000,"fill remains accepted");eq(w.callbacks,1,"one actual dirty callback");eq(w.observed,500,"callback observes reserve");
  eq(c.water(),1000,"tank unchanged by diagnostic");eq(r.waterAt(P),500,"reserve unchanged by diagnostic");
  w.inspect=false;c.reclaimWater();eq(c.water(),1500,"real claim still works");eq(r.waterAt(P),0,"real claim debits once");
 }
 public static void main(String[]args)throws Exception{
  Bootstrap.register();TileEntity.register("nexustech:nuclear_controller",NuclearController.class);TileEntity.register("nexustech:nuclear_part",NuclearPart.class);
  CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY=NuclearReactorTest.capability("test:fluid");
  snapshotAndPlans();largeAndInvalidNbt();compareEstimateToClaim();reentrantInspection();
  System.out.println("NuclearWaterDiagnosticsTest: PASS");
 }
}
