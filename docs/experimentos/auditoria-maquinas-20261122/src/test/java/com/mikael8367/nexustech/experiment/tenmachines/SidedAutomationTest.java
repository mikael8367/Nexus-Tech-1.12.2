package com.mikael8367.nexustech.experiment.tenmachines;

import static com.mikael8367.nexustech.experiment.tenmachines.TenMachinesTest.*;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

/** Isolated fake-world checks, not a Minecraft or Forge integration test. */
public final class SidedAutomationTest {
 static IItemHandler side(ProcessingTile t,EnumFacing f){return t.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY,f);}
 public static void main(String[] args)throws Exception{
  Bootstrap.register();caps();ExperimentalRegistry.registerTiles();
  for(MachineKind kind:MachineKind.values()){
   FakeWorld w=world(false);ProcessingTile t=machine(kind,w);
   IItemHandler shared=side(t,EnumFacing.DOWN);
   for(EnumFacing f:EnumFacing.VALUES){
    ok(t.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY,f),kind+" capability "+f);
    ok(shared==side(t,f),kind+" same handler identity "+f);
    eq(shared.getSlots(),3,"three slots");
    eq(shared.getSlotLimit(-1),0,"negative slot limit");
    eq(shared.getSlotLimit(3),0,"high slot limit");
    ok(shared.extractItem(-1,1,false).isEmpty(),"negative extract");
    ok(shared.extractItem(3,1,false).isEmpty(),"high extract");
   }
   ok(shared!=inv(t),"physical and recovery views differ");
  }
  for(EnumFacing f:EnumFacing.VALUES){
   FakeWorld w=world(false);ProcessingTile t=machine(MachineKind.DYE_MIXER,w);IItemHandler a=side(t,f);
   ItemStack blue=new ItemStack(Items.DYE,65,4);ItemStack green=new ItemStack(Items.DYE,1,2);
   eq(a.insertItem(0,blue,true).getCount(),1,"simulate capacity remainder "+f);
   ok(a.getStackInSlot(0).isEmpty(),"simulate does not insert "+f);
   eq(blue.getCount(),65,"caller stack unchanged "+f);
   eq(a.insertItem(0,blue,false).getCount(),1,"capacity remainder "+f);
   eq(a.getStackInSlot(0).getCount(),64,"slot capacity "+f);
   ok(a.insertItem(1,green,false).isEmpty(),"second input "+f);
   ok(a.insertItem(2,new ItemStack(Items.DYE,1,6),false).getCount()==1,"output insertion denied "+f);
   ok(a.extractItem(0,64,true).isEmpty(),"simulated extraction input forbidden "+f);
   ok(a.extractItem(0,64,false).isEmpty(),"input0 extraction forbidden "+f);
   ok(a.extractItem(1,64,false).isEmpty(),"input1 extraction forbidden "+f);
   eq(a.getStackInSlot(0).getCount(),64,"input0 retained "+f);
   eq(a.getStackInSlot(1).getCount(),1,"input1 retained "+f);
   a.getStackInSlot(0).setCount(1);eq(a.getStackInSlot(0).getCount(),64,"defensive copy "+f);
   charge(t,2000);tick(t,100);
   eq(a.getStackInSlot(0).getCount(),63,"one consumed "+f);
   eq(a.getStackInSlot(2).getCount(),2,"output produced "+f);
   eq(a.extractItem(2,1,true).getCount(),1,"simulate output extraction "+f);
   eq(a.getStackInSlot(2).getCount(),2,"simulate no change "+f);
   NBTTagCompound save=t.writeToNBT(new NBTTagCompound());
   ProcessingTile restored=machine(MachineKind.DYE_MIXER,w);restored.readFromNBT(save);
   ok(a.extractItem(2,2,false).isEmpty(),"stale view refuses mutation "+f);
   IItemHandler r=side(restored,f);eq(r.getStackInSlot(0).getCount(),63,"persist input "+f);
   eq(r.getStackInSlot(2).getCount(),2,"persist output "+f);
   eq(r.extractItem(2,1,false).getCount(),1,"output extraction "+f);
   eq(r.extractItem(2,99,false).getCount(),1,"remaining output extraction "+f);
   ok(r.extractItem(2,1,false).isEmpty(),"no duplicated output "+f);
   w.loaded=false;ok(!r.insertItem(1,green,false).isEmpty(),"unloaded insertion rejected "+f);
   ok(r.extractItem(2,1,false).isEmpty(),"unloaded extraction rejected "+f);
   w.loaded=true;eq(inv(restored).extractItem(0,1,false).getCount(),1,"null-side recovery "+f);
  }
  FakeWorld w=world(false); ProcessingTile t=machine(MachineKind.SUGAR_MILL,w); IItemHandler a=side(t,EnumFacing.UP);
  w.onDirty=()->{ok(!a.insertItem(0,new ItemStack(Items.REEDS),false).isEmpty(),"callback cannot insert reentrantly");ok(a.extractItem(0,1,false).isEmpty(),"callback cannot extract input");};
  a.insertItem(0,new ItemStack(Items.REEDS),false);eq(a.getStackInSlot(0).getCount(),1,"callback did not duplicate");
  FakeWorld d=world(false);ProcessingTile b=machine(MachineKind.BONE_MILL,d);IItemHandler out=side(b,EnumFacing.NORTH);
  out.insertItem(0,new ItemStack(Items.BONE),false);charge(b,2000);tick(b,99);
  d.onDirty=()->{ok(out.extractItem(2,1,false).isEmpty(),"callback output extraction blocked");b.requestDrops(d,POS);};
  tick(b,1);eq(d.drops.size(),1,"one output drop");eq(d.drops.get(0).getCount(),3,"complete output drop");
  ok(out.extractItem(2,1,false).isEmpty(),"dropped stale view blocked");b.requestDrops(d,POS);eq(d.drops.size(),1,"no duplicate drop");
  Bootstrap.printToSYSOUT("PASS sided automation assertions="+assertions);
 }
}
