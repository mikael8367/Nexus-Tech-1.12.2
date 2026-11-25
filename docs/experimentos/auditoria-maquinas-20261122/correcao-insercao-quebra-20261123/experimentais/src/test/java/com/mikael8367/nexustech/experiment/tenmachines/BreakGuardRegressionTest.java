package com.mikael8367.nexustech.experiment.tenmachines;
import static com.mikael8367.nexustech.experiment.tenmachines.TenMachinesTest.*;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.block.state.IBlockState;

/** FakeWorld only. Tests the player/explosion guard, not arbitrary world edits. */
public final class BreakGuardRegressionTest {
 static NBTTagCompound duplicate(){NBTTagCompound n=new NBTTagCompound(); NBTTagList l=new NBTTagList(); l.appendTag(ProcessingSafetyTest.entry(new ItemStack(Items.BONE,5),0));l.appendTag(ProcessingSafetyTest.entry(new ItemStack(Items.BONE,7),0));n.setTag("Inventory",l);n.setInteger("Energy",10);n.setInteger("Progress",0);return n;}
 static void sameRaw(NBTTagCompound expected,ProcessingTile t){NBTTagCompound out=t.writeToNBT(new NBTTagCompound());ok(expected.getTag("Inventory").equals(out.getTag("Inventory")),"both raw duplicate entries remain visible in serialized tile");ok(expected.getTag("Energy").equals(out.getTag("Energy")),"energy retained verbatim");ok(expected.getTag("Progress").equals(out.getTag("Progress")),"progress retained verbatim");}
 public static void main(String[] args)throws Exception{
  Bootstrap.register();caps();ExperimentalRegistry.registerTiles();
  String mode=args[0]; FakeWorld w=world(false); ProcessingTile t=machine(MachineKind.BONE_MILL,w); ProcessingBlock b=(ProcessingBlock)ExperimentalRegistry.blocks().get(MachineKind.BONE_MILL); IBlockState s=b.getDefaultState();
  if(mode.equals("numeric")){
   for(String key:new String[]{"Energy","Progress"}) for(int i=0;i<5;i++){
    NBTTagCompound n=new NBTTagCompound();n.setInteger("Energy",0);n.setInteger("Progress",0);
    switch(i){case 0:n.setString(key,"0");break;case 1:n.setLong(key,4294967296L);break;case 2:n.setDouble(key,0.5);break;case 3:n.setByte(key,(byte)0);break;default:n.setTag(key,new NBTTagCompound());}
    t.readFromNBT(n);ok(t.isQuarantined(),key+" wrong numeric/tag type must quarantine case "+i);NBTTagCompound out=t.writeToNBT(new NBTTagCompound());ok(n.getTag(key).equals(out.getTag(key)),key+" raw type/value retained");
    ProcessingTile r=machine(MachineKind.BONE_MILL,w);r.readFromNBT(out);ok(r.isQuarantined(),"still quarantined after reload");ok(n.getTag(key).equals(r.writeToNBT(new NBTTagCompound()).getTag(key)),"raw survives reload");t=r;
   }
  }else if(mode.equals("identitytypes")){
   for(String field:new String[]{"ProgressKind","ProgressInput0","ProgressInput1"}){
    NBTTagCompound n=new NBTTagCompound();n.setInteger("Progress",0);n.setInteger("Energy",0);n.setInteger(field,0);t.readFromNBT(n);ok(t.isQuarantined(),"invalid identity type "+field);ok(n.getTag(field).equals(t.writeToNBT(new NBTTagCompound()).getTag(field)),"identity type preserved "+field);ProcessingTile r=machine(MachineKind.BONE_MILL,w);r.readFromNBT(t.writeToNBT(new NBTTagCompound()));ok(r.isQuarantined(),"identity remains quarantined");t=r;
   }
   NBTTagCompound n=new NBTTagCompound();n.setTag("ProgressInput0",new NBTTagCompound());t.readFromNBT(n);ok(t.isQuarantined(),"malformed identity stack quarantined");
  }else if(mode.equals("stacktypes")){
   for(String field:new String[]{"id","Count","Damage","tag","ForgeCaps"}){
    NBTTagCompound n=new NBTTagCompound(); NBTTagList l=new NBTTagList(); NBTTagCompound e=ProcessingSafetyTest.entry(new ItemStack(Items.BONE,5),0);e.setString(field,"invalid");l.appendTag(e);n.setTag("Inventory",l);t.readFromNBT(n);ok(t.isQuarantined(),field+" malformed quarantined");ok(n.getTag("Inventory").equals(t.writeToNBT(new NBTTagCompound()).getTag("Inventory")),"malformed stack preserved "+field);ProcessingTile r=machine(MachineKind.BONE_MILL,w);r.readFromNBT(t.writeToNBT(new NBTTagCompound()));ok(r.isQuarantined(),"malformed stack stays quarantined");t=r;
   }
  }else if(mode.equals("duplicate")){
   NBTTagCompound n=duplicate();t.readFromNBT(n);ok(t.isQuarantined(),"duplicate quarantined");sameRaw(n,t);NBTTagCompound saved=t.writeToNBT(new NBTTagCompound());t=machine(MachineKind.BONE_MILL,w);t.readFromNBT(saved);ok(t.isQuarantined(),"reload quarantined");
   for(int i=0;i<3;i++)ok(!b.removedByPlayer(s,w,POS,null,false),"player removal refused");b.onBlockExploded(w,POS,null);sameRaw(n,t);eq(w.drops.size(),0,"no parsed subset spawned");
  }else if(mode.equals("callback")){
   final ProcessingTile x=t;final int[] called={0};w.onDirty=()->{called[0]++;ok(!b.removedByPlayer(s,w,POS,null,false),"callback removal refused");b.onBlockExploded(w,POS,null);};
   ok(inv(x).insertItem(0,new ItemStack(Items.BONE,5),false).isEmpty(),"insert succeeds");eq(called[0],1,"callback exercised");eq(inv(x).getStackInSlot(0).getCount(),5,"input not lost");eq(w.drops.size(),0,"no spawn in callback");
  }else if(mode.equals("normal")){
   inv(t).insertItem(0,new ItemStack(Items.BONE,5),false);for(MachineKind k:MachineKind.values()){ProcessingBlock each=(ProcessingBlock)ExperimentalRegistry.blocks().get(k);for(int i=0;i<2;i++)ok(!each.removedByPlayer(each.getDefaultState(),w,POS,null,false),"normal player removal refused "+k);each.onBlockExploded(w,POS,null);}eq(inv(t).getStackInSlot(0).getCount(),5,"normal inventory retained");eq(w.drops.size(),0,"no item spawn attempted");
  }else throw new IllegalArgumentException(mode);
  Bootstrap.printToSYSOUT("PASS BreakGuardRegression "+mode+" assertions="+assertions);
 }
}
