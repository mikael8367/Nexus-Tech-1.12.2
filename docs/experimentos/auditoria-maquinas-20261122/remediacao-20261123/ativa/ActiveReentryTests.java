package com.mikael8367.nexustech.experiment.tenmachines;
import com.mikael8367.nexustech.techmachines.TechMachineTile;
import net.minecraft.init.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.items.*;
import net.minecraftforge.energy.*;
import java.lang.reflect.*;

public class ActiveReentryTests {
 static int n;
 static void ok(boolean x,String msg){n++;if(!x)throw new AssertionError(msg);}
 static void eq(int a,int b,String m){ok(a==b,m+" got="+a+" expected="+b);}
 static Method drain;
 static ItemStack drain(TechMachineTile t,int slot){try{return (ItemStack)drain.invoke(t,slot);}catch(Exception e){throw new RuntimeException(e);}}
 static IItemHandler inv(TechMachineTile t){return t.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY,EnumFacing.UP);}
 static IEnergyStorage en(TechMachineTile t){return t.getCapability(CapabilityEnergy.ENERGY,null);}
 static int prog(TechMachineTile t){return t.writeToNBT(new NBTTagCompound()).getInteger("Progress");}
 static TechMachineTile.Sawmill machine(TenMachinesTest.FakeWorld w){TechMachineTile.Sawmill t=new TechMachineTile.Sawmill();w.tile=t;t.setWorld(w);t.setPos(TenMachinesTest.POS);return t;}
 static void charge(TechMachineTile t,int count){while(count>0){int x=en(t).receiveEnergy(count,false);ok(x>0,"charge");count-=x;}}
 static void ticks(TechMachineTile t,int count){while(count-->0)t.update();}
 static void setup(TechMachineTile t,int logs){inv(t).insertItem(0,new ItemStack(Blocks.LOG,logs),false);charge(t,1600);ticks(t,79);}
 static NBTTagCompound save(TechMachineTile t){return t.writeToNBT(new NBTTagCompound());}
 public static void main(String[] args)throws Exception{
  Bootstrap.register();TenMachinesTest.caps();net.minecraft.tileentity.TileEntity.register("test:active_sawmill", TechMachineTile.Sawmill.class);drain=TechMachineTile.class.getDeclaredMethod("drain",int.class);drain.setAccessible(true);
  TenMachinesTest.FakeWorld w=TenMachinesTest.world(false);TechMachineTile t=machine(w);IItemHandler h=inv(t);
  eq(h.insertItem(0,new ItemStack(Blocks.LOG,2),true).getCount(),0,"simulate insert remainder");ok(h.getStackInSlot(0).isEmpty(),"simulate no insert");
  h.insertItem(0,new ItemStack(Blocks.LOG,2),false);charge(t,1600);ticks(t,79);eq(prog(t),79,"79 progress");eq(en(t).getEnergyStored(),20,"79 energy");ok(h.getStackInSlot(1).isEmpty(),"79 no output");
  final ItemStack[] got=new ItemStack[4];w.onDirty=()->{eq(prog(t),0,"callback progress committed");eq(en(t).getEnergyStored(),0,"callback energy committed");got[0]=drain(t,0);got[1]=drain(t,1);got[2]=drain(t,0);got[3]=drain(t,1);};t.update();
  eq(got[0].getCount(),1,"one remaining input");eq(got[1].getCount(),6,"only six output");ok(got[2].isEmpty()&&got[3].isEmpty(),"multiple drain exactly once");
  TenMachinesTest.FakeWorld a=TenMachinesTest.world(false);TechMachineTile u=machine(a);setup(u,1);a.onDirty=()->{got[0]=drain(u,0);got[1]=drain(u,1);};u.update();ok(got[0].isEmpty(),"original probe input consumed");eq(got[1].getCount(),6,"original probe output only");
  TenMachinesTest.FakeWorld b=TenMachinesTest.world(false);TechMachineTile v=machine(b);IItemHandler vi=inv(v);final boolean[] callbacks={false};
  b.onDirty=()->{callbacks[0]=true;ok(!vi.insertItem(0,new ItemStack(Blocks.LOG),false).isEmpty(),"insert callback blocked");ok(vi.extractItem(0,1,false).isEmpty(),"extract callback blocked");eq(en(v).receiveEnergy(20,false),0,"energy callback blocked");NBTTagCompound forged=new NBTTagCompound();forged.setInteger("Energy",20000);v.readFromNBT(forged);};
  ok(vi.insertItem(0,new ItemStack(Blocks.LOG,2),false).isEmpty(),"insertion");ok(callbacks[0],"insertion callback occurred");eq(vi.getStackInSlot(0).getCount(),2,"insert count");eq(en(v).getEnergyStored(),0,"reentrant NBT refused");
  b.onDirty=()->{got[0]=drain(v,0);};eq(vi.extractItem(0,1,false).getCount(),1,"extraction returns one");eq(got[0].getCount(),1,"drain remaining one in callback");ok(vi.getStackInSlot(0).isEmpty(),"both removed");
  b.onDirty=()->{eq(en(v).receiveEnergy(20,false),0,"recursive receive blocked");};eq(en(v).receiveEnergy(100,false),100,"energy receive");eq(en(v).getEnergyStored(),100,"energy once");
  vi.insertItem(0,new ItemStack(Blocks.LOG),false);ticks(v,4);eq(prog(v),4,"partial progress");eq(en(v).getEnergyStored(),20,"partial energy");b.onDirty=()->{got[0]=drain(v,0);};v.update();eq(got[0].getCount(),1,"drain on ordinary tick");eq(prog(v),0,"drain resets progress");eq(en(v).getEnergyStored(),0,"ordinary energy once");
  TenMachinesTest.FakeWorld c=TenMachinesTest.world(false);TechMachineTile x=machine(c);setup(x,1);c.onDirty=()->{throw new IllegalStateException("callback failure");};try{x.update();throw new AssertionError("expected exception");}catch(IllegalStateException expected){}eq(prog(x),0,"exception after committed progress");eq(en(x).getEnergyStored(),0,"exception after energy");ok(inv(x).getStackInSlot(0).isEmpty(),"exception after consumed input");eq(inv(x).getStackInSlot(1).getCount(),6,"exception after output");eq(inv(x).extractItem(1,64,false).getCount(),6,"busy released");
  TenMachinesTest.FakeWorld d=TenMachinesTest.world(false);TechMachineTile z=machine(d);setup(z,1);NBTTagCompound snap=save(z);TechMachineTile.Sawmill restored=machine(d);restored.readFromNBT(snap);eq(prog(restored),79,"restore progress");eq(en(restored).getEnergyStored(),20,"restore energy");restored.update();eq(inv(restored).getStackInSlot(1).getCount(),6,"restored completion");ok(!inv(z).insertItem(0,new ItemStack(Blocks.LOG),false).isEmpty(),"stale identity rejects insertion");eq(en(z).receiveEnergy(10,false),0,"stale identity rejects energy");z.update();ok(inv(z).getStackInSlot(1).isEmpty(),"stale tile does not tick");
  TenMachinesTest.FakeWorld e=TenMachinesTest.world(false);TechMachineTile f=machine(e);NBTTagCompound bad=new NBTTagCompound();bad.setInteger("Energy",Integer.MAX_VALUE);bad.setInteger("Progress",Integer.MAX_VALUE);f.readFromNBT(bad);eq(en(f).getEnergyStored(),20000,"clamp energy");eq(prog(f),79,"clamp progress");ticks(f,1);eq(prog(f),0,"invalid recipe resets progress");
  TenMachinesTest.FakeWorld g=TenMachinesTest.world(false);TechMachineTile full=machine(g);inv(full).insertItem(0,new ItemStack(Blocks.LOG,12),false);charge(full,20000);ticks(full,800);eq(inv(full).getStackInSlot(1).getCount(),60,"ten outputs");int before=en(full).getEnergyStored();ticks(full,80);eq(en(full).getEnergyStored(),before,"full output no debit");eq(inv(full).getStackInSlot(0).getCount(),2,"full retains input");
  TenMachinesTest.FakeWorld q=TenMachinesTest.world(false);TechMachineTile changed=machine(q);setup(changed,1);q.loaded=false;changed.update();eq(prog(changed),79,"unloaded no progress");q.loaded=true;q.tile=new TechMachineTile.Sawmill();changed.update();eq(prog(changed),79,"replaced identity no progress");
  Bootstrap.printToSYSOUT("PASS ActiveReentryTests assertions="+n);
 }
}
