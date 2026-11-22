package com.mikael8367.nexustech.nuclear;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;
/** Safety regressions against the real controller and an isolated no-load world. */
public final class NuclearSafetyTest {
 static void ok(boolean b,String s){NuclearReactorTest.ok(b,s);}
 static void eq(int a,int b,String s){NuclearReactorTest.eq(a,b,s);}
 // Forge normally injects this override for @Cancelable events at class-load time.
 static class CancelableBreak extends BlockEvent.BreakEvent {
  CancelableBreak(NuclearReactorTest.TestWorld w,BlockPos p){super(w,p,null,null);}
  @Override public boolean isCancelable(){return true;}
 }
 static boolean blocked(NuclearReactorTest.TestWorld w,BlockPos p){
  CancelableBreak e=new CancelableBreak(w,p);NuclearProtection.onBreak(e);return e.isCanceled();
 }
 static void lossAndReload() throws Exception {
  NuclearReactorTest.TestWorld w=NuclearReactorTest.world();NuclearController c=NuclearReactorTest.build(w,EnumFacing.NORTH);
  c.inventory.setStackInSlot(0,new ItemStack(NuclearRegistry.FUEL));c.setEnabled(true);c.update();ok(c.enabled && c.formed && c.production>0,"started");
  BlockPos missing=NuclearController.at(NuclearReactorTest.P,EnumFacing.NORTH,1,4,2),partPos=NuclearController.at(NuclearReactorTest.P,EnumFacing.NORTH,0,2,1);
  NuclearPart part=(NuclearPart)w.tiles.get(partPos);NBTTagCompound running=c.writeToNBT(new NBTTagCompound());NuclearController loaded=new NuclearController();loaded.readFromNBT(running);
  ok(!loaded.enabled && loaded.scrammed && !loaded.formed,"loaded active reactor requires deliberate restart");
  w.missing.add(missing);int e=c.energy,fuel=c.fuelTicks,heat=c.temperature,dirty=w.dirty;c.update();
  ok(!c.enabled && c.scrammed && !c.formed && c.production==0,"unloaded chunk trips and signals SCRAM");ok(w.dirty>dirty,"interruption saved");
  eq(c.energy,e,"no generation while chunk missing");eq(c.fuelTicks,fuel,"no fuel while missing");eq(c.temperature,heat,"heat paused while missing");
  ok(NuclearReactorTest.P.equals(part.owner),"unloaded structure retains owner");c.setEnabled(true);ok(!c.enabled && c.scrammed,"cannot rearm with unloaded chunk");
  w.missing.clear();c.update();ok(!c.enabled && c.scrammed && c.formed,"reload does not restart");eq(c.energy,e,"reload does not generate");
  c.setEnabled(true);ok(c.enabled && !c.scrammed,"explicit validated restart");w.states.put(missing,Blocks.AIR.getDefaultState());c.update();
  ok(!c.enabled && c.scrammed && !c.formed,"invalid geometry trips");c.setEnabled(true);ok(!c.enabled && c.scrammed,"cannot rearm invalid geometry");
  w.states.put(missing,NuclearRegistry.CASING.getDefaultState());c.update();ok(!c.enabled && c.scrammed && c.formed,"repair does not restart");
  c.temperature=401;c.setEnabled(true);ok(!c.enabled && c.scrammed,"hot SCRAM refuses reset");c.temperature=400;c.setEnabled(true);ok(c.enabled && !c.scrammed,"cooled explicit reset");
 }
 static void requestDuringLoss() throws Exception {
  NuclearReactorTest.TestWorld w=NuclearReactorTest.world();NuclearController c=NuclearReactorTest.build(w,EnumFacing.EAST);
  BlockPos missing=NuclearController.at(NuclearReactorTest.P,EnumFacing.EAST,1,4,2);
  BlockPos partPos=NuclearController.at(NuclearReactorTest.P,EnumFacing.EAST,0,2,1);
  c.fuelTicks=1;c.setEnabled(true);c.temperature=450;w.missing.add(missing);
  c.setEnabled(true);ok(!c.enabled && c.scrammed && !c.formed,"re-enable request trips immediately when chunk missing");
  ok(blocked(w,partPos) && NuclearReactorTest.P.equals(((NuclearPart)w.tiles.get(partPos)).owner),"hot interrupted owner retains protection");
  NuclearController saved=new NuclearController();saved.readFromNBT(c.writeToNBT(new NBTTagCompound()));
  ok(!saved.enabled && saved.scrammed && saved.temperature==450,"interruption remains latched in NBT");
  w.missing.clear();c.update();ok(!c.enabled && c.scrammed && c.formed,"chunk restoration alone does not rearm hot reactor");
  c.setEnabled(true);ok(!c.enabled && c.scrammed,"hot reactor requires cooldown even after restoration");
  c.temperature=NuclearController.RESET;c.setEnabled(true);ok(c.enabled && !c.scrammed,"only explicit cooled request re-enables");
 }
 static void hotOwnership() throws Exception {
  NuclearReactorTest.TestWorld w=NuclearReactorTest.world();BlockPos p=NuclearReactorTest.P,p2=p.east(2);
  NuclearController a=NuclearRegressionTest.build(w,p,EnumFacing.NORTH),b=NuclearRegressionTest.build(w,p2,EnumFacing.NORTH);
  a.update();BlockPos shared=NuclearController.at(p,EnumFacing.NORTH,1,3,1),broken=NuclearController.at(p,EnumFacing.NORTH,-1,4,2);
  NuclearPart part=(NuclearPart)w.tiles.get(shared);a.fuelTicks=1;a.setEnabled(true);a.temperature=405;w.states.put(broken,Blocks.AIR.getDefaultState());
  eq(b.validateStructure(),0,"hot invalid owner cannot be displaced before update");a.update();ok(!a.enabled && a.scrammed && p.equals(part.owner),"hot invalid structure retains binding");
  ok(blocked(w,shared) && blocked(w,p),"mining protected while hot and invalid");eq(b.validateStructure(),0,"hot invalid owner blocks takeover");
  a.releaseStructure();ok(p.equals(part.owner),"release guard keeps hot parts");w.missing.add(p);ok(blocked(w,shared),"unloaded owner fails closed");w.missing.clear();
  for(int i=0;i<10 && a.temperature>400;i++)a.update();ok(a.temperature<=400,"invalid reactor cools within bounded ticks");ok(!blocked(w,shared),"cooled owner permits mining");a.update();ok(part.owner==null,"cooled invalid owner releases parts");
  eq(b.validateStructure(),1,"cooled released structure is claimable");b.update();ok(p2.equals(part.owner),"new owner takes cooled part");
 }
 static void fluid() throws Exception {
  NuclearReactorTest.TestWorld w=NuclearReactorTest.world();NuclearController c=NuclearReactorTest.build(w,EnumFacing.NORTH);
  IFluidHandler f=c.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY,null);IFluidTankProperties p=f.getTankProperties()[0];FluidStack water=new FluidStack(FluidRegistry.WATER,1000);
  ok(p.canFill() && p.canFillFluidType(water),"water input advertised");ok(!p.canDrain() && !p.canDrainFluidType(water),"output not advertised");
  FluidStack lava=new FluidStack(FluidRegistry.LAVA,1000);ok(!p.canFillFluidType(lava),"lava input not advertised");
  eq(f.fill(lava,false),0,"simulated lava fill rejected");eq(f.fill(lava,true),0,"lava fill rejected");
  eq(f.fill(water,false),1000,"simulated water fill");eq(c.water(),0,"simulation leaves tank empty");eq(f.fill(water,true),1000,"water fill");
  ok(f.drain(1,false)==null && f.drain(1,true)==null && f.drain(water,false)==null && f.drain(water,true)==null,"all external drains denied");
  c.inventory.setStackInSlot(0,new ItemStack(NuclearRegistry.FUEL));c.setEnabled(true);c.update();eq(c.water(),999,"internal coolant use still works");
 }
 public static void main(String[] args) throws Exception {NuclearRegressionTest.main(args);lossAndReload();requestDuringLoss();hotOwnership();fluid();System.out.println("NuclearSafetyTest: PASS");}
}
