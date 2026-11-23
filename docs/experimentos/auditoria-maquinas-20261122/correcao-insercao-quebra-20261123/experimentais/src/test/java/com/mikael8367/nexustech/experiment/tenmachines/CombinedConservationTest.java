package com.mikael8367.nexustech.experiment.tenmachines;
import static com.mikael8367.nexustech.experiment.tenmachines.TenMachinesTest.*;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.items.IItemHandler;

/** Cross-feature conservation checks against exactly the combined machine classes. */
public final class CombinedConservationTest {
 public static void main(String[] args)throws Exception {
  Bootstrap.register();caps();ExperimentalRegistry.registerTiles();
  FakeWorld w=world(false); ProcessingTile t=machine(MachineKind.DYE_MIXER,w); IItemHandler phys=SidedAutomationTest.side(t,EnumFacing.UP);
  phys.insertItem(0,new ItemStack(Items.DYE,3,4),false); phys.insertItem(1,new ItemStack(Items.DYE,2,2),false); charge(t,5000); tick(t,100); tick(t,30);
  NBTTagCompound legacy=t.writeToNBT(new NBTTagCompound()); legacy.removeTag("ProgressKind"); legacy.removeTag("ProgressInput0"); legacy.removeTag("ProgressInput1");
  int oldEnergy=energy(t).getEnergyStored(); ProcessingTile restored=machine(MachineKind.DYE_MIXER,w);restored.readFromNBT(legacy); IItemHandler p=SidedAutomationTest.side(restored,EnumFacing.NORTH), n=inv(restored);
  eq(restored.progress(),0,"legacy resets paid progress");eq(energy(restored).getEnergyStored(),oldEnergy,"legacy remaining FE conserved");
  eq(p.getStackInSlot(0).getCount(),2,"legacy input 0 conserved");eq(p.getStackInSlot(1).getCount(),1,"legacy input 1 conserved");eq(p.getStackInSlot(2).getCount(),2,"legacy output conserved");
  ok(p.extractItem(0,1,false).isEmpty(),"legacy physical input blocked");eq(n.extractItem(0,1,true).getCount(),1,"legacy null simulation");eq(n.getStackInSlot(0).getCount(),2,"simulation preserves input");
  eq(n.extractItem(0,1,false).getCount(),1,"legacy null recovery");eq(n.getStackInSlot(0).getCount(),1,"recovery remainder");
  eq(p.extractItem(2,2,false).getCount(),2,"legacy output recovery");ok(n.extractItem(2,1,false).isEmpty(),"no duplicate through alias");
  NBTTagCompound persisted=restored.writeToNBT(new NBTTagCompound()); ProcessingTile rr=machine(MachineKind.DYE_MIXER,w);rr.readFromNBT(persisted);eq(inv(rr).getStackInSlot(0).getCount(),1,"remainder persists");eq(inv(rr).getStackInSlot(1).getCount(),1,"second ingredient persists");ok(inv(rr).getStackInSlot(2).isEmpty(),"extracted output remains absent");
  tick(rr,100);eq(inv(rr).getStackInSlot(2).getCount(),2,"fresh cycle yields exactly two");ok(inv(rr).getStackInSlot(0).isEmpty(),"one remaining ingredient consumed");ok(inv(rr).getStackInSlot(1).isEmpty(),"second remaining ingredient consumed");
  FakeWorld ow=world(false);ProcessingTile over=machine(MachineKind.BONE_MILL,ow);NBTTagCompound corrupt=new NBTTagCompound();NBTTagList list=new NBTTagList();list.appendTag(ProcessingSafetyTest.entry(new ItemStack(Items.BONE,100),0));list.appendTag(ProcessingSafetyTest.entry(new ItemStack(Items.DYE,100,15),2));corrupt.setTag("Inventory",list);over.readFromNBT(corrupt);
  IItemHandler face=SidedAutomationTest.side(over,EnumFacing.SOUTH),internal=inv(over);ok(face.extractItem(0,100,false).isEmpty(),"oversize input blocked on face");eq(internal.getStackInSlot(0).getCount(),100,"face cannot remove oversized input");eq(face.extractItem(2,100,true).getCount(),64,"output simulate capped");eq(face.getStackInSlot(2).getCount(),100,"output simulate unchanged");eq(face.extractItem(2,100,false).getCount(),64,"output real capped");eq(internal.getStackInSlot(2).getCount(),36,"shared output remainder");
  NBTTagCompound save=over.writeToNBT(new NBTTagCompound());ProcessingTile again=machine(MachineKind.BONE_MILL,ow);again.readFromNBT(save);face=SidedAutomationTest.side(again,EnumFacing.EAST);internal=inv(again);eq(face.extractItem(2,100,false).getCount(),36,"output remainder survives save");ok(internal.extractItem(2,1,false).isEmpty(),"output not duplicated across views");eq(internal.extractItem(0,100,false).getCount(),64,"input recovery cap");eq(internal.extractItem(0,100,false).getCount(),36,"input recovery remainder");ok(internal.extractItem(0,1,false).isEmpty(),"input not duplicated");
  FakeWorld dw=world(false);ProcessingTile dual=machine(MachineKind.DYE_MIXER,dw);IItemHandler di=inv(dual);di.insertItem(0,new ItemStack(Items.DYE,1,4),false);di.insertItem(1,new ItemStack(Items.DYE,1,2),false);charge(dual,2000);tick(dual,50);NBTTagCompound bound=dual.writeToNBT(new NBTTagCompound());bound.removeTag("ProgressInput1");ProcessingTile bad=machine(MachineKind.DYE_MIXER,dw);bad.readFromNBT(bound);eq(bad.progress(),0,"missing second identity resets");eq(inv(bad).getStackInSlot(0).getCount(),1,"first ingredient remains");eq(inv(bad).getStackInSlot(1).getCount(),1,"second ingredient remains");eq(energy(bad).getEnergyStored(),1000,"unspent energy remains");tick(bad,100);ok(inv(bad).getStackInSlot(2).isEmpty(),"no free output after reset");eq(inv(bad).getStackInSlot(0).getCount(),1,"underfunded input remains");
  Bootstrap.printToSYSOUT("PASS combined conservation assertions="+assertions);
 }
}
