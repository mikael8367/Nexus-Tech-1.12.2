package com.mikael8367.nexustech.experiment.tenmachines;
import java.lang.reflect.Method;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.items.IItemHandler;
import com.mikael8367.nexustech.techmachines.TechMachineTile;
/** Simulated reentrant drain at the first markDirty during completion. */
public final class ActiveMachineReentryProbe {
 public static void main(String[] args)throws Exception{
  Bootstrap.register();TenMachinesTest.caps();
  TenMachinesTest.FakeWorld w=TenMachinesTest.world(false);TechMachineTile.Sawmill t=new TechMachineTile.Sawmill();w.tile=t;t.setWorld(w);t.setPos(TenMachinesTest.POS);
  IItemHandler inv=t.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY,EnumFacing.UP);
  inv.insertItem(0,new ItemStack(Blocks.LOG),false);for(int n=0;n<2;n++)t.getCapability(CapabilityEnergy.ENERGY,EnumFacing.DOWN).receiveEnergy(800,false);
  for(int n=0;n<79;n++)t.update();
  Method drain=TechMachineTile.class.getDeclaredMethod("drain",int.class);drain.setAccessible(true);ItemStack[] taken=new ItemStack[2];
  w.onDirty=()->{try{taken[0]=(ItemStack)drain.invoke(t,0);taken[1]=(ItemStack)drain.invoke(t,1);}catch(Exception e){throw new RuntimeException(e);}};
  t.update();
  TenMachinesTest.eq(taken[0].getCount(),1,"input recovered in callback");TenMachinesTest.eq(taken[1].getCount(),6,"output recovered simultaneously");
  Bootstrap.printToSYSOUT("REPRODUCED active sawmill: 1 log and 6 planks drained on reentrant callback");
 }
}
