package com.mikael8367.nexustech.nuclear;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import net.minecraft.init.Bootstrap;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.storage.MapStorage;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import sun.misc.Unsafe;

/** Real Forge tank/controller, artificial synchronous world callback; not a live game. */
public final class NuclearFillRemovalReentryTest {
 static final class CallbackWorld extends NuclearReactorTest.TestWorld {
  MapStorage storage; boolean armed; int callbacks, waterAtCallback=-1;
  @Override public MapStorage getPerWorldStorage(){return storage;}
  @Override public void removeTileEntity(BlockPos p){TileEntity t=tiles.remove(p);if(t!=null)t.invalidate();}
  @Override public void markChunkDirty(BlockPos p,TileEntity t){
   super.markChunkDirty(p,t);
   if(armed && t instanceof NuclearController){
    armed=false;callbacks++;waterAtCallback=((NuclearController)t).water();
    NuclearRegistry.CONTROLLER.breakBlock(this,p,getBlockState(p));
   }
  }
 }
 static CallbackWorld world() throws Exception {
  Field f=Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);
  CallbackWorld w=(CallbackWorld)((Unsafe)f.get(null)).allocateInstance(CallbackWorld.class);
  w.states=new HashMap<>();w.tiles=new HashMap<>();w.missing=new HashSet<>();w.storage=new MapStorage(null);
  return w;
 }
 static void eq(int actual,int expected,String label){NuclearReactorTest.eq(actual,expected,label);}
 static void ok(boolean actual,String label){NuclearReactorTest.ok(actual,label);}
 static void transfer(boolean remove,int initial) throws Exception {
  CallbackWorld w=world();NuclearController c=NuclearReactorTest.build(w,EnumFacing.NORTH);
  IFluidHandler input=c.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY,null);
  if(initial>0)eq(input.fill(new FluidStack(FluidRegistry.WATER,initial),true),initial,"prefill");
  int source=5000, offer=1000, before=c.water();w.armed=remove;
  int simulated=input.fill(new FluidStack(FluidRegistry.WATER,offer),false);
  eq(simulated,offer,"simulated");eq(c.water(),before,"simulation no mutation");
  eq(w.callbacks,0,"simulation no removal");ok(w.tiles.get(NuclearReactorTest.P)==c,"simulation live");
  int accepted=input.fill(new FluidStack(FluidRegistry.WATER,offer),true);source-=accepted;
  eq(accepted,offer,"actual accepted");eq(c.water(),remove?0:initial+offer,"water held only by live owner or recovery");
  eq(w.callbacks,remove?1:0,"callback count");
  if(remove){
   eq(w.waterAtCallback,initial+offer,"mutation precedes removal");
   ok(w.tiles.get(NuclearReactorTest.P)==null && c.isInvalid(),"controller removed");
   eq(input.fill(new FluidStack(FluidRegistry.WATER,1),true),0,"stale capability rejects");
  } else ok(w.tiles.get(NuclearReactorTest.P)==c && !c.isInvalid(),"normal remains live");
  int live=remove?0:c.water(), recovered=NuclearRecovery.get(w).waterAt(NuclearReactorTest.P);
  System.out.println("remove="+remove+" initial="+initial+" source="+source+" live="+live+" recovery="+recovered);
  eq(source+live+recovered,5000+initial,"water balance");
 }
 static void normalBreak() throws Exception {
  CallbackWorld w=world();NuclearController c=NuclearReactorTest.build(w,EnumFacing.NORTH);
  IFluidHandler input=c.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY,null);
  eq(input.fill(new FluidStack(FluidRegistry.WATER,1500),true),1500,"ordinary prefill");
  NuclearRegistry.CONTROLLER.breakBlock(w,NuclearReactorTest.P,w.getBlockState(NuclearReactorTest.P));
  ok(w.tiles.get(NuclearReactorTest.P)==null && c.isInvalid(),"ordinary break removes tile");
  eq(c.water(),0,"detached tank empty");
  eq(NuclearRecovery.get(w).waterAt(NuclearReactorTest.P),1500,"ordinary break water recovered");
 }
 public static void main(String[] args) throws Exception {
  Bootstrap.register();CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY=NuclearReactorTest.capability("test:fluid");
  transfer(false,0);transfer(false,500);transfer(true,0);transfer(true,500);normalBreak();
  System.out.println("NuclearFillRemovalReentryTest: PASS");
 }
}
