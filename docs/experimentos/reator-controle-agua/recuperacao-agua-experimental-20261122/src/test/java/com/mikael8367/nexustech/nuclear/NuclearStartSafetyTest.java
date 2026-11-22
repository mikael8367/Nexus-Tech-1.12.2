package com.mikael8367.nexustech.nuclear;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import sun.misc.Unsafe;

/** Server activation, interrupted transactions and GUI/NBT safety regressions. */
public final class NuclearStartSafetyTest {
 static void ok(boolean value,String message){NuclearReactorTest.ok(value,message);}
 static void eq(int actual,int expected,String message){NuclearReactorTest.eq(actual,expected,message);}
 static class CallbackWorld extends NuclearReactorTest.TestWorld {
  Runnable onNotify,onDirty;
  @Override public void notifyBlockUpdate(BlockPos p,IBlockState a,IBlockState b,int flags){super.notifyBlockUpdate(p,a,b,flags);Runnable r=onNotify;onNotify=null;if(r!=null)r.run();}
  @Override public void markChunkDirty(BlockPos p,TileEntity t){super.markChunkDirty(p,t);if(t instanceof NuclearController){Runnable r=onDirty;onDirty=null;if(r!=null)r.run();}}
 }
 static CallbackWorld world()throws Exception{
  Field f=Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);
  CallbackWorld w=(CallbackWorld)((Unsafe)f.get(null)).allocateInstance(CallbackWorld.class);
  w.states=new HashMap<>();w.tiles=new HashMap<>();w.missing=new HashSet<>();return w;
 }
 static void interruptedStarts()throws Exception{
  CallbackWorld w=world();NuclearController c=NuclearReactorTest.build(w,EnumFacing.NORTH);
  BlockPos q=NuclearController.at(NuclearReactorTest.P,EnumFacing.NORTH,1,4,2);
  c.inventory.setStackInSlot(0,new ItemStack(NuclearRegistry.FUEL));
  w.onNotify=()->w.states.put(q,Blocks.AIR.getDefaultState());c.setEnabled(true);
  ok(!c.enabled && !c.formed,"binding callback breaks structure: no start");
  eq(c.energy,0,"no energy");eq(c.inventory.getStackInSlot(0).getCount(),1,"fuel preserved");
  CallbackWorld w2=world();NuclearController c2=NuclearReactorTest.build(w2,EnumFacing.NORTH);
  c2.inventory.setStackInSlot(0,new ItemStack(NuclearRegistry.FUEL));
  w2.onNotify=()->c2.inventory.setStackInSlot(0,ItemStack.EMPTY);c2.setEnabled(true);
  ok(!c2.enabled,"binding callback removes fuel: no start");eq(c2.fuelTicks,0,"no burn");
 }
 static void interruptionDuringConsumption()throws Exception{
  CallbackWorld w=world();NuclearController c=NuclearReactorTest.build(w,EnumFacing.NORTH);
  BlockPos q=NuclearController.at(NuclearReactorTest.P,EnumFacing.NORTH,1,4,2);
  c.inventory.setStackInSlot(0,new ItemStack(NuclearRegistry.FUEL));c.setEnabled(true);c.update();
  c.setEnabled(false);c.fuelTicks=0;c.inventory.setStackInSlot(0,new ItemStack(NuclearRegistry.FUEL));c.setEnabled(true);
  int e=c.energy;w.onDirty=()->w.states.put(q,Blocks.AIR.getDefaultState());c.update();
  ok(!c.enabled && c.scrammed && !c.formed,"structure loss during fuel extraction trips immediately");
  eq(c.energy,e,"no generation in broken structure");eq(c.fuelTicks,NuclearController.FUEL_TICKS,"consumed fuel remains as unused burn");
  w.states.put(q,NuclearRegistry.CASING.getDefaultState());c.update();ok(!c.enabled,"repair never auto starts");
 }
 static void unloadDuringConsumption()throws Exception{
  CallbackWorld w=world();NuclearController c=NuclearReactorTest.build(w,EnumFacing.NORTH);
  BlockPos q=NuclearController.at(NuclearReactorTest.P,EnumFacing.NORTH,1,4,2);
  c.inventory.setStackInSlot(0,new ItemStack(NuclearRegistry.FUEL));c.setEnabled(true);c.temperature=200;
  w.onDirty=()->w.missing.add(q);c.update();
  ok(!c.enabled && c.scrammed && !c.formed,"chunk loss during extraction trips");
  eq(c.temperature,200,"chunk loss pauses heat");eq(c.energy,0,"chunk loss prevents generation");
  eq(c.fuelTicks,NuclearController.FUEL_TICKS,"newly consumed fuel preserved");
 }
 static void guiAndNbt()throws Exception{
  NuclearReactorTest.TestWorld w=NuclearReactorTest.world();NuclearController c=NuclearReactorTest.build(w,EnumFacing.NORTH);
  NuclearGuiAccessTest.MockPlayer p=NuclearGuiAccessTest.player(w);NuclearGuiAccessTest.at(p,NuclearReactorTest.P,0,0,0);
  NuclearContainer gui=new NuclearContainer(p.inventory,c);
  ok(gui.enchantItem(p,1),"button accepted");ok(!c.enabled,"empty button cannot start");
  c.inventory.setStackInSlot(0,new ItemStack(NuclearRegistry.FUEL));ok(gui.enchantItem(p,1)&&c.enabled,"fuel and valid GUI start");
  c.update();ok(c.production>0,"produced");ok(gui.enchantItem(p,0),"off button");ok(!c.enabled && c.production==0,"manual off updates state immediately");
  c.setEnabled(true);NBTTagCompound n=c.writeToNBT(new NBTTagCompound());NuclearController loaded=new NuclearController();loaded.readFromNBT(n);
  ok(!loaded.enabled && loaded.scrammed,"NBT active state requires manual restart");eq(loaded.fuelTicks,c.fuelTicks,"NBT preserves used fuel");
  BlockPos q=NuclearController.at(NuclearReactorTest.P,EnumFacing.NORTH,1,4,2);w.states.put(q,Blocks.AIR.getDefaultState());
  ok(!gui.enchantItem(p,1),"invalid structure rejects server GUI command");c.update();ok(!c.enabled && c.scrammed,"invalid structure stops server");
 }
 public static void main(String[]args)throws Exception{NuclearUraniumTest.main(args);interruptedStarts();interruptionDuringConsumption();unloadDuringConsumption();guiAndNbt();System.out.println("NuclearStartSafetyTest: PASS");}
}
