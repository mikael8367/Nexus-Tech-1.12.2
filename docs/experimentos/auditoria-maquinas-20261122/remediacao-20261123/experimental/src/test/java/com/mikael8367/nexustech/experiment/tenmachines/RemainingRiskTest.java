package com.mikael8367.nexustech.experiment.tenmachines;
import static com.mikael8367.nexustech.experiment.tenmachines.TenMachinesTest.*;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.items.IItemHandler;
/** Expected observations of unfixed risks, not evidence of security. */
public final class RemainingRiskTest {
 public static void main(String[] args)throws Exception{
  Bootstrap.register();caps();ExperimentalRegistry.registerTiles();
  FakeWorld w=world(false);ProcessingTile t=machine(MachineKind.BONE_MILL,w);NBTTagCompound tag=new NBTTagCompound();NBTTagList list=new NBTTagList();list.appendTag(ProcessingSafetyTest.entry(new ItemStack(Items.BONE,5),0));list.appendTag(ProcessingSafetyTest.entry(new ItemStack(Items.BONE,7),0));tag.setTag("Inventory",list);tag.setInteger("Progress",10);t.readFromNBT(tag);ok(t.isQuarantined(),"quarantined");t.requestDrops(w,POS);eq(w.drops.size(),1,"break drops only parsed first slot");eq(w.drops.get(0).getCount(),5,"second duplicate is not dropped");eq(t.writeToNBT(new NBTTagCompound()).getTagList("Inventory",10).tagCount(),2,"stale serialized raw contains both entries after drop");
  FakeWorld x=world(false);ProcessingTile inserted=machine(MachineKind.BONE_MILL,x);IItemHandler h=inv(inserted);ItemStack source=new ItemStack(Items.BONE);final int[] seen={-1,-1};x.onDirty=()->{inserted.requestDrops(x,POS);seen[0]=source.getCount();seen[1]=x.drops.size();};ItemStack rem=h.insertItem(0,source,false);eq(seen[0],1,"source not yet debited");eq(seen[1],0,"drop deferred inside callback");eq(x.drops.size(),1,"drop occurs before caller debits");ok(rem.isEmpty(),"handler reports success");
  Bootstrap.printToSYSOUT("OBSERVED remaining risks assertions="+assertions+": quarantine break is unsafe; reentrant external transaction is not atomic");
 }
}
