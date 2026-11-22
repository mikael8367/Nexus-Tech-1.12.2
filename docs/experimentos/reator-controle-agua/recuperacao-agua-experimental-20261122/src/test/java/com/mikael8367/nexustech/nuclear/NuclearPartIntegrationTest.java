package com.mikael8367.nexustech.nuclear;

import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;

/** Real tiles and capability wrappers in an isolated, no-load world. */
public final class NuclearPartIntegrationTest {
 static void ok(boolean b,String label){NuclearReactorTest.ok(b,label);}
 static void eq(int a,int b,String label){NuclearReactorTest.eq(a,b,label);}
 static void faces() throws Exception {
  for(EnumFacing front:EnumFacing.Plane.HORIZONTAL){
   NuclearReactorTest.TestWorld w=NuclearReactorTest.world();NuclearController c=NuclearReactorTest.build(w,front);c.energy=5000;c.update();
   for(int x=-1;x<=1;x++)for(int y=0;y<5;y++)for(int d=0;d<3;d++){
    BlockPos q=NuclearController.at(NuclearReactorTest.P,front,x,y,d);TileEntity tile=w.tiles.get(q);
    ok(w.getBlockState(q).getActualState(w,q).getValue(NuclearBlock.FORMED),"formed actual state "+q);
    for(EnumFacing face:EnumFacing.VALUES){
     IEnergyStorage e=tile.getCapability(CapabilityEnergy.ENERGY,face);
     ok(e!=null && e.canExtract() && !e.canReceive(),"all faces "+q+" "+face);
     eq(e.extractEnergy(1,true),1,"simulation");
    }
   }
   BlockPos q=NuclearController.at(NuclearReactorTest.P,front,1,4,2);
   NuclearPart p=(NuclearPart)w.tiles.get(q);IEnergyStorage cached=p.getCapability(CapabilityEnergy.ENERGY,null);
   NuclearPart client=new NuclearPart();client.handleUpdateTag(p.getUpdateTag());ok(client.visualFormed(),"part update tag formed");
   NuclearController clientController=new NuclearController();clientController.handleUpdateTag(c.getUpdateTag());ok(clientController.visualFormed(),"controller update tag formed");
   eq(cached.extractEnergy(150,false),150,"part pull");eq(c.getCapability(CapabilityEnergy.ENERGY,null).extractEnergy(1000,false),250,"shared budget");
   w.missing.add(q);eq(cached.extractEnergy(1,false),0,"cached fails closed when unloaded");ok(c.getCapability(CapabilityEnergy.ENERGY,null)==null,"unloaded controller capability absent");w.missing.clear();
   w.states.put(q,Blocks.AIR.getDefaultState());eq(cached.extractEnergy(1,false),0,"cached fails closed on damage");ok(p.getCapability(CapabilityEnergy.ENERGY,null)==null,"damaged capability absent");ok(!c.beginOperation(),"open GUI operations denied after damage");
   c.update();ok(!c.visualFormed(),"controller visual clears");
   BlockPos other=NuclearController.at(NuclearReactorTest.P,front,-1,4,2);ok(!((NuclearPart)w.tiles.get(other)).visualFormed(),"part visual clears");
   w.put(q,NuclearRegistry.CASING.getDefaultState(),new NuclearPart());c.update();eq(cached.extractEnergy(1,false),0,"replaced stale part denied");ok(((NuclearPart)w.tiles.get(q)).visualFormed(),"repair visual restored");
  }
 }
 static void push() throws Exception {
  NuclearReactorTest.TestWorld w=NuclearReactorTest.world();NuclearController c=NuclearReactorTest.build(w,EnumFacing.NORTH);c.energy=2000;
  BlockPos part=NuclearController.at(NuclearReactorTest.P,EnumFacing.NORTH,1,4,2);NuclearRegressionTest.Receiver sink=new NuclearRegressionTest.Receiver();
  w.put(part.up(),Blocks.STONE.getDefaultState(),sink);c.update();eq(sink.received,400,"active push from distant top part");eq(c.energy,1600,"push conservation");
  IEnergyStorage cached=((NuclearPart)w.tiles.get(part)).getCapability(CapabilityEnergy.ENERGY,null);
  w.time++;w.tiles.remove(NuclearReactorTest.P);w.states.put(NuclearReactorTest.P,Blocks.AIR.getDefaultState());eq(cached.extractEnergy(100,false),0,"removed controller cached handle");ok(!cached.canExtract(),"removed controller no advertised output");
 }
 static void stale(IEnergyStorage e,String label){
  ok(!e.canExtract() && !e.canReceive(),label+" capabilities");
  eq(e.getEnergyStored(),0,label+" stored");eq(e.getMaxEnergyStored(),0,label+" capacity");
  eq(e.extractEnergy(50,true),0,label+" simulated");eq(e.extractEnergy(50,false),0,label+" real");
  eq(e.receiveEnergy(50,false),0,label+" receive");
 }
 static class MutatingWorld extends NuclearReactorTest.TestWorld {
  Runnable callback; int renderUpdates;
  @Override public void markBlockRangeForRenderUpdate(BlockPos a,BlockPos b){renderUpdates++;}
  @Override public void markChunkDirty(BlockPos pos,TileEntity tile){super.markChunkDirty(pos,tile);if(callback!=null){Runnable r=callback;callback=null;r.run();}}
 }
 static void rebindDuringExtraction() throws Exception {
  java.lang.reflect.Field field=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");field.setAccessible(true);
  MutatingWorld w=(MutatingWorld)((sun.misc.Unsafe)field.get(null)).allocateInstance(MutatingWorld.class);
  w.states=new java.util.HashMap<>();w.tiles=new java.util.HashMap<>();w.missing=new java.util.HashSet<>();
  NuclearController c=NuclearReactorTest.build(w,EnumFacing.NORTH);c.energy=5000;c.update();
  NuclearPart part=(NuclearPart)w.tiles.get(NuclearController.at(NuclearReactorTest.P,EnumFacing.NORTH,1,4,2));
  NuclearPart other=(NuclearPart)w.tiles.get(NuclearController.at(NuclearReactorTest.P,EnumFacing.NORTH,-1,0,0));
  IEnergyStorage stale=part.getCapability(CapabilityEnergy.ENERGY,null);other.unbind(NuclearReactorTest.P);
  w.callback=()->{part.unbind(NuclearReactorTest.P);part.bind(NuclearReactorTest.P);};
  eq(stale.extractEnergy(100,false),0,"rebind during bindStructure blocks stale transaction");
  eq(c.energy,5000,"reentrant rebind consumes no energy");stale(stale,"reentrant old lease");
  IEnergyStorage fresh=part.getCapability(CapabilityEnergy.ENERGY,null);eq(fresh.extractEnergy(100,false),100,"fresh lease after reentrant bind");
 }
 static void clientCableVisual() throws Exception {
  NuclearReactorTest.TestWorld server=NuclearReactorTest.world();NuclearController c=NuclearReactorTest.build(server,EnumFacing.NORTH);c.update();
  BlockPos q=NuclearController.at(NuclearReactorTest.P,EnumFacing.NORTH,1,4,2);
  NuclearPart source=(NuclearPart)server.tiles.get(q);
  java.lang.reflect.Field field=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");field.setAccessible(true);sun.misc.Unsafe u=(sun.misc.Unsafe)field.get(null);
  MutatingWorld client=(MutatingWorld)u.allocateInstance(MutatingWorld.class);client.states=new java.util.HashMap<>();client.tiles=new java.util.HashMap<>();client.missing=new java.util.HashSet<>();
  u.putBoolean(client,u.objectFieldOffset(net.minecraft.world.World.class.getField("isRemote")),true);
  NuclearPart copy=new NuclearPart();client.put(q,NuclearRegistry.CASING.getDefaultState(),copy);
  com.mikael8367.nexustech.EnergyCableBlock cable=new com.mikael8367.nexustech.EnergyCableBlock();BlockPos cablePos=q.west();
  copy.onDataPacket(null,source.getUpdatePacket());ok(client.renderUpdates==1,"client packet requests render update");
  ok(cable.getActualState(cable.getDefaultState(),client,cablePos).getValue(com.mikael8367.nexustech.EnergyCableBlock.EAST),"client cable sees formed reactor part");
  source.setVisualFormed(false);copy.onDataPacket(null,source.getUpdatePacket());
  ok(client.renderUpdates==2,"client packet requests disconnect redraw");
  ok(!cable.getActualState(cable.getDefaultState(),client,cablePos).getValue(com.mikael8367.nexustech.EnergyCableBlock.EAST),"client cable disconnects when unformed");
  NuclearController clientController=new NuclearController();client.put(q,NuclearRegistry.CONTROLLER.getDefaultState(),clientController);clientController.onDataPacket(null,c.getUpdatePacket());
  ok(cable.getActualState(cable.getDefaultState(),client,cablePos).getValue(com.mikael8367.nexustech.EnergyCableBlock.EAST),"client cable sees formed controller");
 }
 static void controllerModelSync() throws Exception {
  NuclearReactorTest.TestWorld server=NuclearReactorTest.world();
  NuclearController c=NuclearReactorTest.build(server,EnumFacing.NORTH);
  java.lang.reflect.Field field=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");field.setAccessible(true);
  sun.misc.Unsafe u=(sun.misc.Unsafe)field.get(null);
  MutatingWorld client=(MutatingWorld)u.allocateInstance(MutatingWorld.class);
  client.states=new java.util.HashMap<>();client.tiles=new java.util.HashMap<>();client.missing=new java.util.HashSet<>();
  u.putBoolean(client,u.objectFieldOffset(net.minecraft.world.World.class.getField("isRemote")),true);
  NuclearController copy=new NuclearController();client.put(NuclearReactorTest.P,NuclearRegistry.CONTROLLER.getDefaultState(),copy);
  copy.handleUpdateTag(c.getUpdateTag());
  ok(!client.getBlockState(NuclearReactorTest.P).getActualState(client,NuclearReactorTest.P).getValue(NuclearBlock.FORMED),"initial chunk tag not formed");
  c.update();copy.onDataPacket(null,c.getUpdatePacket());
  ok(client.getBlockState(NuclearReactorTest.P).getActualState(client,NuclearReactorTest.P).getValue(NuclearBlock.FORMED),"formed packet selects details");
  eq(client.renderUpdates,1,"formed packet redraws controller");
  NuclearController late=new NuclearController();late.handleUpdateTag(c.getUpdateTag());ok(late.visualFormed(),"late chunk tag formed");
  BlockPos part=NuclearController.at(NuclearReactorTest.P,EnumFacing.NORTH,1,4,2);
  server.missing.add(part);c.update();copy.onDataPacket(null,c.getUpdatePacket());
  ok(!copy.visualFormed(),"unloaded structure clears details");eq(client.renderUpdates,2,"unloaded packet redraws");
  server.missing.clear();c.update();copy.onDataPacket(null,c.getUpdatePacket());ok(copy.visualFormed(),"loaded valid structure restores details");
  server.states.put(part,Blocks.AIR.getDefaultState());c.update();copy.onDataPacket(null,c.getUpdatePacket());
  ok(!client.getBlockState(NuclearReactorTest.P).getActualState(client,NuclearReactorTest.P).getValue(NuclearBlock.FORMED),"broken structure removes details");
  late.handleUpdateTag(c.getUpdateTag());ok(!late.visualFormed(),"late chunk tag after damage unformed");
  server.put(part,NuclearRegistry.CASING.getDefaultState(),new NuclearPart());c.update();copy.onDataPacket(null,c.getUpdatePacket());
  ok(copy.visualFormed(),"repair restores details");eq(client.renderUpdates,5,"every transition redraws");
 }
 static void bindingGenerations() throws Exception {
  NuclearReactorTest.TestWorld w=NuclearReactorTest.world();NuclearController c=NuclearReactorTest.build(w,EnumFacing.NORTH);c.energy=5000;c.update();
  BlockPos q=NuclearController.at(NuclearReactorTest.P,EnumFacing.NORTH,1,4,2);NuclearPart p=(NuclearPart)w.tiles.get(q);
  IEnergyStorage first=p.getCapability(CapabilityEnergy.ENERGY,null);ok(first.canExtract(),"initial lease live");
  p.unbind(NuclearReactorTest.P);p.bind(NuclearReactorTest.P);stale(first,"unbind rebind same owner");
  IEnergyStorage second=p.getCapability(CapabilityEnergy.ENERGY,null);ok(second!=first && second.canExtract(),"new lease after rebind");
  BlockPos foreign=NuclearReactorTest.P.add(100,0,0);p.bind(foreign);p.bind(NuclearReactorTest.P);stale(second,"different owner then original");
  IEnergyStorage third=p.getCapability(CapabilityEnergy.ENERGY,null);
  net.minecraft.nbt.NBTTagCompound saved=p.writeToNBT(new net.minecraft.nbt.NBTTagCompound());p.readFromNBT(saved);stale(third,"read NBT invalidates generation");
  IEnergyStorage fourth=p.getCapability(CapabilityEnergy.ENERGY,null);
  NuclearController replacement=new NuclearController();replacement.energy=8000;w.put(NuclearReactorTest.P,w.getBlockState(NuclearReactorTest.P),replacement);
  stale(fourth,"different controller at same coordinates");
  w.put(NuclearReactorTest.P,w.getBlockState(NuclearReactorTest.P),c);
  stale(fourth,"observed replacement permanently revokes old lease");
  w.put(NuclearReactorTest.P,w.getBlockState(NuclearReactorTest.P),replacement);
  IEnergyStorage fifth=p.getCapability(CapabilityEnergy.ENERGY,null);ok(fifth!=fourth && fifth.canExtract(),"replacement gets new lease");
  eq(fifth.extractEnergy(100,false),100,"replacement FE accessible through new lease");eq(replacement.energy,7900,"only replacement charged");eq(c.energy,5000,"previous controller unchanged");
  w.put(NuclearReactorTest.P,w.getBlockState(NuclearReactorTest.P),c);stale(fifth,"replacement lease cannot access old controller");stale(fourth,"old lease stays revoked after owner returns");
  IEnergyStorage sixth=p.getCapability(CapabilityEnergy.ENERGY,null);p.onChunkUnload();stale(sixth,"chunk unload revokes lease");
 }
 public static void main(String[]args)throws Exception{NuclearUpdateReentryTest.main(args);faces();push();bindingGenerations();rebindDuringExtraction();clientCableVisual();controllerModelSync();System.out.println("NuclearPartIntegrationTest: PASS");}
}
