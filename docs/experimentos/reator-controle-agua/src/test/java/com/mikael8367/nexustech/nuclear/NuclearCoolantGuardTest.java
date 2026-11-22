package com.mikael8367.nexustech.nuclear;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;

/** Runs against real controller/container and the no-load test world, not a game client. */
public final class NuclearCoolantGuardTest {
 static void ok(boolean b,String s){NuclearReactorTest.ok(b,s);}
 static void eq(int a,int b,String s){NuclearReactorTest.eq(a,b,s);}
 static IFluidHandler fluid(NuclearController c){return c.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY,null);}
 static void refill(NuclearController c,int n){eq(fluid(c).fill(new FluidStack(FluidRegistry.WATER,n),true),n,"water added");}
 static void defaultsAndPersistence() throws Exception {
  NuclearReactorTest.TestWorld w=NuclearReactorTest.world();NuclearController c=NuclearReactorTest.build(w,EnumFacing.NORTH);
  c.fuelTicks=10;ok(!c.coolantGuard(),"old saves default to guard off");c.setEnabled(true);c.update();eq(c.energy,200,"guard off preserves dry generation");
  c.setCoolantGuard(true);ok(c.coolantGuard() && !c.enabled && c.scrammed,"enabling dry guard trips immediately");
  eq(c.production,0,"trip clears indicator production");eq(c.fuelTicks,9,"trip itself consumes no fuel");
  NBTTagCompound n=c.writeToNBT(new NBTTagCompound());NuclearController loaded=new NuclearController();loaded.readFromNBT(n);
  ok(loaded.coolantGuard() && loaded.scrammed && !loaded.enabled,"guard and trip persisted");
  n.removeTag("CoolantGuard");loaded.readFromNBT(n);ok(!loaded.coolantGuard(),"legacy tag defaults off");
  NuclearController client=new NuclearController();client.setClientField(5,c.field(5));ok(client.coolantGuard() && client.scrammed,"flag sync");
 }
 static void exhaustionAndReset() throws Exception {
  NuclearReactorTest.TestWorld w=NuclearReactorTest.world();NuclearController c=NuclearReactorTest.build(w,EnumFacing.EAST);
  c.fuelTicks=50;c.setCoolantGuard(true);c.setEnabled(true);ok(!c.enabled && !c.scrammed,"dry cold start denied without new trip");
  refill(c,1);c.setEnabled(true);ok(c.enabled,"water allows start");c.update();
  eq(c.energy,200,"last water generation credited once");eq(c.fuelTicks,49,"one fuel tick");eq(c.water(),0,"last water consumed");
  ok(!c.enabled && c.scrammed && c.production==0,"last water trips immediately");
  c.update();eq(c.energy,200,"no extra generation after trip");c.setEnabled(true);ok(!c.enabled && c.scrammed,"dry manual reset denied");
  refill(c,5);c.temperature=401;c.setEnabled(true);ok(!c.enabled && c.scrammed,"hot reset denied");
  c.temperature=400;c.setEnabled(true);ok(c.enabled && !c.scrammed,"cooled manual reset with water");
  c.setEnabled(false);c.setCoolantGuard(false);ok(!c.enabled,"guard toggle does not start reactor");
 }
 static void fullAndBucket() throws Exception {
  NuclearReactorTest.TestWorld w=NuclearReactorTest.world();NuclearController c=NuclearReactorTest.build(w,EnumFacing.SOUTH);
  c.setCoolantGuard(true);refill(c,1);c.inventory.setStackInSlot(0,new ItemStack(NuclearRegistry.FUEL));
  c.energy=NuclearController.CAPACITY;c.setEnabled(true);c.update();eq(c.water(),1,"full buffer pauses water use");eq(c.fuelTicks,0,"full buffer pauses fuel use");ok(c.enabled && !c.scrammed,"full buffer does not trip");
  c.energy=0;c.inventory.setStackInSlot(1,new ItemStack(Items.WATER_BUCKET));c.update();eq(c.water(),1000,"bucket processed before generation");ok(c.enabled && !c.scrammed,"bucket keeps guard armed");
  eq(c.inventory.getStackInSlot(2).getCount(),1,"empty bucket returned");
 }
 static void buttons() throws Exception {
  NuclearReactorTest.TestWorld w=NuclearReactorTest.world();NuclearController c=NuclearReactorTest.build(w,EnumFacing.WEST);
  NuclearGuiAccessTest.MockPlayer p=NuclearGuiAccessTest.player(w);NuclearGuiAccessTest.at(p,NuclearReactorTest.P,0,0,0);
  c.prepareForGui();NuclearContainer gui=new NuclearContainer(p.inventory,c);
  ok(gui.enchantItem(p,2) && c.coolantGuard(),"authorized toggle");ok(!gui.enchantItem(p,3),"unknown button denied");
  NuclearGuiAccessTest.at(p,NuclearReactorTest.P,9,0,0);ok(!gui.enchantItem(p,2) && c.coolantGuard(),"out of range toggle denied");
 }
 static void reentrantGuardDuringFuelExtraction() throws Exception {
  java.lang.reflect.Field f=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);
  NuclearPartIntegrationTest.MutatingWorld w=(NuclearPartIntegrationTest.MutatingWorld)((sun.misc.Unsafe)f.get(null)).allocateInstance(NuclearPartIntegrationTest.MutatingWorld.class);
  w.states=new java.util.HashMap<>();w.tiles=new java.util.HashMap<>();w.missing=new java.util.HashSet<>();
  NuclearController c=NuclearReactorTest.build(w,EnumFacing.NORTH);c.update();
  c.inventory.setStackInSlot(0,new ItemStack(NuclearRegistry.FUEL));c.setEnabled(true);
  w.callback=()->c.setCoolantGuard(true);c.update();
  ok(c.coolantGuard() && !c.enabled && c.scrammed,"callback activating dry guard trips");
  eq(c.energy,0,"reentrant trip prevents generation");eq(c.fuelTicks,NuclearController.FUEL_TICKS,"extracted fuel remains as unburned ticks");
 }
 public static void main(String[] args) throws Exception {
  NuclearReactorTest.main(args);defaultsAndPersistence();exhaustionAndReset();fullAndBucket();buttons();reentrantGuardDuringFuelExtraction();System.out.println("NuclearCoolantGuardTest: PASS");
 }
}
