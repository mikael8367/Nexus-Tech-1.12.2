package com.mikael8367.nexustech.techmachines;
import java.lang.reflect.Field;
import net.minecraft.init.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.WorldProviderSurface;
import sun.misc.Unsafe;

public class FocusedRegressionTests {
 static int n;
 static void ok(boolean b,String m){n++;if(!b)throw new AssertionError(m);}
 static void eq(int got,int want,String m){ok(got==want,m+" got="+got+" want="+want);}
 static class W extends RealBlockFakeWorldTests.FakeWorld {
  int lookupCount, trigger; Runnable atLookup; boolean dirtyBit;
  @Override public void markChunkDirty(net.minecraft.util.math.BlockPos p,net.minecraft.tileentity.TileEntity tile){dirtyBit=true;super.markChunkDirty(p,tile);}
  @Override public net.minecraft.tileentity.TileEntity getTileEntity(net.minecraft.util.math.BlockPos p){
   lookupCount++; if(lookupCount==trigger && atLookup!=null){Runnable r=atLookup;atLookup=null;r.run();}return super.getTileEntity(p);
  }
 }
 static W world() throws Exception {
  Field f=Unsafe.class.getDeclaredField("theUnsafe"); f.setAccessible(true); Unsafe u=(Unsafe)f.get(null);
  W w=(W)u.allocateInstance(W.class); w.loaded=true;w.drops=new java.util.ArrayList<>();
  u.putObject(w,u.objectFieldOffset(World.class.getField("provider")),new WorldProviderSurface()); return w;
 }
 static TechMachineTile.Sawmill place(W w){return RealBlockFakeWorldTests.place(w,new TechMachineTile.Sawmill());}
 static net.minecraftforge.items.IItemHandler h(TechMachineTile t){return RealBlockFakeWorldTests.h(t);}
 static int count(ItemStack s){return s.getCount();}
 static int progress(TechMachineTile t){return RealBlockFakeWorldTests.progress(t);}
 static void insertThrow(int oldCount,int offerCount) throws Exception {
  W w=world(); TechMachineTile t=place(w);if(oldCount>0)h(t).insertItem(0,new ItemStack(Blocks.LOG,oldCount),false);
  ItemStack offer=new ItemStack(Blocks.LOG,offerCount); final ItemStack[] drained={ItemStack.EMPTY};
  w.dirty=()->{drained[0]=t.drain(0);throw new IllegalStateException("after external drain");};
  boolean thrown=false;try { h(t).insertItem(0,offer,false); } catch(IllegalStateException expected){thrown=true;}
  ok(thrown,"exception propagated");eq(count(offer),offerCount,"caller retains offer");eq(count(drained[0]),oldCount,"only old destination drained");eq(count(h(t).getStackInSlot(0)),0,"destination empty after drain");
  ok(h(t).insertItem(0,new ItemStack(Blocks.LOG),false).isEmpty(),"busy released after exception");
 }
 static void normalInsert(int oldCount,int offerCount,int accepted) throws Exception {
  W w=world();TechMachineTile t=place(w);if(oldCount>0)h(t).insertItem(0,new ItemStack(Blocks.LOG,oldCount),false);
  ItemStack offered=new ItemStack(Blocks.LOG,offerCount);ItemStack simulated=h(t).insertItem(0,offered,true);
  eq(count(simulated),offerCount-accepted,"simulation remainder");eq(count(h(t).getStackInSlot(0)),oldCount,"simulation no mutation");
  ItemStack returned=h(t).insertItem(0,offered,false);eq(count(returned),offerCount-accepted,"actual remainder");eq(count(h(t).getStackInSlot(0)),oldCount+accepted,"destination");eq(count(offered),offerCount,"forge input not mutated");
  NBTTagCompound tag=t.writeToNBT(new NBTTagCompound());TechMachineTile.Sawmill restored=place(w);restored.readFromNBT(tag);eq(count(h(restored).getStackInSlot(0)),oldCount+accepted,"save roundtrip destination");
 }
 static void tickDrain(int trigger,int logs,boolean throwing) throws Exception {
  W w=world();TechMachineTile t=place(w);h(t).insertItem(0,new ItemStack(Blocks.LOG,logs),false);RealBlockFakeWorldTests.charge(t);t.update();
  int energy=RealBlockFakeWorldTests.fe(t).getEnergyStored();eq(progress(t),1,"pre progress");w.lookupCount=0;w.trigger=trigger;
  final ItemStack[] drained={ItemStack.EMPTY};w.atLookup=()->{drained[0]=t.drain(0); if(throwing)throw new IllegalStateException("lookup");};
  boolean threw=false;try{t.update();}catch(IllegalStateException ex){threw=true;}
  ok(threw==throwing,"lookup exception outcome");eq(count(drained[0]),logs,"drained original input");eq(count(h(t).getStackInSlot(0)),0,"input empty");eq(count(h(t).getStackInSlot(1)),0,"no output");ok(progress(t)==0 && RealBlockFakeWorldTests.fe(t).getEnergyStored()==energy,"drained tick progress="+progress(t)+" expected=0 energy="+RealBlockFakeWorldTests.fe(t).getEnergyStored()+" expected="+energy);
 }
 static void interruptions() throws Exception {
  for(int mode=0;mode<4;mode++){
   final int m=mode;W w=world();TechMachineTile t=place(w);ItemStack offer=new ItemStack(Blocks.LOG,3);final int[] recursive={-1};
   w.dirty=()->{ recursive[0]=count(h(t).insertItem(0,new ItemStack(Blocks.LOG,2),false)); if(m==0)w.loaded=false; if(m==1)w.tile=new TechMachineTile.Sawmill(); if(m==2)t.drain(0); if(m==3)throw new IllegalStateException(); };
   ItemStack ret=null;boolean threw=false;try{ret=h(t).insertItem(0,offer,false);}catch(IllegalStateException e){threw=true;}
   eq(recursive[0],2,"recursive insertion rejected");eq(count(offer),3,"source unchanged");
   if(m<2){eq(count(ret),3,"unload/replacement returns full remainder");eq(count(h(t).getStackInSlot(0)),0,"unload/replacement does not commit");}
   if(m==2){eq(count(ret),0,"empty drain no conflict");eq(count(h(t).getStackInSlot(0)),3,"empty drain commit");}
   if(m==3){ok(threw,"throw propagated");eq(count(h(t).getStackInSlot(0)),0,"throw no insertion");}
  }
  W w=world();TechMachineTile t=place(w);h(t).insertItem(0,new ItemStack(Blocks.LOG,4),false);final ItemStack[] d={ItemStack.EMPTY};
  w.dirty=()->{d[0]=t.drain(0);};ItemStack r=h(t).insertItem(0,new ItemStack(Blocks.LOG,3),false);eq(count(d[0]),4,"drain old input");eq(count(r),3,"changed destination returns full offer");eq(count(h(t).getStackInSlot(0)),0,"no overwrite after drain");
 }
 static void blockException()throws Exception {
  W w=world();TechMachineTile t=place(w);TechMachineBlock b=new TechMachineBlock("test_machine2",0);ItemStack offer=new ItemStack(Blocks.LOG,2);
  w.dirty=()->{b.breakBlock(w,RealBlockFakeWorldTests.P,b.getDefaultState());throw new IllegalStateException();};
  try{h(t).insertItem(0,offer,false);throw new AssertionError("expected exception");}catch(IllegalStateException expected){}
  eq(count(offer),2,"source survives block callback throw");eq(RealBlockFakeWorldTests.total(w,net.minecraft.item.Item.getItemFromBlock(Blocks.LOG)),0,"no new items spawned on callback throw");ok(w.tile==null,"block removed");
 }
 static void saveDuringDirty() throws Exception {
  W w=world();TechMachineTile t=place(w);final NBTTagCompound[] snap={null};w.dirty=()->{snap[0]=t.writeToNBT(new NBTTagCompound());w.dirtyBit=false;};
  ItemStack ret=h(t).insertItem(0,new ItemStack(Blocks.LOG,3),false);eq(count(ret),0,"save callback returned accepted");eq(count(h(t).getStackInSlot(0)),3,"after save callback accepted");ok(!w.dirtyBit,"modeled synchronous save clears dirty before commit and no later notification");
  TechMachineTile.Sawmill restored=place(w);restored.readFromNBT(snap[0]);eq(count(h(restored).getStackInSlot(0)),0,"save in pre-notify sees old state (known persistence risk)");
 }
 public static void main(String[] args)throws Exception {
  Bootstrap.register();RealBlockFakeWorldTests.caps();net.minecraft.tileentity.TileEntity.register("test:focused",TechMachineTile.Sawmill.class);
  String which=args.length==0?"normal":args[0];
  if(which.equals("tick")){tickDrain(2,1,false);tickDrain(2,3,false);tickDrain(2,2,true);tickDrain(3,1,false);}
  else if(which.equals("tick-one"))tickDrain(2,1,false);
  else if(which.equals("tick-three"))tickDrain(2,3,false);
  else if(which.equals("tick-throw"))tickDrain(2,2,true);
  else if(which.equals("insert")){insertThrow(0,1);insertThrow(0,3);insertThrow(2,3);insertThrow(63,3);blockException();}
  else if(which.equals("insert-one"))insertThrow(0,1);
  else if(which.equals("insert-three"))insertThrow(0,3);
  else if(which.equals("insert-existing"))insertThrow(2,3);
  else if(which.equals("insert-partial"))insertThrow(63,3);
  else if(which.equals("block"))blockException();
  else {normalInsert(0,1,1);normalInsert(0,64,64);normalInsert(63,3,1);interruptions();saveDuringDirty();}
  Bootstrap.printToSYSOUT("PASS FocusedRegressionTests "+(args.length>0?args[0]:"normal")+" assertions="+n);
 }
}
