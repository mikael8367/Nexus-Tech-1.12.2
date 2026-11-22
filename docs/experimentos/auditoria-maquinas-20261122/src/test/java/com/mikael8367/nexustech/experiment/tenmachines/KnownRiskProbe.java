package com.mikael8367.nexustech.experiment.tenmachines;
import static com.mikael8367.nexustech.experiment.tenmachines.TenMachinesTest.*;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.items.IItemHandler;
/** Negative probes: assert observation of known risks, NOT safe outcomes. */
public final class KnownRiskProbe {
 public static void main(String[] args)throws Exception {
  Bootstrap.register();caps();ExperimentalRegistry.registerTiles();
  FakeWorld w=world(false);ProcessingTile t=machine(MachineKind.BONE_MILL,w);
  NBTTagCompound n=new NBTTagCompound();NBTTagList l=new NBTTagList();l.appendTag(ProcessingSafetyTest.entry(new ItemStack(Items.BONE,5),0));l.appendTag(ProcessingSafetyTest.entry(new ItemStack(Items.BONE,7),0));n.setTag("Inventory",l);t.readFromNBT(n);
  eq(inv(t).getStackInSlot(0).getCount(),7,"duplicate slot last wins");
  eq(inv(t).getStackInSlot(0).getCount(),12-5,"five items lost in malformed duplicate slot");
  FakeWorld x=world(false);ProcessingTile inserted=machine(MachineKind.BONE_MILL,x);IItemHandler h=SidedAutomationTest.side(inserted,EnumFacing.UP);ItemStack source=new ItemStack(Items.BONE);final int[] seen={-1,-1};
  x.onDirty=()->{inserted.requestDrops(x,POS);seen[0]=source.getCount();seen[1]=x.drops.size();};
  ItemStack remaining=h.insertItem(0,source,false);eq(seen[0],1,"source not debited in reentrant callback");eq(seen[1],0,"drop deferred during callback");eq(x.drops.size(),1,"drop appears before caller debits source");eq(source.getCount(),1,"caller has not debited source when insert returns");eq(x.drops.get(0).getCount(),1,"one bone dropped during insertion");ok(remaining.isEmpty(),"successful insertion returned");
  Bootstrap.printToSYSOUT("OBSERVED RISKS assertions="+assertions+": duplicate NBT slot loses 5 items; conditional reentrant drop precedes caller debit (not proof of normal duplication)");
 }
}
