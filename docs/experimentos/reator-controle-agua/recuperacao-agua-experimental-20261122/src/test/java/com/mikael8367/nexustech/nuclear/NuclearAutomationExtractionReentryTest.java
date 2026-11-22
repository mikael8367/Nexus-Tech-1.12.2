package com.mikael8367.nexustech.nuclear;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.storage.MapStorage;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import sun.misc.Unsafe;

/** Real controller and capability, artificial copy/notification callbacks; not a live game. */
public final class NuclearAutomationExtractionReentryTest {
 static class W extends NuclearReactorTest.TestWorld {
  MapStorage storage; boolean breakDirty, throwDirty;
  @Override public MapStorage getPerWorldStorage(){return storage;}
  @Override public void removeTileEntity(BlockPos p){TileEntity t=tiles.remove(p);if(t!=null)t.invalidate();}
  @Override public void markChunkDirty(BlockPos p,TileEntity t){
   if(breakDirty){breakDirty=false;NuclearRegistry.CONTROLLER.breakBlock(this,p,getBlockState(p));}
   if(throwDirty){throwDirty=false;throw new IllegalStateException("injected notification failure");}
  }
 }
 static W world()throws Exception{
  Field f=Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);
  W w=(W)((Unsafe)f.get(null)).allocateInstance(W.class);
  w.states=new HashMap<>();w.tiles=new HashMap<>();w.missing=new HashSet<>();w.storage=new MapStorage(null);return w;
 }
 static Runnable callback; static int copies, trigger; static boolean fail;
 static final Item SPECIAL=new Item(){
  @Override public ICapabilityProvider initCapabilities(ItemStack stack,NBTTagCompound n){
   return new ICapabilitySerializable<NBTTagCompound>(){
    public boolean hasCapability(Capability<?> c,EnumFacing f){return false;}
    public <T>T getCapability(Capability<T> c,EnumFacing f){return null;}
    public NBTTagCompound serializeNBT(){
     if(trigger>0 && ++copies==trigger){trigger=0;if(callback!=null)callback.run();if(fail)throw new IllegalStateException("injected copy failure");}
     return new NBTTagCompound();
    }
    public void deserializeNBT(NBTTagCompound n){}
   };
  }
 };
 static void eq(int a,int b,String s){NuclearReactorTest.eq(a,b,s);}
 static void ok(boolean a,String s){NuclearReactorTest.ok(a,s);}
 static int recovered(NuclearRecovery r)throws Exception{
  Field f=NuclearRecovery.class.getDeclaredField("entries");f.setAccessible(true);int total=0;
  for(Object e:(List<?>)f.get(r)){Field stack=e.getClass().getDeclaredField("stack");stack.setAccessible(true);total+=((ItemStack)stack.get(e)).getCount();}
  return total;
 }
 static void copyBreak(int copy,boolean simulate)throws Exception{
  W w=world();NuclearController c=NuclearReactorTest.build(w,EnumFacing.NORTH);
  c.inventory.setStackInSlot(2,new ItemStack(SPECIAL,5));
  IItemHandler h=c.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY,null);
  copies=0;trigger=copy;fail=false;callback=()->{
   ok(h.extractItem(2,1,false).isEmpty(),"nested extraction rejected");
   ok(!h.insertItem(1,new ItemStack(Items.WATER_BUCKET),false).isEmpty(),"nested insertion rejected");
   ok(!c.beginOperation(),"nested GUI rejected");c.update();
   NuclearRegistry.CONTROLLER.breakBlock(w,NuclearReactorTest.P,w.getBlockState(NuclearReactorTest.P));
  };
  ItemStack result=h.extractItem(2,2,simulate);callback=null;
  eq(copies,copy,"copy callback triggered");ok(result.isEmpty(),"removal before commit returns empty");
  ok(w.tiles.get(NuclearReactorTest.P)==null && c.isInvalid(),"real block break removed controller");
  eq(recovered(NuclearRecovery.get(w)),5,"entire stack recovered without duplication");
  ok(c.inventory.getStackInSlot(2).isEmpty(),"removed inventory drained");
  ok(h.extractItem(2,1,false).isEmpty(),"stale handler rejected");
  c.onControllerRemoved();eq(recovered(NuclearRecovery.get(w)),5,"repeat removal no duplicate");
  System.out.println("copy break: copy="+copy+" simulate="+simulate+" extracted=0 recovered=5");
 }
 static void normalAndNotification(boolean breakOnDirty,boolean throwOnDirty,int amount)throws Exception{
  W w=world();NuclearController c=NuclearReactorTest.build(w,EnumFacing.NORTH);
  c.inventory.setStackInSlot(2,new ItemStack(SPECIAL,5));
  IItemHandler h=c.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY,null);
  trigger=0;ItemStack sim=h.extractItem(2,amount,true);eq(sim.getCount(),Math.min(5,amount),"simulate result");
  eq(c.inventory.getStackInSlot(2).getCount(),5,"simulate leaves source unchanged");
  w.breakDirty=breakOnDirty;w.throwDirty=throwOnDirty;
  ItemStack result=h.extractItem(2,amount,false);int returned=result.isEmpty()?0:result.getCount();
  int live=w.tiles.get(NuclearReactorTest.P)==c?c.inventory.getStackInSlot(2).getCount():0;
  int queued=recovered(NuclearRecovery.get(w));
  eq(returned,Math.min(5,amount),"actual result");eq(returned+live+queued,5,"item balance");
  System.out.println("notification: break="+breakOnDirty+" throws="+throwOnDirty+" returned="+returned+" live="+live+" recovered="+queued);
 }
 static void copyFailure()throws Exception{
  W w=world();NuclearController c=NuclearReactorTest.build(w,EnumFacing.NORTH);
  c.inventory.setStackInSlot(2,new ItemStack(SPECIAL,5));IItemHandler h=c.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY,null);
  copies=0;trigger=1;fail=true;callback=null;
  try{h.extractItem(2,2,false);throw new AssertionError("expected copy exception");}catch(IllegalStateException expected){}finally{fail=false;trigger=0;}
  eq(c.inventory.getStackInSlot(2).getCount(),5,"copy failure before commit retains items");
  eq(h.extractItem(2,2,false).getCount(),2,"lock released after exception");
 }
 static void guardControls()throws Exception{
  W w=world();NuclearController c=NuclearReactorTest.build(w,EnumFacing.NORTH);
  c.inventory.setStackInSlot(2,new ItemStack(SPECIAL,5));IItemHandler h=c.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY,null);
  ok(h.extractItem(0,1,false).isEmpty(),"input extraction rejected");
  ok(h.extractItem(2,0,false).isEmpty(),"zero extraction rejected");
  ok(h.extractItem(2,-1,false).isEmpty(),"negative extraction rejected");
  ok(c.beginOperation(),"GUI operation started");
  try{ok(h.extractItem(2,1,false).isEmpty(),"pull rejected inside GUI operation");}
  finally{c.endOperation();}
  eq(c.inventory.getStackInSlot(2).getCount(),5,"guard controls preserve output");
 }
 static void realBreakCopyFailure()throws Exception{
  W w=world();NuclearController c=NuclearReactorTest.build(w,EnumFacing.NORTH);
  c.inventory.setStackInSlot(0,new ItemStack(Items.DIAMOND,3));c.inventory.setStackInSlot(2,new ItemStack(SPECIAL,5));
  copies=0;trigger=1;fail=true;callback=null;
  try{NuclearRegistry.CONTROLLER.breakBlock(w,NuclearReactorTest.P,w.getBlockState(NuclearReactorTest.P));throw new AssertionError("expected real break copy failure");}
  catch(IllegalStateException expected){}finally{fail=false;trigger=0;}
  eq(recovered(NuclearRecovery.get(w)),3,"earlier slot queued");
  eq(c.inventory.getStackInSlot(2).getCount(),5,"failing slot remains on mock tile");
  ok(w.tiles.get(NuclearReactorTest.P)==c && !c.isInvalid(),"exception interrupted super.breakBlock in mock");
  c.onControllerRemoved();eq(recovered(NuclearRecovery.get(w)),3,"no automatic retry of uncertain break");
 }
 public static void main(String[] args)throws Exception{
  Bootstrap.register();CapabilityItemHandler.ITEM_HANDLER_CAPABILITY=NuclearReactorTest.capability("test:item");
  copyBreak(1,false);copyBreak(2,false);copyBreak(1,true);
  normalAndNotification(false,false,2);normalAndNotification(false,false,10);
  normalAndNotification(true,false,2);normalAndNotification(true,true,2);normalAndNotification(false,true,2);copyFailure();guardControls();realBreakCopyFailure();
  System.out.println("NuclearAutomationExtractionReentryTest: PASS");
 }
}
