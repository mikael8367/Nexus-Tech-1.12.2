package com.mikael8367.nexustech.experiment.tenmachines;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.Callable;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldProviderSurface;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import sun.misc.Unsafe;

public final class TenMachinesTest {
 static int assertions;
 static final BlockPos POS=new BlockPos(0,64,0);
 static void ok(boolean x,String msg){assertions++;if(!x)throw new AssertionError(msg);}
 static void eq(int x,int y,String msg){ok(x==y,msg+" got="+x+" expected="+y);}
 static final class FakeWorld extends World {
  TileEntity tile; boolean loaded; Runnable onDirty; List<ItemStack> drops;
  private FakeWorld(){super(null,null,null,null,false);}
  protected IChunkProvider createChunkProvider(){return null;}
  protected boolean isChunkLoaded(int x,int z,boolean allow){return loaded;}
  @Override public boolean isBlockLoaded(BlockPos p){return loaded;}
  @Override public TileEntity getTileEntity(BlockPos p){return tile;}
  @Override public IBlockState getBlockState(BlockPos p){return Blocks.AIR.getDefaultState();}
  @Override public void markChunkDirty(BlockPos p,TileEntity t){if(onDirty!=null){Runnable r=onDirty;onDirty=null;r.run();}}
  @Override public boolean spawnEntity(Entity e){drops.add(((EntityItem)e).getItem().copy());return true;}
  void put(ProcessingTile t){tile=t;t.setWorld(this);t.setPos(POS);}
 }
 static FakeWorld world(boolean remote)throws Exception{
  Field f=Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);Unsafe u=(Unsafe)f.get(null);
  FakeWorld w=(FakeWorld)u.allocateInstance(FakeWorld.class);w.loaded=true;w.drops=new ArrayList<>();
  u.putBoolean(w,u.objectFieldOffset(World.class.getField("isRemote")),remote);
  u.putObject(w,u.objectFieldOffset(World.class.getField("provider")),new WorldProviderSurface());return w;
 }
 @SuppressWarnings("unchecked") static void caps()throws Exception{
  Constructor<?> c=Capability.class.getDeclaredConstructors()[0];c.setAccessible(true);
  CapabilityEnergy.ENERGY=(Capability<IEnergyStorage>)c.newInstance("test:energy",null,(Callable<IEnergyStorage>)()->null);
  CapabilityItemHandler.ITEM_HANDLER_CAPABILITY=(Capability<IItemHandler>)c.newInstance("test:items",null,(Callable<IItemHandler>)()->null);
 }
 // Baseline exercises the internal recovery view, including input extraction.
 static IItemHandler inv(ProcessingTile t){return t.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY,null);}
 static IEnergyStorage energy(ProcessingTile t){return t.getCapability(CapabilityEnergy.ENERGY,EnumFacing.DOWN);}
 static void charge(ProcessingTile t,int n){while(n>0){int got=energy(t).receiveEnergy(n,false);ok(got>0,"charge");n-=got;}}
 static void tick(ProcessingTile t,int n){while(n-->0)t.update();}
 static void same(ItemStack a,ItemStack b,String msg){ok(ItemStack.areItemsEqual(a,b) && a.getCount()==b.getCount(),msg+" got="+a+" expected="+b);}
 static ProcessingTile machine(MachineKind k,FakeWorld w){ProcessingTile t=(ProcessingTile)ExperimentalRegistry.createTile(k);w.put(t);return t;}
 static void runRecipe(MachineKind kind,ItemStack a,ItemStack b,ItemStack result)throws Exception{
  FakeWorld w=world(false);ProcessingTile t=machine(kind,w);IItemHandler h=inv(t);
  ok(h.insertItem(0,a,false).isEmpty(),kind+" input0");if(!b.isEmpty())ok(h.insertItem(1,b,false).isEmpty(),kind+" input1");
  eq(energy(t).receiveEnergy(2000,true),1000,"rate simulate");eq(energy(t).getEnergyStored(),0,"simulate FE");
  charge(t,2000);tick(t,99);eq(t.progress(),99,kind+" progress");eq(energy(t).getEnergyStored(),20,"FE at 99");ok(h.getStackInSlot(2).isEmpty(),"no early output");
  tick(t,1);eq(t.progress(),0,"complete progress");eq(energy(t).getEnergyStored(),0,"2000 FE/cycle");same(h.getStackInSlot(2),result,kind+" result");
  ok(h.getStackInSlot(0).isEmpty() && h.getStackInSlot(1).isEmpty(),"ingredients consumed");
  eq(h.extractItem(2,64,true).getCount(),result.getCount(),"simulate extraction");same(h.getStackInSlot(2),result,"simulate unchanged");
  h.getStackInSlot(2).setCount(0);same(h.getStackInSlot(2),result,"defensive copy");
  eq(h.extractItem(2,64,false).getCount(),result.getCount(),"real extraction");
 }
 public static void main(String[] args)throws Exception{
  Bootstrap.register();caps();ExperimentalRegistry.registerTiles();
  eq(ExperimentalRegistry.blocks().size(),10,"10 blocks");Set<Class<?>> classes=new HashSet<>();for(MachineKind k:MachineKind.values()){ok(ExperimentalRegistry.blocks().get(k).getRegistryName().toString().equals("nexustech:"+k.id),"registry id");ok(classes.add(ExperimentalRegistry.createTile(k).getClass()),"unique tile class");}
  runRecipe(MachineKind.STONE_CUTTER,new ItemStack(Blocks.STONE,4,0),ItemStack.EMPTY,new ItemStack(Blocks.STONEBRICK,4,0));
  runRecipe(MachineKind.MOSS_ASSEMBLER,new ItemStack(Blocks.STONEBRICK,1,0),new ItemStack(Blocks.VINE),new ItemStack(Blocks.STONEBRICK,1,1));
  runRecipe(MachineKind.GLASS_SHAPER,new ItemStack(Blocks.GLASS,3),ItemStack.EMPTY,new ItemStack(Blocks.GLASS_PANE,8));
  for(int i=0;i<16;i++)runRecipe(MachineKind.FIBER_SPINNER,new ItemStack(Blocks.WOOL,1,i),ItemStack.EMPTY,new ItemStack(Items.STRING,4));
  runRecipe(MachineKind.PAPER_PRESS,new ItemStack(Items.REEDS,3),ItemStack.EMPTY,new ItemStack(Items.PAPER,3));
  runRecipe(MachineKind.SUGAR_MILL,new ItemStack(Items.REEDS),ItemStack.EMPTY,new ItemStack(Items.SUGAR));
  runRecipe(MachineKind.BREAD_BAKER,new ItemStack(Items.WHEAT,3),ItemStack.EMPTY,new ItemStack(Items.BREAD));
  runRecipe(MachineKind.SEED_EXTRACTOR,new ItemStack(Blocks.PUMPKIN),ItemStack.EMPTY,new ItemStack(Items.PUMPKIN_SEEDS,4));
  runRecipe(MachineKind.SEED_EXTRACTOR,new ItemStack(Items.MELON),ItemStack.EMPTY,new ItemStack(Items.MELON_SEEDS));
  runRecipe(MachineKind.DYE_MIXER,new ItemStack(Items.DYE,1,4),new ItemStack(Items.DYE,1,2),new ItemStack(Items.DYE,2,6));
  runRecipe(MachineKind.BONE_MILL,new ItemStack(Items.BONE),ItemStack.EMPTY,new ItemStack(Items.DYE,3,15));
  FakeWorld w=world(false);ProcessingTile t=machine(MachineKind.DYE_MIXER,w);IItemHandler h=inv(t);
  ok(!h.insertItem(0,new ItemStack(Items.DYE,1,2),false).isEmpty(),"wrong input metadata");ok(!h.insertItem(1,new ItemStack(Items.DYE,1,4),false).isEmpty(),"wrong second metadata");
  ItemStack tagged=new ItemStack(Items.DYE,1,4);tagged.setTagCompound(new NBTTagCompound());ok(!h.insertItem(0,tagged,false).isEmpty(),"reject tags");
  ok(!h.insertItem(2,new ItemStack(Items.DYE,1,4),false).isEmpty(),"output insertion rejected");
  h.insertItem(0,new ItemStack(Items.DYE,2,4),false);charge(t,2000);tick(t,100);eq(energy(t).getEnergyStored(),2000,"missing second input costs nothing");
  h.insertItem(1,new ItemStack(Items.DYE,1,2),false);tick(t,20);eq(t.progress(),20,"progress before save");
  NBTTagCompound save=t.writeToNBT(new NBTTagCompound());ProcessingTile restored=machine(MachineKind.DYE_MIXER,w);restored.readFromNBT(save);eq(restored.progress(),20,"progress restored");eq(energy(restored).getEnergyStored(),1600,"energy restored");tick(restored,80);same(inv(restored).getStackInSlot(2),new ItemStack(Items.DYE,2,6),"restored cycle");ok(h.extractItem(0,1,false).isEmpty(),"stale handle");
  // Output incompatibility and capacity reject production and energy debit.
  FakeWorld q=world(false);ProcessingTile full=machine(MachineKind.BONE_MILL,q);IItemHandler fi=inv(full);fi.insertItem(0,new ItemStack(Items.BONE,23),false);charge(full,20000);tick(full,2000);eq(fi.getStackInSlot(2).getCount(),30,"ten cycles consume buffer");charge(full,20000);tick(full,1000);eq(fi.getStackInSlot(2).getCount(),60,"20 cycles");charge(full,6000);tick(full,100);eq(fi.getStackInSlot(2).getCount(),63,"21 cycles");int e=energy(full).getEnergyStored();tick(full,200);eq(energy(full).getEnergyStored(),e,"full output no FE");eq(fi.getStackInSlot(0).getCount(),2,"full output retains inputs");
  // A crafted incompatible output blocks completion without consuming FE.
  FakeWorld z=world(false);ProcessingTile incompatible=machine(MachineKind.BONE_MILL,z);inv(incompatible).insertItem(0,new ItemStack(Items.BONE),false);charge(incompatible,2000);
  NBTTagCompound altered=incompatible.writeToNBT(new NBTTagCompound());net.minecraft.nbt.NBTTagCompound wrong=new NBTTagCompound();new ItemStack(Items.IRON_INGOT,3).writeToNBT(wrong);wrong.setByte("Slot",(byte)2);altered.getTagList("Inventory",10).appendTag(wrong);incompatible.readFromNBT(altered);tick(incompatible,100);eq(energy(incompatible).getEnergyStored(),2000,"incompatible output no energy");eq(inv(incompatible).getStackInSlot(0).getCount(),1,"incompatible output preserves ingredient");
  // Reentrant NBT read and energy insertion in markDirty are not allowed to alter an in-flight operation.
  FakeWorld re=world(false);ProcessingTile locked=machine(MachineKind.SUGAR_MILL,re);NBTTagCompound inject=new NBTTagCompound();inject.setInteger("Energy",20000);inject.setInteger("Progress",99);
  re.onDirty=()->{locked.readFromNBT(inject);eq(energy(locked).receiveEnergy(100,false),0,"busy refuses reentrant FE");ok(!inv(locked).insertItem(0,new ItemStack(Items.REEDS),false).isEmpty(),"busy refuses reentrant item");};
  inv(locked).insertItem(0,new ItemStack(Items.REEDS),false);eq(energy(locked).getEnergyStored(),0,"reentrant NBT ignored");eq(locked.progress(),0,"reentrant progress ignored");eq(inv(locked).getStackInSlot(0).getCount(),1,"reentrant item ignored");
  // Pause resumes with the already paid progress, then a changed input resets it.
  charge(locked,100);tick(locked,10);eq(locked.progress(),5,"energy shortage pauses");charge(locked,1000);tick(locked,1);eq(locked.progress(),6,"energy restore resumes");
  FakeWorld client=world(true);ProcessingTile ct=machine(MachineKind.SUGAR_MILL,client);ok(!inv(ct).insertItem(0,new ItemStack(Items.REEDS),false).isEmpty(),"client insertion blocked");eq(energy(ct).receiveEnergy(100,false),0,"client FE blocked");tick(ct,200);ok(inv(ct).getStackInSlot(2).isEmpty(),"client update blocked");
  FakeWorld feedWorld=world(false); ProcessingTile fed=machine(MachineKind.SUGAR_MILL,feedWorld); IItemHandler feed=inv(fed); feed.insertItem(0,new ItemStack(Items.REEDS),false); charge(fed,2000); tick(fed,50); feed.insertItem(0,new ItemStack(Items.REEDS),false); eq(fed.progress(),50,"same ingredient insertion preserves progress"); tick(fed,50); eq(feed.getStackInSlot(2).getCount(),1,"continuous feed completes cycle");
   FakeWorld u=world(false);ProcessingTile reset=machine(MachineKind.PAPER_PRESS,u);inv(reset).insertItem(0,new ItemStack(Items.REEDS,4),false);charge(reset,2000);tick(reset,10);inv(reset).extractItem(0,1,false);eq(reset.progress(),0,"input mutation resets progress");u.loaded=false;eq(energy(reset).receiveEnergy(10,false),0,"unloaded refused");
  // Reentrant block removal in dirty notification: entire committed output is dropped once.
  FakeWorld d=world(false);ProcessingTile breaking=machine(MachineKind.BONE_MILL,d);IItemHandler bi=inv(breaking);bi.insertItem(0,new ItemStack(Items.BONE),false);charge(breaking,2000);tick(breaking,99);
  d.onDirty=()->{ok(bi.getStackInSlot(0).isEmpty(),"callback sees ingredient consumed");same(bi.getStackInSlot(2),new ItemStack(Items.DYE,3,15),"callback sees complete result");ok(bi.extractItem(2,1,false).isEmpty(),"busy blocks callback extraction");breaking.requestDrops(d,POS);breaking.requestDrops(d,POS);};
  tick(breaking,1);eq(d.drops.size(),1,"single drop");same(d.drops.get(0),new ItemStack(Items.DYE,3,15),"drop only result");ok(bi.extractItem(2,1,false).isEmpty(),"broken stale handler");breaking.requestDrops(d,POS);eq(d.drops.size(),1,"repeat break no duplication");
  FakeWorld n=world(false);ProcessingTile clamp=machine(MachineKind.BONE_MILL,n);NBTTagCompound bad=new NBTTagCompound();bad.setInteger("Energy",Integer.MAX_VALUE);bad.setInteger("Progress",Integer.MAX_VALUE);clamp.readFromNBT(bad);eq(energy(clamp).getEnergyStored(),20000,"NBT energy clamp");eq(clamp.progress(),0,"unbound NBT progress reset");
  Bootstrap.printToSYSOUT("PASS ten machines assertions="+assertions);
 }
}
