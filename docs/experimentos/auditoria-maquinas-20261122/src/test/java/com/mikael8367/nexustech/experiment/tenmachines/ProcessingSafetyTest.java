package com.mikael8367.nexustech.experiment.tenmachines;

import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.items.IItemHandler;

/** Run independently with the cached mapped Forge API, not a running server. */
public final class ProcessingSafetyTest {
 static void check(boolean b,String m){TenMachinesTest.ok(b,m);}
 static void eq(int a,int b,String m){TenMachinesTest.eq(a,b,m);}
 static ProcessingTile fresh(MachineKind k, TenMachinesTest.FakeWorld w){return TenMachinesTest.machine(k,w);}
 static IItemHandler inv(ProcessingTile t){return TenMachinesTest.inv(t);}
 static NBTTagCompound entry(ItemStack s,int slot){NBTTagCompound n=new NBTTagCompound();s.writeToNBT(n);n.setByte("Slot",(byte)slot);return n;}
 static void replaceSlot(NBTTagCompound n,int slot,ItemStack s){
  NBTTagList old=n.getTagList("Inventory",10), list=new NBTTagList();
  for(int i=0;i<old.tagCount();i++){NBTTagCompound e=old.getCompoundTagAt(i);if((e.getByte("Slot")&255)!=slot)list.appendTag(e.copy());}
  if(!s.isEmpty())list.appendTag(entry(s,slot));n.setTag("Inventory",list);
 }
 public static void main(String[] args)throws Exception{
  Bootstrap.register();TenMachinesTest.caps();ExperimentalRegistry.registerTiles();
  TenMachinesTest.FakeWorld w=TenMachinesTest.world(false);ProcessingTile t=fresh(MachineKind.SEED_EXTRACTOR,w);
  IItemHandler h=inv(t);h.insertItem(0,new ItemStack(Items.MELON,2),false);TenMachinesTest.charge(t,2000);TenMachinesTest.tick(t,70);
  NBTTagCompound good=t.writeToNBT(new NBTTagCompound());ProcessingTile same=fresh(MachineKind.SEED_EXTRACTOR,w);same.readFromNBT(good.copy());eq(same.progress(),70,"valid progress restored");
  NBTTagCompound swapped=good.copy();replaceSlot(swapped,0,new ItemStack(net.minecraft.init.Blocks.PUMPKIN));same.readFromNBT(swapped);eq(same.progress(),0,"changed recipe resets progress");eq(inv(same).getStackInSlot(0).getCount(),1,"changed input preserved");
  NBTTagCompound old=good.copy();old.removeTag("ProgressKind");same.readFromNBT(old);eq(same.progress(),0,"legacy progress resets");eq(inv(same).getStackInSlot(0).getCount(),2,"legacy items preserved");
  NBTTagCompound wrongKind=good.copy();wrongKind.setString("ProgressKind","SUGAR_MILL");same.readFromNBT(wrongKind);eq(same.progress(),0,"wrong machine resets");
  NBTTagCompound missing=good.copy();missing.removeTag("ProgressInput0");same.readFromNBT(missing);eq(same.progress(),0,"missing identity resets");
  NBTTagCompound tagged=good.copy();ItemStack altered=new ItemStack(Items.MELON,2);altered.setTagCompound(new NBTTagCompound());replaceSlot(tagged,0,altered);same.readFromNBT(tagged);eq(same.progress(),0,"tagged input rejected");TenMachinesTest.tick(same,100);eq(inv(same).getStackInSlot(0).getCount(),2,"tagged input preserved");
  check(!inv(same).isItemValid(0,null),"null validity safe");
  NBTTagCompound large=new NBTTagCompound();large.setInteger("Energy",Integer.MAX_VALUE);large.setInteger("Progress",Integer.MAX_VALUE);NBTTagList list=new NBTTagList();list.appendTag(entry(new ItemStack(Items.BONE,100),0));large.setTag("Inventory",list);
  ProcessingTile over=fresh(MachineKind.BONE_MILL,w);over.readFromNBT(large);IItemHandler hi=inv(over);eq(hi.getStackInSlot(0).getCount(),100,"oversized survives load");eq(TenMachinesTest.energy(over).getEnergyStored(),20000,"energy overflow clamps");eq(over.progress(),0,"unbound overflow progress resets");
  eq(hi.insertItem(0,new ItemStack(Items.BONE),true).getCount(),1,"overflow simulated insertion refused");eq(hi.insertItem(0,new ItemStack(Items.BONE),false).getCount(),1,"overflow insertion refused");
  eq(hi.extractItem(0,Integer.MAX_VALUE,true).getCount(),64,"simulated extraction max stack");eq(hi.getStackInSlot(0).getCount(),100,"simulation unchanged");
  eq(hi.extractItem(0,Integer.MAX_VALUE,false).getCount(),64,"real extraction max stack");eq(hi.getStackInSlot(0).getCount(),36,"overflow remainder preserved");
  NBTTagCompound resave=over.writeToNBT(new NBTTagCompound());ProcessingTile reload=fresh(MachineKind.BONE_MILL,w);reload.readFromNBT(resave);eq(inv(reload).extractItem(0,Integer.MAX_VALUE,false).getCount(),36,"remainder survives reload");
  Bootstrap.printToSYSOUT("PASS safety assertions="+TenMachinesTest.assertions);
 }
}
