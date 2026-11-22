package com.mikael8367.nexustech.nuclear;

import com.mikael8367.nexustech.ores.OreRegistry;
import com.mikael8367.nexustech.ores.OreType;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

/** Tests server-side fuel transitions with the actual controller and inventory. */
public final class NuclearUraniumTest {
 static void ok(boolean b,String s){NuclearReactorTest.ok(b,s);}
 static void eq(int a,int b,String s){NuclearReactorTest.eq(a,b,s);}
 static ItemStack uranium(int n){return new ItemStack(OreRegistry.INGOTS.get(OreType.URANIUM),n);}
 static void transitions()throws Exception{
  NuclearReactorTest.TestWorld w=NuclearReactorTest.world();NuclearController c=NuclearReactorTest.build(w,EnumFacing.NORTH);
  c.setEnabled(true);ok(!c.enabled,"empty reactor cannot start");c.update();eq(c.energy,0,"empty makes no energy");
  IItemHandler auto=c.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY,null);
  ok(!auto.insertItem(0,new ItemStack(Items.IRON_INGOT),false).isEmpty(),"reject iron");
  ok(auto.insertItem(0,uranium(2),true).isEmpty(),"simulate uranium accepted");ok(c.inventory.getStackInSlot(0).isEmpty(),"simulate unchanged");
  ok(auto.insertItem(0,uranium(2),false).isEmpty(),"uranium inserted");
  c.energy=NuclearController.CAPACITY;c.setEnabled(true);ok(c.enabled,"uranium permits start");c.update();
  eq(c.inventory.getStackInSlot(0).getCount(),2,"full battery does not consume");eq(c.fuelTicks,0,"full battery no fuel activation");
  c.energy=0;c.update();eq(c.inventory.getStackInSlot(0).getCount(),1,"consume exactly one");eq(c.fuelTicks,2399,"one burn tick");eq(c.energy,200,"one generation tick");
  c.setEnabled(false);c.update();eq(c.fuelTicks,2399,"manual stop pauses fuel");
  c.inventory.setStackInSlot(0,ItemStack.EMPTY);c.setEnabled(true);ok(c.enabled,"remaining burn permits restart");
  NBTTagCompound saved=c.writeToNBT(new NBTTagCompound());NuclearController loaded=new NuclearController();loaded.readFromNBT(saved);eq(loaded.fuelTicks,2399,"remaining burn saved");ok(!loaded.enabled && loaded.scrammed,"loaded running reactor requires reset");
  BlockPos q=NuclearController.at(NuclearReactorTest.P,EnumFacing.NORTH,1,4,2);w.missing.add(q);c.update();eq(c.fuelTicks,2399,"missing chunk pauses burn");ok(!c.enabled && c.scrammed,"missing chunk trips");w.missing.clear();c.setEnabled(true);ok(c.enabled,"manual reset with remaining burn");
  c.fuelTicks=1;c.update();eq(c.fuelTicks,0,"last tick consumed");ok(!c.enabled,"fuel exhaustion switches off");int e=c.energy;c.update();eq(c.energy,e,"no free generation after exhaustion");
  c.inventory.setStackInSlot(0,uranium(1));c.update();ok(!c.enabled,"refill does not automatically restart");c.setEnabled(true);c.update();eq(c.fuelTicks,2399,"refill and manual restart");
 }
 static void legacyAndBoundary()throws Exception{
  NuclearReactorTest.TestWorld w=NuclearReactorTest.world();NuclearController c=NuclearReactorTest.build(w,EnumFacing.SOUTH);
  c.inventory.setStackInSlot(0,new ItemStack(NuclearRegistry.FUEL));c.setEnabled(true);c.update();eq(c.fuelTicks,2399,"old nuclear fuel works");
  c.fuelTicks=1;c.inventory.setStackInSlot(0,uranium(1));c.update();ok(c.enabled,"queued replacement keeps reactor armed");eq(c.inventory.getStackInSlot(0).getCount(),1,"replacement not consumed early");c.update();eq(c.fuelTicks,2399,"replacement consumed next generating tick");ok(c.inventory.getStackInSlot(0).isEmpty(),"one replacement consumed");
  c.fuelTicks=0;c.inventory.setStackInSlot(0,new ItemStack(Items.IRON_INGOT));c.update();ok(!c.enabled,"invalid saved slot cannot fuel reactor");eq(c.inventory.getStackInSlot(0).getCount(),1,"invalid saved item preserved");
 }
 public static void main(String[]args)throws Exception{NuclearGuiAccessTest.main(args);transitions();legacyAndBoundary();System.out.println("NuclearUraniumTest: PASS");}
}
