package com.mikael8367.nexustech;

import net.minecraft.init.Bootstrap;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.energy.IEnergyStorage;

/** End-to-end checks with real machine logic and a non-loading simulated world. */
public final class NexusEnergyRegressionTest extends NexusEnergyTestSupport {
 static void capacityAwarePeers() throws Exception {
   FakeWorld w=world(false); Cable cable=new Cable(); Endpoint source=new Endpoint(200,false,true);
   w.put(O,cable); w.put(W,source);
   IEnergyStorage wire=cap(cable,EnumFacing.EAST);
   ok(cable.hasCapability(net.minecraftforge.energy.CapabilityEnergy.ENERGY,EnumFacing.EAST),"sided FE capability advertised");
   ok(wire.getEnergyStored()>0,"pulling peer can see available energy");
   int pulled=wire.extractEnergy(Math.min(80,wire.getEnergyStored()),false);
   eq(pulled,80,"capacity-aware pull"); eq(source.energy,120,"actual pull debits source");
   FakeWorld p=world(false);Cable c=new Cable();Crusher sink=new Crusher();p.put(O,c);p.put(E,sink);
   IEnergyStorage receiver=cap(c,EnumFacing.WEST);
   int free=receiver.getMaxEnergyStored()-receiver.getEnergyStored();
   ok(free>0,"pushing peer can see available capacity");
   eq(receiver.receiveEnergy(Math.min(90,free),false),90,"capacity-aware push");eq(sink.energy,90,"actual push credits sink");
   FakeWorld u=world(false);Cable unsided=new Cable();Endpoint peer=new Endpoint(100,true,true);u.put(O,unsided);u.put(E,peer);
   eq(cap(unsided,null).receiveEnergy(50,false),0,"unsided push must not credit its unknown sender");
   eq(peer.energy,100,"unsided sender not self-credited");
 }
 static void sidedPeers() throws Exception {
   for (final EnumFacing face : EnumFacing.VALUES) {
     FakeWorld w=world(false);Cable c=new Cable();
     Endpoint source=new Endpoint(150,false,true) {
       @Override public <T>T getCapability(net.minecraftforge.common.capabilities.Capability<T> capability, EnumFacing side) {
         return side==face.getOpposite() ? super.getCapability(capability,side) : null;
       }
     };
     w.put(O,c);w.put(O.offset(face),source);
     EnumFacing caller=face.getOpposite(); IEnergyStorage wire=cap(c,caller);
     eq(wire.extractEnergy(50,true),50,"sided simulation "+face);eq(source.energy,150,"simulation does not debit");
     eq(wire.extractEnergy(50,false),50,"correct source face "+face);eq(source.energy,100,"actual sided debit");
     FakeWorld push=world(false);Cable cp=new Cable();
     Endpoint sink=new Endpoint(0,true,false) {
       @Override public <T>T getCapability(net.minecraftforge.common.capabilities.Capability<T> capability, EnumFacing side) {
         return side==face.getOpposite() ? super.getCapability(capability,side) : null;
       }
     };
     push.put(O,cp);push.put(O.offset(face),sink);
     IEnergyStorage target=cap(cp,caller);
     eq(target.receiveEnergy(70,true),70,"sided push simulation "+face);eq(sink.energy,0,"no simulated credit");
     eq(target.receiveEnergy(70,false),70,"correct receiver face "+face);eq(sink.energy,70,"actual sided credit");
   }
 }
 static void realMachinePath() throws Exception {
   FakeWorld w=world(false);Generator g=new Generator();Crusher machine=new Crusher();
   w.put(W,g);w.put(O,new Cable());w.put(E,machine);
   g.inventory.setStackInSlot(0,new ItemStack(Items.COAL));
   machine.inventory.setStackInSlot(0,new ItemStack(Blocks.IRON_ORE));
   for(int i=0;i<210;i++){g.update();machine.update();}
   eq(machine.inventory.getStackInSlot(1).getCount(),2,"crusher actually processes iron ore");
   ok(machine.inventory.getStackInSlot(1).getItem()==Items.IRON_INGOT,"correct output");
   eq(g.energy+machine.energy+4000,210*40,"generated FE equals stored plus consumed");
 }
 static void persistenceAndChunks() throws Exception {
   net.minecraft.tileentity.TileEntity.register("test:energy_battery",Battery.class);
   FakeWorld w=world(false);Battery b=new Battery();w.put(O,b);b.energy=73521;
   NBTTagCompound saved=b.writeToNBT(new NBTTagCompound());Battery reloaded=new Battery();reloaded.readFromNBT(saved);
   eq(reloaded.energy,73521,"NBT round trip keeps all 32 energy bits");
   // Window properties carry 16 bits each: reconstruct the displayed 32-bit FE.
   java.lang.reflect.Field unsafeField=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");unsafeField.setAccessible(true);
   sun.misc.Unsafe unsafe=(sun.misc.Unsafe)unsafeField.get(null);
   MachineContainer container=(MachineContainer)unsafe.allocateInstance(MachineContainer.class);
   java.lang.reflect.Field tileField=MachineContainer.class.getDeclaredField("tile");tileField.setAccessible(true);tileField.set(container,reloaded);
   reloaded.energy=0;container.updateProgressBar(0,73521 & 0xffff);container.updateProgressBar(1,73521 >>> 16);
   eq(reloaded.energy,73521,"container reconstructs 32-bit energy");
   FakeWorld p=world(false);Crusher sink=new Crusher();Generator g=new Generator();g.energy=300;
   p.put(W,g);p.put(O,new Cable());p.put(E,sink);p.unloaded.add(O);
   sink.update();g.update();eq(sink.energy,0,"unloaded cable stops transfer");eq(g.energy,300,"unloaded cable preserves source");
   p.unloaded.remove(O);g.update();eq(sink.energy,100,"transfer resumes when loaded");eq(g.energy,200,"debit after reload");eq(p.forbiddenQueries,0,"no forced unloaded lookup");
 }
 public static void main(String[] args) throws Exception {
   Bootstrap.register();setupCapability();capacityAwarePeers();sidedPeers();realMachinePath();persistenceAndChunks();
   Bootstrap.printToSYSOUT("PASS capacity-aware peers, six-sided I/O, real machine path, persistence, GUI sync and chunk regression suites");
 }
}
