package com.mikael8367.nexustech.nuclear;

import net.minecraft.block.BlockHorizontal;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

/** Explicit regressions; exercises real capability implementations against the isolated test world. */
public final class NuclearRegressionTest {
 static void eq(int got,int want,String label){NuclearReactorTest.eq(got,want,label);}
 static void ok(boolean b,String label){NuclearReactorTest.ok(b,label);}
 static NuclearController build(NuclearReactorTest.TestWorld w,BlockPos p,EnumFacing f){
  NuclearController c=new NuclearController();w.put(p,NuclearRegistry.CONTROLLER.getDefaultState().withProperty(BlockHorizontal.FACING,f),c);
  for(int x=-1;x<=1;x++)for(int y=0;y<5;y++)for(int d=0;d<3;d++){
   if(x==0&&y==0&&d==0)continue;
   BlockPos q=NuclearController.at(p,f,x,y,d);
   if(!w.tiles.containsKey(q))w.put(q,(x==0&&y==2&&d==1?NuclearRegistry.CORE:NuclearRegistry.CASING).getDefaultState(),new NuclearPart());
  }return c;
 }
 static class Receiver extends TileEntity {
  int received,limit=Integer.MAX_VALUE; IEnergyStorage source; boolean reenter;
  IEnergyStorage sink=new IEnergyStorage(){
   public int receiveEnergy(int n,boolean sim){if(reenter)eq(source.extractEnergy(100,false),0,"reentrant pull");n=Math.min(n,limit);if(!sim)received+=n;return n;}
   public int extractEnergy(int n,boolean sim){return 0;}
   public int getEnergyStored(){return received;}public int getMaxEnergyStored(){return 100000;}
   public boolean canExtract(){return false;}public boolean canReceive(){return true;}
  };
  @Override public <T>T getCapability(Capability<T> cap,EnumFacing side){return cap==CapabilityEnergy.ENERGY?CapabilityEnergy.ENERGY.cast(sink):super.getCapability(cap,side);}
 }
 static void energy()throws Exception{
  NuclearReactorTest.TestWorld w=NuclearReactorTest.world();NuclearController c=build(w,NuclearReactorTest.P,EnumFacing.NORTH);c.energy=5000;
  IEnergyStorage fe=c.getCapability(CapabilityEnergy.ENERGY,null);
  Receiver sink=new Receiver();sink.source=fe;sink.reenter=true;w.put(NuclearReactorTest.P.north(),net.minecraft.init.Blocks.STONE.getDefaultState(),sink);
  w.time=10;BlockPos first=NuclearController.at(NuclearReactorTest.P,EnumFacing.NORTH,1,1,1); NuclearPart firstPart=(NuclearPart)w.tiles.get(first);
  int beforeDirty=w.dirty;eq(fe.extractEnergy(150,true),150,"simulate before update");ok(firstPart.owner==null,"simulation cannot form unbound structure");eq(w.dirty,beforeDirty,"simulation does not dirty unbound structure");eq(fe.extractEnergy(150,false),150,"pull before update");c.update();
  eq(sink.received,250,"push shares pull budget");eq(fe.extractEnergy(50,true),0,"simulate after full budget");eq(fe.extractEnergy(50,false),0,"pull after full budget");
  w.time=11;eq(fe.extractEnergy(500,true),400,"simulation refreshes next tick");eq(fe.extractEnergy(100,true),100,"simulation is non-consuming");c.update();eq(sink.received,650,"push first on next tick");eq(fe.extractEnergy(100,false),0,"pull after push");
  w.time=12;eq(fe.extractEnergy(75,false),75,"new tick before update");NBTTagCompound tag=c.writeToNBT(new NBTTagCompound());NuclearController copy=new NuclearController();copy.readFromNBT(tag);w.tiles.put(NuclearReactorTest.P,copy);copy.setWorld(w);copy.setPos(NuclearReactorTest.P);
  eq(copy.getCapability(CapabilityEnergy.ENERGY,null).extractEnergy(1000,true),325,"same-tick persisted budget");
  w.time=13;eq(copy.getCapability(CapabilityEnergy.ENERGY,null).extractEnergy(1000,true),400,"persisted budget resets next tick");
  NuclearReactorTest.TestWorld two=NuclearReactorTest.world();NuclearController cc=build(two,NuclearReactorTest.P,EnumFacing.NORTH);cc.energy=2000;
  Receiver low=new Receiver(),high=new Receiver();low.limit=125;two.put(NuclearReactorTest.P.down(),net.minecraft.init.Blocks.STONE.getDefaultState(),low);two.put(NuclearReactorTest.P.north(),net.minecraft.init.Blocks.STONE.getDefaultState(),high);
  cc.update();eq(low.received,125,"first receiver partial accept");eq(high.received,275,"second receiver remaining budget");eq(cc.getCapability(CapabilityEnergy.ENERGY,null).extractEnergy(1,true),0,"both receivers share budget");
 }
 static void items()throws Exception{
  NuclearReactorTest.TestWorld w=NuclearReactorTest.world();NuclearController c=build(w,NuclearReactorTest.P,EnumFacing.NORTH);
  IItemHandler h=c.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY,EnumFacing.UP);
  ItemStack junk=new ItemStack(Items.DIAMOND,3),fuel=new ItemStack(NuclearRegistry.FUEL,2),water=new ItemStack(Items.WATER_BUCKET);
  eq(h.insertItem(0,junk,true).getCount(),3,"simulate invalid fuel");eq(h.insertItem(0,junk,false).getCount(),3,"reject invalid fuel");
  eq(c.inventory.insertItem(1,junk,false).getCount(),3,"backing handler validates too");
  eq(h.insertItem(2,fuel,false).getCount(),2,"reject output insertion");ok(h.insertItem(0,fuel,false).isEmpty(),"accept fuel");ok(h.insertItem(1,water,false).isEmpty(),"accept water");
  h.getStackInSlot(0).shrink(1);eq(c.inventory.getStackInSlot(0).getCount(),2,"automation cannot mutate returned stack");
  ok(h.extractItem(0,1,true).isEmpty() && h.extractItem(0,1,false).isEmpty(),"fuel cannot be extracted");
  ok(h.extractItem(1,1,false).isEmpty(),"coolant cannot be extracted");c.inventory.setStackInSlot(2,new ItemStack(Items.BUCKET,2));
  eq(h.extractItem(2,1,true).getCount(),1,"simulate output extraction");eq(c.inventory.getStackInSlot(2).getCount(),2,"simulation preserves output");eq(h.extractItem(2,1,false).getCount(),1,"extract output");
  // The standalone bootstrap locks the mod item registry; use a registered vanilla item to test inventory NBT.
  c.inventory.setStackInSlot(0,new ItemStack(Items.DIAMOND,2));
  NBTTagCompound n=c.writeToNBT(new NBTTagCompound());NuclearController copy=new NuclearController();copy.readFromNBT(n);eq(copy.inventory.getStackInSlot(0).getCount(),2,"persist slot 0");eq(copy.inventory.getStackInSlot(1).getCount(),1,"persist coolant");eq(copy.inventory.getStackInSlot(2).getCount(),1,"persist output");
  NuclearReactorTest.TestWorld bucketWorld=NuclearReactorTest.world();NuclearController bucket=build(bucketWorld,NuclearReactorTest.P,EnumFacing.NORTH);IItemHandler bh=bucket.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY,null);
  ok(bh.insertItem(1,new ItemStack(Items.WATER_BUCKET),false).isEmpty(),"automate coolant input");bucket.update();eq(bucket.water(),1000,"internal bucket processing");eq(bh.extractItem(2,1,false).getItem()==Items.BUCKET?1:0,1,"automate empty bucket output");
 }
 static void ownership()throws Exception{
  NuclearReactorTest.TestWorld w=NuclearReactorTest.world();BlockPos p=NuclearReactorTest.P,p2=p.east(2);EnumFacing f=EnumFacing.NORTH;
  NuclearController a=build(w,p,f),b=build(w,p2,f);BlockPos shared=NuclearController.at(p,f,1,3,1);NuclearPart part=(NuclearPart)w.tiles.get(shared);
  a.update();ok(p.equals(part.owner),"old owner bound");eq(b.validateStructure(),0,"valid owner prevents sharing");
  BlockPos broken=NuclearController.at(p,f,-1,4,2);w.states.put(broken,net.minecraft.init.Blocks.AIR.getDefaultState());
  b.energy=1000;IEnergyStorage fe=b.getCapability(CapabilityEnergy.ENERGY,null);int dirty=w.dirty,changes=w.changes;
  eq(fe.extractEnergy(100,true),100,"simulate takeover");ok(p.equals(part.owner),"simulation does not bind");eq(w.dirty,dirty,"simulation not dirty");eq(w.changes,changes,"simulation does not change world");
  eq(fe.extractEnergy(100,false),100,"real takeover");ok(p2.equals(part.owner),"real extraction commits ownership");
  a.update();ok(p2.equals(part.owner),"invalid old owner cannot release new owner's part");
  ok(a.inventory.getStackInSlot(0).isEmpty(),"old inventory unaffected");
  NuclearReactorTest.TestWorld v=NuclearReactorTest.world();NuclearController x=build(v,p,f),y=build(v,p2,f);x.update();NuclearPart shared2=(NuclearPart)v.tiles.get(shared);
  v.missing.add(broken);eq(y.validateStructure(),-1,"unknown old geometry fails closed without loading");ok(p.equals(shared2.owner),"unloaded old structure keeps owner");v.missing.clear();
  v.missing.add(p);eq(y.validateStructure(),-1,"unloaded old owner fails closed");v.missing.clear();
  v.states.put(broken,net.minecraft.init.Blocks.AIR.getDefaultState());x.inventory.setStackInSlot(0,new ItemStack(NuclearRegistry.FUEL,2));x.update();ok(shared2.owner==null,"invalid structure releases loaded parts");eq(x.inventory.getStackInSlot(0).getCount(),2,"release preserves inventory");
  eq(y.validateStructure(),1,"released structure claimable");y.update();ok(p2.equals(shared2.owner),"new controller binds released parts");
  NuclearReactorTest.TestWorld z=NuclearReactorTest.world();NuclearController gone=build(z,p,f),claim=build(z,p2,f);gone.update();NuclearPart orphan=(NuclearPart)z.tiles.get(shared);z.tiles.remove(p);z.states.put(p,net.minecraft.init.Blocks.AIR.getDefaultState());
  eq(claim.validateStructure(),1,"missing old controller claimable when loaded");claim.update();ok(p2.equals(orphan.owner),"orphan claimed");
 }
 public static void main(String[]args)throws Exception{
  NuclearReactorTest.main(args);energy();items();ownership();System.out.println("NuclearRegressionTest: PASS");
 }
}
