package com.mikael8367.nexustech.nuclear;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.storage.MapStorage;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import sun.misc.Unsafe;

/** Isolated real Forge tile/tank and artificial synchronous callbacks. */
public final class NuclearWaterRecoveryTest {
 static final BlockPos P=NuclearReactorTest.P;
 static void eq(int a,int b,String s){NuclearReactorTest.eq(a,b,s);}
 static void ok(boolean b,String s){NuclearReactorTest.ok(b,s);}
 static class W extends NuclearReactorTest.TestWorld {
  MapStorage storage; boolean breakOnDirty, throwOnDirty, failAfterRemoval, removed; int nested;
  boolean throwDuringDrain, drainCallbackSeen, throwOnCommittedBucket, breakOnCommittedBucket, bucketCallbackSeen;
  IFluidHandler input;
  public MapStorage getPerWorldStorage(){return storage;}
  public net.minecraft.block.state.IBlockState getBlockState(BlockPos p){if(failAfterRemoval && removed){failAfterRemoval=false;throw new IllegalStateException("injected release failure");}return super.getBlockState(p);}
  public void removeTileEntity(BlockPos p){TileEntity t=tiles.remove(p);if(t!=null)t.invalidate();removed=true;}
  public void markChunkDirty(BlockPos p,TileEntity tile){
   if(tile instanceof NuclearController){
    NuclearController c=(NuclearController)tile;
    if(throwDuringDrain && c.inventory.getStackInSlot(0).isEmpty() && !c.inventory.getStackInSlot(1).isEmpty()){
     drainCallbackSeen=true;throw new IllegalStateException("injected drain notification failure");
    }
    if((throwOnCommittedBucket || breakOnCommittedBucket) && c.water()==1000 && c.inventory.getStackInSlot(1).isEmpty()){
     bucketCallbackSeen=true;
     if(breakOnCommittedBucket){breakOnCommittedBucket=false;NuclearRegistry.CONTROLLER.breakBlock(this,P,getBlockState(P));}
     if(throwOnCommittedBucket){throwOnCommittedBucket=false;throw new IllegalStateException("injected bucket notification failure");}
    }
   }
   if(breakOnDirty){breakOnDirty=false;nested=input.fill(new FluidStack(FluidRegistry.WATER,1),true);NuclearRegistry.CONTROLLER.breakBlock(this,p,getBlockState(p));}
   if(throwOnDirty){throwOnDirty=false;throw new IllegalStateException("injected notification failure");}
  }
 }
 static W world()throws Exception{
  Field f=Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);W w=(W)((Unsafe)f.get(null)).allocateInstance(W.class);
  w.states=new HashMap<>();w.tiles=new HashMap<>();w.missing=new HashSet<>();w.storage=new MapStorage(null);return w;
 }
 static IFluidHandler input(NuclearController c){return c.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY,null);}
 static NuclearController replace(W w){NuclearController c=new NuclearController();w.put(P,NuclearRegistry.CONTROLLER.getDefaultState(),c);return c;}
 static void breaks(int amount)throws Exception{
  W w=world();NuclearController c=replace(w);IFluidHandler in=input(c);
  if(amount>0)eq(in.fill(new FluidStack(FluidRegistry.WATER,amount),true),amount,"prefill");
  NuclearRegistry.CONTROLLER.breakBlock(w,P,w.getBlockState(P));NuclearRecovery r=NuclearRecovery.get(w);
  eq(c.water(),0,"detached tank empty");eq(r.waterAt(P),amount,"queued exactly");
  c.onControllerRemoved();NuclearRegistry.CONTROLLER.breakBlock(w,P,w.getBlockState(P));eq(r.waterAt(P),amount,"repeated break no replay");
  NuclearRecovery copy=new NuclearRecovery();copy.readFromNBT(r.writeToNBT(new NBTTagCompound()));eq(copy.waterAt(P),amount,"NBT round trip");
  w.storage.setData("nexustech_nuclear_recovery",copy);
  NuclearController fresh=replace(w);fresh.reclaimWater();eq(fresh.water(),amount,"restored exact");eq(copy.waterAt(P),0,"ledger debited");
  fresh.reclaimWater();eq(fresh.water(),amount,"repeat reclaim no duplication");
  NuclearController restored=new NuclearController();restored.readFromNBT(fresh.writeToNBT(new NBTTagCompound()));eq(restored.water(),amount,"tile NBT roundtrip");
 }
 static void reentry(int initial)throws Exception{
  W w=world();NuclearController c=replace(w);w.input=input(c);
  if(initial>0)eq(w.input.fill(new FluidStack(FluidRegistry.WATER,initial),true),initial,"prefill");
  w.breakOnDirty=true;int simulated=w.input.fill(new FluidStack(FluidRegistry.WATER,1000),false);
  eq(simulated,1000,"simulated");ok(w.breakOnDirty,"simulation no callback");
  int accepted=w.input.fill(new FluidStack(FluidRegistry.WATER,1000),true);
  eq(accepted,1000,"source debit");eq(w.nested,0,"nested fill rejected");eq(c.water(),0,"detached empty");
  eq(NuclearRecovery.get(w).waterAt(P),initial+1000,"source plus ledger conserved");eq(w.input.fill(new FluidStack(FluidRegistry.WATER,1),true),0,"stale input");
 }
 static void exceptionAfterMutation(boolean removing)throws Exception{
  W w=world();NuclearController c=replace(w);w.input=input(c);w.breakOnDirty=removing;w.throwOnDirty=true;
  int accepted=w.input.fill(new FluidStack(FluidRegistry.WATER,500),true);eq(accepted,500,"committed fill returned despite notification exception");
  eq((w.tiles.get(P)==c?c.water():0)+NuclearRecovery.get(w).waterAt(P),500,"exception balance");
 }
 static void finalizationException()throws Exception{
  W w=world();NuclearController c=replace(w);c.inventory.setStackInSlot(0,new ItemStack(Items.DIAMOND,3));
  w.input=input(c);w.breakOnDirty=true;w.failAfterRemoval=true;
  eq(w.input.fill(new FluidStack(FluidRegistry.WATER,500),true),500,"fill returns committed amount on release failure");
  NuclearRecovery r=NuclearRecovery.get(w);eq(r.waterAt(P),500,"water retained after release failure");eq(r.size(),1,"inventory enqueued before release failure");
 }
 static void bucket()throws Exception{
  NuclearRecoveryTest.W w=NuclearRecoveryTest.world();NuclearController c=w.tile;
  c.inventory.setStackInSlot(1,new ItemStack(Items.WATER_BUCKET));w.bucketBreak=true;c.update();
  NuclearRecovery r=NuclearRecovery.get(w);eq(r.waterAt(P),1000,"bucket water recorded");eq(r.size(),1,"empty bucket recovered");
  w.allow=true;r.retry(w);eq(w.spawned.size(),1,"one item dropped");ok(w.spawned.get(0).getItem()==Items.BUCKET,"empty bucket not duplicated");
 }
 static void drainWithoutInventoryCallbacks()throws Exception{
  W w=world();NuclearController c=replace(w);
  c.inventory.setStackInSlot(0,new ItemStack(Items.DIAMOND,3));c.inventory.setStackInSlot(1,new ItemStack(Items.WATER_BUCKET));c.inventory.setStackInSlot(2,new ItemStack(Items.BUCKET,2));
  w.throwDuringDrain=true;NuclearRegistry.CONTROLLER.breakBlock(w,P,w.getBlockState(P));
  ok(!w.drainCallbackSeen,"slot clearing does not call markDirty between slots");
  NuclearRecovery r=NuclearRecovery.get(w);eq(r.size(),3,"all three stacks enqueued");
  for(int i=0;i<3;i++)ok(c.inventory.getStackInSlot(i).isEmpty(),"slot drained "+i);
  c.onControllerRemoved();eq(r.size(),3,"repeated removal does not duplicate");
  NBTTagCompound n=r.writeToNBT(new NBTTagCompound());NuclearRecovery copy=new NuclearRecovery();copy.readFromNBT(n);eq(copy.size(),3,"all entries serialized");
 }
 static boolean failCopy;
 static void ambiguousCopyFailsClosed()throws Exception{
  W w=world();NuclearController c=replace(w);
  net.minecraft.item.Item special=new net.minecraft.item.Item(){
   @Override public net.minecraftforge.common.capabilities.ICapabilityProvider initCapabilities(ItemStack s,NBTTagCompound n){
    return new net.minecraftforge.common.capabilities.ICapabilitySerializable<NBTTagCompound>(){
     public boolean hasCapability(net.minecraftforge.common.capabilities.Capability<?> cap,EnumFacing face){return false;}
     public <T>T getCapability(net.minecraftforge.common.capabilities.Capability<T> cap,EnumFacing face){return null;}
     public NBTTagCompound serializeNBT(){if(failCopy)throw new IllegalStateException("injected item capability copy failure");return new NBTTagCompound();}
     public void deserializeNBT(NBTTagCompound tag){}
    };
   }
  };
  c.inventory.setStackInSlot(0,new ItemStack(Items.DIAMOND,3));c.inventory.setStackInSlot(1,new ItemStack(special));c.inventory.setStackInSlot(2,new ItemStack(Items.BUCKET));
  failCopy=true;
  try{c.onControllerRemoved();throw new AssertionError("expected copy failure");}catch(IllegalStateException expected){}finally{failCopy=false;}
  NuclearRecovery r=NuclearRecovery.get(w);eq(r.size(),1,"earlier slot queued once");
  ok(c.inventory.getStackInSlot(0).isEmpty(),"earlier slot cleared");ok(!c.inventory.getStackInSlot(1).isEmpty() && !c.inventory.getStackInSlot(2).isEmpty(),"later slots not silently discarded");
  java.lang.reflect.Method finish=NuclearController.class.getDeclaredMethod("finishRemoval",NuclearRecovery.class);finish.setAccessible(true);finish.invoke(c,r);
  eq(r.size(),1,"uncertain finalization not replayed");ok(!c.inventory.getStackInSlot(2).isEmpty(),"remaining slot stays for inspection");
 }
 static void bucketNotificationException(boolean breakFirst,boolean withOutput)throws Exception{
  W w=world();NuclearController c=replace(w);c.inventory.setStackInSlot(1,new ItemStack(Items.WATER_BUCKET));
  if(withOutput)c.inventory.setStackInSlot(2,new ItemStack(Items.BUCKET,2));
  w.throwOnCommittedBucket=true;w.breakOnCommittedBucket=breakFirst;c.update();
  ok(w.bucketCallbackSeen,"exception injected after complete conversion");
  NuclearRecovery r=NuclearRecovery.get(w);
  if(breakFirst){
   eq(r.waterAt(P),1000,"removed water recovered");eq(r.size(),1,"removed bucket stack recovered");
   NBTTagCompound stack=r.writeToNBT(new NBTTagCompound()).getTagList("Entries",10).getCompoundTagAt(0).getCompoundTag("Stack");
   ItemStack recovered=new ItemStack(stack);ok(recovered.getItem()==Items.BUCKET,"empty bucket recovered");eq(recovered.getCount(),withOutput?3:1,"all empty buckets recovered");
   c.onControllerRemoved();eq(r.size(),1,"repeated removal does not duplicate bucket");
  }else{
   eq(c.water(),1000,"water committed after notification exception");ok(c.inventory.getStackInSlot(1).isEmpty(),"filled bucket consumed");
   ok(c.inventory.getStackInSlot(2).getItem()==Items.BUCKET,"empty bucket created");eq(c.inventory.getStackInSlot(2).getCount(),withOutput?3:1,"bucket output count");
  }
 }
 static void clickRecovery()throws Exception{
  W w=world();NuclearRecovery r=NuclearRecovery.get(w);r.enqueueWater(P,500);NuclearController c=replace(w);
  NuclearGuiAccessTest.MockPlayer p=NuclearGuiAccessTest.player(w);NuclearGuiAccessTest.at(p,P,9,0,0);
  NuclearRegistry.CONTROLLER.onBlockActivated(w,P,w.getBlockState(P),p,net.minecraft.util.EnumHand.MAIN_HAND,EnumFacing.UP,0,0,0);
  eq(c.water(),0,"out of reach cannot reclaim");eq(r.waterAt(P),500,"unclaimed remains");
  NuclearGuiAccessTest.at(p,P,0,0,0);
  NuclearRegistry.CONTROLLER.onBlockActivated(w,P,w.getBlockState(P),p,net.minecraft.util.EnumHand.OFF_HAND,EnumFacing.UP,0,0,0);
  eq(c.water(),0,"off hand does not reclaim");
  NuclearRegistry.CONTROLLER.onBlockActivated(w,P,w.getBlockState(P),p,net.minecraft.util.EnumHand.MAIN_HAND,EnumFacing.UP,0,0,0);
  eq(c.water(),500,"in-range main-hand click reclaims unformed controller");eq(r.waterAt(P),0,"click debited ledger");
 }
 static void partialCapacity()throws Exception{
  W w=world();NuclearRecovery r=NuclearRecovery.get(w);r.enqueueWater(P,8000);r.enqueueWater(P,500);r.enqueueWater(P.east(),1);
  NuclearController c=replace(w);c.reclaimWater();eq(c.water(),8000,"capacity bounded");eq(r.waterAt(P),500,"remainder retained");eq(r.waterAt(P.east()),1,"position isolation");
  NuclearRegistry.CONTROLLER.breakBlock(w,P,w.getBlockState(P));eq(r.waterAt(P),8500,"repeat break accumulates");
  NuclearController d=replace(w);d.reclaimWater();eq(d.water(),8000,"second reclaim");eq(r.waterAt(P),500,"remaining held");
 }
 public static void main(String[]args)throws Exception{
  Bootstrap.register();TileEntity.register("nexustech:nuclear_controller",NuclearController.class);TileEntity.register("nexustech:nuclear_part",NuclearPart.class);CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY=NuclearReactorTest.capability("test:fluid");
  for(int n:new int[]{0,1,500,1000,8000})breaks(n);
  reentry(0);reentry(500);exceptionAfterMutation(false);exceptionAfterMutation(true);finalizationException();bucket();drainWithoutInventoryCallbacks();ambiguousCopyFailsClosed();
  for(boolean b:new boolean[]{false,true})for(boolean o:new boolean[]{false,true})bucketNotificationException(b,o);
  clickRecovery();partialCapacity();
  System.out.println("NuclearWaterRecoveryTest: PASS");
 }
}
