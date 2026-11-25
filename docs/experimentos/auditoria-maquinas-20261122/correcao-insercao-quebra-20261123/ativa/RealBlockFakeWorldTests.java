package com.mikael8367.nexustech.techmachines;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.init.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.*;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.energy.*;
import net.minecraftforge.items.*;
import sun.misc.Unsafe;
import java.util.concurrent.Callable;
public class RealBlockFakeWorldTests {
 static final BlockPos P=new BlockPos(0,64,0); static int n;
 static void ok(boolean x,String s){n++;if(!x)throw new AssertionError(s);}
 static void eq(int a,int b,String s){ok(a==b,s+" got="+a+" want="+b);}
 static class FakeWorld extends World {
  TileEntity tile; boolean loaded; Runnable dirty,lookup,spawn; List<ItemStack> drops; boolean water;
  FakeWorld(){super(null,null,null,null,false);}
  protected IChunkProvider createChunkProvider(){return null;}
  protected boolean isChunkLoaded(int x,int z,boolean allow){return loaded;}
  public boolean isBlockLoaded(BlockPos p){return loaded;}
  public TileEntity getTileEntity(BlockPos p){if(lookup!=null){Runnable r=lookup;lookup=null;r.run();}return P.equals(p)?tile:null;}
  public void removeTileEntity(BlockPos p){if(P.equals(p)){if(tile!=null)tile.invalidate();tile=null;}}
  public IBlockState getBlockState(BlockPos p){return !P.equals(p)&&water?Blocks.WATER.getDefaultState():Blocks.AIR.getDefaultState();}
  public void markChunkDirty(BlockPos p,TileEntity t){if(dirty!=null){Runnable r=dirty;dirty=null;r.run();}}
  public boolean spawnEntity(Entity e){if(spawn!=null){Runnable r=spawn;spawn=null;r.run();}drops.add(((EntityItem)e).getItem().copy());return true;}
 }
 static FakeWorld world()throws Exception{Field f=Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);Unsafe u=(Unsafe)f.get(null);FakeWorld w=(FakeWorld)u.allocateInstance(FakeWorld.class);w.loaded=true;w.drops=new ArrayList<>();u.putObject(w,u.objectFieldOffset(World.class.getField("provider")),new WorldProviderSurface());return w;}
 @SuppressWarnings("unchecked") static void caps()throws Exception{Constructor<?> c=Capability.class.getDeclaredConstructors()[0];c.setAccessible(true);CapabilityEnergy.ENERGY=(Capability<IEnergyStorage>)c.newInstance("test:fe",null,(Callable<IEnergyStorage>)()->null);CapabilityItemHandler.ITEM_HANDLER_CAPABILITY=(Capability<IItemHandler>)c.newInstance("test:item",null,(Callable<IItemHandler>)()->null);}
 static <T extends TechMachineTile>T place(FakeWorld w,T t){w.tile=t;t.setWorld(w);t.setPos(P);return t;}
 static IItemHandler h(TechMachineTile t){return t.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY,null);}
 static IEnergyStorage fe(TechMachineTile t){return t.getCapability(CapabilityEnergy.ENERGY,null);}
 static void charge(TechMachineTile t){fe(t).receiveEnergy(1000,false);fe(t).receiveEnergy(600,false);}
 static void ticks(TechMachineTile t,int x){while(x-->0)t.update();}
 static int progress(TechMachineTile t){return t.writeToNBT(new NBTTagCompound()).getInteger("Progress");}
 static int total(FakeWorld w,net.minecraft.item.Item item){int n=0;for(ItemStack s:w.drops)if(s.getItem()==item)n+=s.getCount();return n;}
 public static void main(String[] args)throws Exception{
  Bootstrap.register();caps();TileEntity.register("test:sawmill",TechMachineTile.Sawmill.class);TileEntity.register("test:compost",TechMachineTile.Composter.class);TileEntity.register("test:bottler",TechMachineTile.WaterBottler.class);
  TechMachineBlock block=new TechMachineBlock("test_machine",0); IBlockState state=block.getDefaultState();
  FakeWorld w=world();TechMachineTile t=place(w,new TechMachineTile.Sawmill());h(t).insertItem(0,new ItemStack(Blocks.LOG,2),false);charge(t);ticks(t,79);
  w.dirty=()->{block.breakBlock(w,P,state);block.breakBlock(w,P,state);};t.update();eq(total(w,net.minecraft.item.Item.getItemFromBlock(Blocks.LOG)),1,"break leftover");eq(total(w,net.minecraft.item.Item.getItemFromBlock(Blocks.PLANKS)),6,"break output");eq(w.drops.size(),2,"break once");ok(w.tile==null,"block removed");ok(h(t).extractItem(0,1,false).isEmpty(),"stale capability");
  FakeWorld x=world();TechMachineTile q=place(x,new TechMachineTile.Sawmill());h(q).insertItem(0,new ItemStack(Blocks.LOG,2),false);charge(q);ticks(q,80);x.dirty=()->block.breakBlock(x,P,state);block.breakBlock(x,P,state);eq(total(x,net.minecraft.item.Item.getItemFromBlock(Blocks.LOG)),1,"nested break input once");eq(total(x,net.minecraft.item.Item.getItemFromBlock(Blocks.PLANKS)),6,"nested break output once");
  FakeWorld y=world();TechMachineTile r=place(y,new TechMachineTile.Sawmill());h(r).insertItem(0,new ItemStack(Blocks.LOG),false);charge(r);ticks(r,79);y.lookup=()->{r.drain(0);};r.update();eq(h(r).getStackInSlot(1).getCount(),0,"lookup drain prevents output");eq(progress(r),0,"lookup drain resets progress");
  FakeWorld c=world();TechMachineTile comp=place(c,new TechMachineTile.Composter());h(comp).insertItem(0,new ItemStack(Items.WHEAT_SEEDS,9),false);charge(comp);ticks(comp,80);eq(h(comp).getStackInSlot(0).getCount(),1,"compost consumes 8");eq(h(comp).getStackInSlot(1).getCount(),1,"compost output");eq(fe(comp).getEnergyStored(),0,"compost energy");
  FakeWorld z=world();z.water=true;TechMachineTile bottler=place(z,new TechMachineTile.WaterBottler());h(bottler).insertItem(0,new ItemStack(Items.GLASS_BOTTLE,2),false);charge(bottler);ticks(bottler,80);eq(h(bottler).getStackInSlot(0).getCount(),1,"water consumes 1");eq(h(bottler).getStackInSlot(1).getCount(),1,"water potion");ok(h(bottler).getStackInSlot(1).getItem()==Items.POTIONITEM,"water type");
  FakeWorld e=world();TechMachineTile ins=place(e,new TechMachineTile.Sawmill());ItemStack offered=new ItemStack(Blocks.LOG);final ItemStack[] drained={ItemStack.EMPTY};e.dirty=()->{drained[0]=ins.drain(0);throw new IllegalStateException("hostile dirty callback");};try{h(ins).insertItem(0,offered,false);throw new AssertionError("expected throw");}catch(IllegalStateException expected){}eq(offered.getCount(),1,"offered retained on exception");eq(drained[0].getCount(),1,"committed item also drained on exception");
  FakeWorld j=world();TechMachineTile stacked=place(j,new TechMachineTile.Sawmill());h(stacked).insertItem(0,new ItemStack(Blocks.LOG,2),false);charge(stacked);ticks(stacked,80);charge(stacked);ticks(stacked,79);j.dirty=()->block.breakBlock(j,P,state);stacked.update();eq(total(j,net.minecraft.item.Item.getItemFromBlock(Blocks.PLANKS)),12,"existing output plus new result drops once");eq(total(j,net.minecraft.item.Item.getItemFromBlock(Blocks.LOG)),0,"both ingredients consumed");
  FakeWorld k=world();TechMachineTile fail=place(k,new TechMachineTile.Sawmill());h(fail).insertItem(0,new ItemStack(Blocks.LOG,2),false);charge(fail);ticks(fail,80);k.spawn=()->{throw new IllegalStateException("spawn fails");};try{block.breakBlock(k,P,state);throw new AssertionError("expected spawn failure");}catch(IllegalStateException expected){}ok(h(fail).getStackInSlot(0).isEmpty(),"spawn failure already removed input");eq(k.drops.size(),0,"failed spawn lost input");block.breakBlock(k,P,state);eq(total(k,net.minecraft.item.Item.getItemFromBlock(Blocks.PLANKS)),6,"output recovered on later break");
  FakeWorld m=world();TechMachineTile forged=place(m,new TechMachineTile.Sawmill());h(forged).insertItem(0,new ItemStack(Blocks.LOG),false);charge(forged);NBTTagCompound modified=forged.writeToNBT(new NBTTagCompound());modified.setInteger("Progress",79);forged.readFromNBT(modified);forged.update();eq(h(forged).getStackInSlot(1).getCount(),6,"forged NBT progress completes without 79 paid ticks");eq(fe(forged).getEnergyStored(),1580,"forged NBT underpays energy");
  Bootstrap.printToSYSOUT("PASS RealBlockFakeWorldTests assertions="+n+"; KNOWN RISKS: insertion callback exception, spawn failure, forged NBT progress");
 }
}
