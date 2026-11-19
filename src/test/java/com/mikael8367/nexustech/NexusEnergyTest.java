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

/** Standalone integration checks; run main with assertions. */
public final class NexusEnergyTest extends NexusEnergyTestSupport {
 static void testBidirectionalBattery() throws Exception {
   // Direct and cable, including ticks after the external buffer is empty.
   for (boolean viaCable : new boolean[]{false,true}) {
     FakeWorld w=world(false); Battery battery=new Battery(); Endpoint source=new Endpoint(500,true,true);
     w.put(O,battery); if(viaCable){w.put(E,new Cable());w.put(E.east(),source);}else w.put(E,source);
     for(int tick=1;tick<=7;tick++) {
       battery.update(); eq(battery.energy,Math.min(500,tick*128),"battery accumulates "+viaCable+" tick "+tick);
       eq(battery.energy+source.energy,500,"bidirectional conservation tick "+tick);
     }
   }
   // Simultaneous charging and supply: only the receive-only consumer is pushed.
   FakeWorld w=world(false); Battery b=new Battery(); Endpoint src=new Endpoint(600,true,true);
   Endpoint sink=new Endpoint(0,true,false);sink.receiveLimit=40;w.put(O,b);w.put(E,src);w.put(W,sink);
   for(int tick=1;tick<=8;tick++) { b.update(); eq(b.energy+src.energy+sink.energy,600,"simultaneous conservation"); }
   eq(src.energy,0,"source remains empty after charge");eq(sink.energy,320,"consumer receives each tick");eq(b.energy,280,"battery keeps remaining FE");
   // A bidirectional consumer can actively pull despite the conservative push policy.
   eq(cap(b,EnumFacing.WEST).extractEnergy(60,false),60,"bidirectional consumer can pull");
   sink.energy+=60;eq(b.energy+src.energy+sink.energy,600,"active pull conservation");
   // Extraction may be on a different face from reception.
   FakeWorld sided=world(false);Battery sb=new Battery();sb.energy=100;
   Endpoint twoFaces=new Endpoint(0,true,true) {
     public <T>T getCapability(Capability<T> capability,EnumFacing face) {
       if(capability!=CapabilityEnergy.ENERGY) return null;
       if(face==EnumFacing.WEST) return capability.cast((T)new IEnergyStorage(){
         public int receiveEnergy(int n,boolean sim){return twoReceive(n,sim);}
         public int extractEnergy(int n,boolean sim){return 0;}
         public int getEnergyStored(){return energy;}public int getMaxEnergyStored(){return 100000;}
         public boolean canExtract(){return false;} public boolean canReceive(){return true;}
       });
       return super.getCapability(capability,face);
     }
     int twoReceive(int n,boolean sim){return super.receiveEnergy(n,sim);}
   };
   sided.put(O,sb);sided.put(E,twoFaces);sb.update();eq(sb.energy,100,"different output face not pushed");eq(twoFaces.energy,0,"sided endpoint excluded");
 }
 static void testCycleAndUnloaded() throws Exception {
   FakeWorld w=world(false);Cable c=new Cable();w.put(O,c);w.put(O.north(),new Cable());w.put(O.north().east(),new Cable());w.put(E,new Cable());
   Endpoint s=new Endpoint(200,false,true);w.put(E.east(),s);w.unloaded.add(O.south());
   eq(cap(c,EnumFacing.WEST).extractEnergy(150,true),150,"cycle simulate");eq(s.energy,200,"cycle sim unchanged");eq(s.reads,1,"endpoint once despite cycle");
   eq(cap(c,EnumFacing.WEST).extractEnergy(150,false),150,"cycle pull");eq(s.energy,50,"cycle conservation");eq(w.forbiddenQueries,0,"no unloaded access");
   FakeWorld no=world(false);Cable unloaded=new Cable();no.put(O,unloaded);no.unloaded.add(O);eq(cap(unloaded,EnumFacing.WEST).extractEnergy(50,false),0,"unloaded origin");
   FakeWorld off=world(false);Battery removed=new Battery();removed.energy=40;off.put(O,removed);off.unloaded.add(O);
   eq(cap(removed,EnumFacing.EAST).extractEnergy(10,false),0,"unloaded storage inert");removed.update();eq(removed.energy,40,"unloaded tick inert");
 }
 static void testReentry() throws Exception {
   FakeWorld w=world(false);Generator g=new Generator();g.energy=200;Endpoint receiver=new Endpoint(0,true,false);receiver.receiveLimit=60;w.put(O,g);w.put(E,receiver);
   receiver.onReceive=()->{eq(cap(g,EnumFacing.EAST).extractEnergy(100,false),0,"reserved sender cannot extract reentrantly");eq(cap(g,EnumFacing.EAST).receiveEnergy(100,false),0,"sender cannot receive reentrantly");eq(EnergyNetwork.pull(w,O,O,10,false,true),0,"network pull reentry blocked");g.update();};
   g.update();eq(g.energy,140,"restore unaccepted reservation");eq(receiver.energy,60,"partial receive");eq(g.energy+receiver.energy,200,"push conservation");
   FakeWorld p=world(false);Crusher cr=new Crusher();Endpoint src=new Endpoint(150,false,true);p.put(O,cr);p.put(E,src);
   src.onExtract=()->{eq(cap(cr,EnumFacing.EAST).receiveEnergy(100,false),0,"pull recipient locked");eq(EnergyNetwork.send(p,O,O,10,false,false),0,"send blocked during pull");cr.update();};
   cr.update();eq(cr.energy,100,"no pull duplication");eq(src.energy,50,"source debit on reentrant pull");
 }
 static void testSimulationAndClient() throws Exception {
   FakeWorld w=world(false);Cable c=new Cable();Endpoint r=new Endpoint(0,true,false);w.put(O,c);w.put(E,r);
   eq(cap(c,EnumFacing.WEST).receiveEnergy(80,true),80,"simulate push");eq(r.energy,0,"simulate receiver unchanged");
   eq(cap(c,EnumFacing.WEST).receiveEnergy(80,false),80,"push");eq(r.energy,80,"actual push");
   FakeWorld client=world(true);Cable cc=new Cable();Battery b=new Battery();b.energy=300;client.put(O,cc);client.put(E,b);
   eq(cap(cc,EnumFacing.WEST).extractEnergy(50,false),0,"client cable pull");eq(cap(cc,EnumFacing.WEST).receiveEnergy(50,false),0,"client cable push");eq(cap(b,EnumFacing.WEST).extractEnergy(50,false),0,"client storage extract");b.update();eq(b.energy,300,"client tick inert");
 }
 public static void main(String[] args) throws Exception {Bootstrap.register();setupCapability();testCablePull();testAuto();testBatteries();testBidirectionalBattery();testCycleAndUnloaded();testReentry();testSimulationAndClient();Bootstrap.printToSYSOUT("PASS seven FE integration suites");}
}
