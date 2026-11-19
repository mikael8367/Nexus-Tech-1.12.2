package com.mikael8367.nexustech;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.Callable;
import net.minecraft.init.Bootstrap;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;
import sun.misc.Unsafe;

/** Shared fixtures and energy integration checks. */
class NexusEnergyTestSupport {
 static void eq(int a,int b,String msg){ if(a!=b) throw new AssertionError(msg+": "+a+" != "+b); }
 static void ok(boolean b,String msg){if(!b)throw new AssertionError(msg);}
 static final BlockPos O=new BlockPos(0,64,0), E=O.east(), W=O.west();
 static class FakeWorld extends World {
   Map<BlockPos,TileEntity> tiles; Set<BlockPos> unloaded; int forbiddenQueries;
   private FakeWorld(){ super(null,null,null,null,false); }
   protected IChunkProvider createChunkProvider(){return null;}
   protected boolean isChunkLoaded(int x,int z,boolean a){return true;}
   public boolean isBlockLoaded(BlockPos p){return !unloaded.contains(p);}
   public TileEntity getTileEntity(BlockPos p){if(unloaded.contains(p)){forbiddenQueries++; throw new AssertionError("unloaded queried");} return tiles.get(p);}
   void put(BlockPos p,TileEntity t){tiles.put(p,t);t.setPos(p);t.setWorld(this);}
 }
 static FakeWorld world(boolean remote) throws Exception {
   Field f=Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);Unsafe u=(Unsafe)f.get(null);
   FakeWorld w=(FakeWorld)u.allocateInstance(FakeWorld.class); w.tiles=new HashMap<>(); w.unloaded=new HashSet<>();
   u.putBoolean(w,u.objectFieldOffset(World.class.getField("isRemote")),remote); return w;
 }
 static void setupCapability() throws Exception {
   Constructor<?> c=Capability.class.getDeclaredConstructors()[0]; c.setAccessible(true);
   CapabilityEnergy.ENERGY=(Capability<IEnergyStorage>)c.newInstance("test:energy",null,(Callable<IEnergyStorage>)() -> null);
 }
 static class Cable extends MachineTile.Cable {public void markDirty(){}}
 static class Battery extends MachineTile.Battery {public void markDirty(){}}
 static class Crusher extends MachineTile.Crusher {public void markDirty(){}}
 static class Compressor extends MachineTile.Compressor {public void markDirty(){}}
 static class Furnace extends MachineTile.Furnace {public void markDirty(){}}
 static class Generator extends MachineTile.Generator {public void markDirty(){}}
 static class Endpoint extends TileEntity implements IEnergyStorage {
   int energy, receiveLimit=1000, extractLimit=1000, reads; boolean receive,extract; Runnable onReceive,onExtract;
   Endpoint(int n,boolean r,boolean e){energy=n;receive=r;extract=e;}
   public <T>T getCapability(Capability<T> cap,EnumFacing side){return cap==CapabilityEnergy.ENERGY?cap.cast((T)this):null;}
   public int receiveEnergy(int max,boolean sim){if(onReceive!=null)onReceive.run(); int n=Math.min(max,receiveLimit);if(!receive)return 0;if(!sim)energy+=n;return n;}
   public int extractEnergy(int max,boolean sim){reads++;if(onExtract!=null)onExtract.run(); int n=Math.min(energy,Math.min(max,extractLimit));if(!extract)return 0;if(!sim)energy-=n;return n;}
   public int getEnergyStored(){return energy;} public int getMaxEnergyStored(){return 100000;}
   public boolean canExtract(){return extract;} public boolean canReceive(){return receive;}
 }
 static IEnergyStorage cap(TileEntity t,EnumFacing side){return t.getCapability(CapabilityEnergy.ENERGY,side);}
 static void testCablePull() throws Exception {
   FakeWorld w=world(false); Cable c=new Cable(); Battery b=new Battery(); b.energy=500;
   Endpoint consumer=new Endpoint(70,true,true);w.put(O,c);w.put(E,consumer);w.put(W,b);
   IEnergyStorage wire=cap(c,EnumFacing.EAST);ok(wire.canExtract(),"cable extract exposed");
   eq(wire.extractEnergy(120,true),120,"simulate pull");eq(b.energy,500,"sim battery");eq(consumer.energy,70,"sim consumer");
   eq(wire.extractEnergy(120,false),120,"battery to external pull-only consumer");eq(b.energy,380,"battery debited");eq(consumer.energy,70,"consumer not queried as source");eq(consumer.reads,0,"self exclusion");
   eq(cap(c,null).extractEnergy(10,false),0,"unsided pull cannot identify requester");
   // This consumer refuses all pushes and only obtains energy by pulling.
   FakeWorld q=world(false);Cable wire2=new Cable();Battery battery=new Battery();battery.energy=300;
   Endpoint passiveConsumer=new Endpoint(0,false,false);q.put(O,wire2);q.put(W,battery);q.put(E,passiveConsumer);
   battery.update();eq(passiveConsumer.energy,0,"pull-only receiver rejects push");eq(battery.energy,300,"rejected push refunded");
   int n=cap(wire2,EnumFacing.EAST).extractEnergy(90,false);passiveConsumer.energy+=n;
   eq(n,90,"pull-only receiver uses wire");eq(battery.energy+passiveConsumer.energy,300,"pull-only conservation");
 }
 static void testAuto() throws Exception {
   FakeWorld w=world(false);Crusher c=new Crusher();Endpoint src=new Endpoint(350,false,true);w.put(O,c);w.put(E,src);
   c.update();eq(c.energy,100,"direct passive pull 100");eq(src.energy,250,"direct conservation");c.update();eq(c.energy,200,"tick limit resets");eq(src.energy,150,"direct twice");
   c.energy=c.capacity-30;c.update();eq(c.energy,c.capacity,"capacity bound");eq(src.energy,120,"space bound");
   FakeWorld v=world(false);Crusher cr=new Crusher();Cable cable=new Cable();Endpoint ext=new Endpoint(200,false,true);v.put(O,cr);v.put(E,cable);v.put(E.east(),ext);cr.update();eq(cr.energy,100,"cable autopull");eq(ext.energy,100,"cable source debited");
   FakeWorld z=world(false);Battery bat=new Battery();Endpoint only=new Endpoint(200,false,true);z.put(O,bat);z.put(E,only);bat.update();eq(bat.energy,128,"battery passive pull limit");eq(only.energy,72,"battery conservation");
   FakeWorld bw=world(false);Battery byCable=new Battery();Endpoint extBattery=new Endpoint(200,false,true);bw.put(O,byCable);bw.put(E,new Cable());bw.put(E.east(),extBattery);byCable.update();eq(byCable.energy,128,"battery pulls cable");eq(extBattery.energy,72,"battery cable conservation");
   FakeWorld fw=world(false);Furnace furnace=new Furnace();Endpoint fs=new Endpoint(100,false,true);fw.put(O,furnace);fw.put(E,fs);furnace.update();eq(furnace.energy,100,"furnace pulls");eq(fs.energy,0,"furnace source debit");
   FakeWorld pw=world(false);Compressor compressor=new Compressor();Endpoint ps=new Endpoint(100,false,true);pw.put(O,compressor);pw.put(E,ps);compressor.update();eq(compressor.energy,100,"compressor pulls");eq(ps.energy,0,"compressor source debit");
 }
 static void testBatteries() throws Exception {
   FakeWorld w=world(false);Battery a=new Battery(),b=new Battery();a.energy=500;w.put(W,a);w.put(E,b);w.put(O,new Cable());
   b.update();a.update();eq(a.energy,500,"no battery source autopull/push");eq(b.energy,0,"no battery recipient autopull/push");
   FakeWorld d=world(false);Battery x=new Battery(),y=new Battery();x.energy=500;d.put(O,x);d.put(E,y);y.update();x.update();eq(x.energy,500,"direct battery no transfer");eq(y.energy,0,"direct battery no transfer recipient");
   FakeWorld n=world(false);Battery bat=new Battery();bat.energy=250;Crusher cr=new Crusher();n.put(O,cr);n.put(E,new Cable());n.put(E.east(),bat);cr.update();eq(cr.energy,100,"nexus consumer may pull nexus battery");eq(bat.energy,150,"battery debited");
 }
}
