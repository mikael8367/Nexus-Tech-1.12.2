package com.mikael8367.nexustech;

import java.lang.reflect.Constructor;
import java.util.concurrent.Callable;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

/** Conservative battery policy and coal-generator hopper regression checks. */
public final class NexusBatteryAndBucketTest extends NexusEnergyTestSupport {
 @SuppressWarnings("unchecked")
 static void setupItems() throws Exception {
   Constructor<?> c=Capability.class.getDeclaredConstructors()[0]; c.setAccessible(true);
   CapabilityItemHandler.ITEM_HANDLER_CAPABILITY=(Capability<IItemHandler>)c.newInstance("test:items",null,(Callable<IItemHandler>)() -> null);
 }
 static void policy() throws Exception {
   // An empty passive bidirectional sink and a drained bidirectional source have
   // the same observable interface. Neither may receive automatic battery push.
   for (boolean viaCable : new boolean[]{false,true}) {
     FakeWorld w=world(false); Battery b=new Battery(); b.energy=300;
     Endpoint peer=new Endpoint(80,true,true);w.put(W,b);
     if (viaCable) {w.put(O,new Cable());w.put(E,peer);} else {w.put(O,peer);}
     for(int tick=0;tick<8;tick++) {
       b.update(); eq(b.energy+peer.energy,380,"battery/peer conservation");
       eq(peer.energy,0,"bidirectional donor must not be refilled across ticks");
     }
     eq(b.energy,380,"donor drained once, no bounce");
   }
   FakeWorld w=world(false); Battery b=new Battery();b.energy=500;Endpoint passive=new Endpoint(0,true,true);
   w.put(W,b);w.put(O,new Cable());w.put(E,passive);b.update();
   eq(passive.energy,0,"passive bidirectional sink not automatically pushed");eq(b.energy,500,"battery unchanged");
   int pulled=cap(w.tiles.get(O),EnumFacing.EAST).extractEnergy(90,false);passive.energy+=pulled;
   eq(pulled,90,"bidirectional sink actively pulls");eq(b.energy+passive.energy,500,"active pull conserves");
   FakeWorld p=world(false); Battery bat=new Battery();bat.energy=300;Endpoint receiveOnly=new Endpoint(0,true,false);
   p.put(W,bat);p.put(O,new Cable());p.put(E,receiveOnly);bat.update();
   eq(receiveOnly.energy,128,"battery pushes receive-only");eq(bat.energy+receiveOnly.energy,300,"push conserves");
   FakeWorld g=world(false);Generator generator=new Generator();generator.energy=200;Endpoint bidirectional=new Endpoint(0,true,true);
   g.put(W,generator);g.put(O,new Cable());g.put(E,bidirectional);generator.update();
   eq(bidirectional.energy,100,"generator can push to bidirectional receiver");eq(generator.energy+bidirectional.energy,200,"generator push conserves");
 }
 static void buckets() throws Exception {
   FakeWorld w=world(false);Generator g=new Generator();w.put(O,g);
   IItemHandler hopper=g.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY,EnumFacing.DOWN);
   ok(hopper!=null,"hopper capability");
   g.inventory.setStackInSlot(0,new ItemStack(Items.COAL));
   ok(hopper.extractItem(0,1,false).isEmpty(),"hopper cannot extract coal fuel");
   g.inventory.setStackInSlot(0,new ItemStack(Items.LAVA_BUCKET));
   ok(hopper.extractItem(0,1,false).isEmpty(),"hopper cannot extract unburnt lava bucket");
   g.update();ok(g.inventory.getStackInSlot(0).getItem()==Items.BUCKET,"burn returns empty bucket");
   ok(hopper.extractItem(0,1,true).getItem()==Items.BUCKET,"simulated extraction returns bucket");
   ok(g.inventory.getStackInSlot(0).getItem()==Items.BUCKET,"simulation preserves bucket");
   ok(hopper.extractItem(0,1,false).getItem()==Items.BUCKET,"hopper extracts empty bucket");
   ok(g.inventory.getStackInSlot(0).isEmpty(),"bucket removed");
   ok(hopper.extractItem(0,1,false).isEmpty(),"empty slot stays empty");
   ok(hopper.insertItem(0,new ItemStack(Items.COAL),false).isEmpty(),"hopper refills fuel slot");
 }
 public static void main(String[] args) throws Exception {
   Bootstrap.register();setupCapability();setupItems();policy();buckets();
   Bootstrap.printToSYSOUT("PASS battery anti-bounce, sided pull/push conservation and hopper bucket regression");
 }
}
