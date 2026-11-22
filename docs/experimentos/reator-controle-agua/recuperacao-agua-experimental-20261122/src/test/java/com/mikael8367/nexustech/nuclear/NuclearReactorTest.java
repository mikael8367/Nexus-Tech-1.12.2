package com.mikael8367.nexustech.nuclear;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.Callable;
import net.minecraft.block.BlockHorizontal;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import sun.misc.Unsafe;

/** Standalone checks exercise the actual tile, FE and structure using an isolated world. */
public final class NuclearReactorTest {
 static final BlockPos P=new BlockPos(0,64,0);
 static void ok(boolean b,String s){if(!b)throw new AssertionError(s);}
 static void eq(int a,int b,String s){if(a!=b)throw new AssertionError(s+" got "+a+" expected "+b);}
 static class TestWorld extends World {
  Map<BlockPos,IBlockState> states; Map<BlockPos,TileEntity> tiles; Set<BlockPos> missing; long time; int dirty, changes;
  TestWorld(){super(null,null,null,null,false);}
  protected IChunkProvider createChunkProvider(){return null;} protected boolean isChunkLoaded(int x,int z,boolean allow){return true;}
  public long getTotalWorldTime(){return time;}
  public boolean isBlockLoaded(BlockPos p){return !missing.contains(p);}
  public IBlockState getBlockState(BlockPos p){if(missing.contains(p))throw new AssertionError("unloaded state query");IBlockState s=states.get(p);return s==null?Blocks.AIR.getDefaultState():s;}
  public TileEntity getTileEntity(BlockPos p){if(missing.contains(p))throw new AssertionError("unloaded tile query");return tiles.get(p);}
  public boolean setBlockState(BlockPos p,IBlockState s,int flags){states.put(p,s);changes++;return true;}
  public void markChunkDirty(BlockPos p,TileEntity t){dirty++;}
  @Override public void notifyBlockUpdate(BlockPos p,IBlockState oldState,IBlockState newState,int flags){changes++;}
  void put(BlockPos p,IBlockState s,TileEntity t){states.put(p,s);tiles.put(p,t);t.setPos(p);t.setWorld(this);}
 }
 static TestWorld world()throws Exception{Field f=Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);Unsafe u=(Unsafe)f.get(null);TestWorld w=(TestWorld)u.allocateInstance(TestWorld.class);w.states=new HashMap<>();w.tiles=new HashMap<>();w.missing=new HashSet<>();return w;}
 @SuppressWarnings("unchecked") static <T> Capability<T> capability(String name)throws Exception{Constructor<?> c=Capability.class.getDeclaredConstructors()[0];c.setAccessible(true);return (Capability<T>)c.newInstance(name,null,(Callable<T>)()->null);}
 static NuclearController build(TestWorld w,EnumFacing face){NuclearController c=new NuclearController();w.put(P,NuclearRegistry.CONTROLLER.getDefaultState().withProperty(BlockHorizontal.FACING,face),c);for(int x=-1;x<=1;x++)for(int y=0;y<5;y++)for(int d=0;d<3;d++){if(x==0&&y==0&&d==0)continue;BlockPos q=NuclearController.at(P,face,x,y,d);w.put(q,(x==0&&y==2&&d==1?NuclearRegistry.CORE:NuclearRegistry.CASING).getDefaultState(),new NuclearPart());}return c;}
 public static void main(String[]args)throws Exception{
  Bootstrap.register(); TileEntity.register("nexustech:nuclear_controller", NuclearController.class); TileEntity.register("nexustech:nuclear_part", NuclearPart.class); CapabilityEnergy.ENERGY=capability("test:energy");CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY=capability("test:fluid");CapabilityItemHandler.ITEM_HANDLER_CAPABILITY=capability("test:item");
  for(EnumFacing f:EnumFacing.Plane.HORIZONTAL){TestWorld w=world();NuclearController c=build(w,f);eq(c.validateStructure(),1,"45 block valid "+f);BlockPos q=NuclearController.at(P,f,1,4,2);w.states.put(q,Blocks.AIR.getDefaultState());eq(c.validateStructure(),0,"missing block "+f);w.missing.add(q);eq(c.validateStructure(),-1,"chunk pause "+f);}
  TestWorld w=world();NuclearController c=build(w,EnumFacing.NORTH);c.inventory.setStackInSlot(0,new ItemStack(NuclearRegistry.FUEL));c.setEnabled(true);
  IFluidHandler fluid=c.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY,null);eq(fluid.fill(new FluidStack(FluidRegistry.WATER,3000),true),3000,"fill");c.update();eq(c.energy,200,"generation");eq(c.fuelTicks,2399,"finite fuel");eq(c.water(),2999,"finite water");eq(c.temperature,0,"cooling");
  NBTTagCompound n=c.writeToNBT(new NBTTagCompound());NuclearController copy=new NuclearController();copy.readFromNBT(n);eq(copy.energy,200,"energy nbt");eq(copy.water(),2999,"water nbt");eq(copy.fuelTicks,2399,"fuel nbt");
  BlockPos missing=NuclearController.at(P,EnumFacing.NORTH,1,4,2);w.missing.add(missing);int e=c.energy,water=c.water(),fuel=c.fuelTicks;c.update();eq(c.energy,e,"paused energy");eq(c.water(),water,"paused water");eq(c.fuelTicks,fuel,"paused fuel");ok(c.getCapability(CapabilityEnergy.ENERGY,null)==null,"paused output capability absent");w.missing.clear();c.setEnabled(true);
  IFluidHandler f2=c.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY,null);ok(f2.drain(1,true)==null,"external draining forbidden");
  c.energy=0;c.temperature=998;c.inventory.setStackInSlot(0,new ItemStack(NuclearRegistry.FUEL,2));for(int i=0;i<3000 && c.water()>0;i++)c.update();eq(c.water(),0,"water exhausted");c.temperature=998;c.update();ok(c.scrammed&&!c.enabled,"overheat scram");c.setEnabled(true);ok(!c.enabled,"hot restart refused");c.temperature=400;c.setEnabled(true);ok(c.enabled&&!c.scrammed,"manual cooled restart");
  c.energy=0;c.temperature=0;c.fuelTicks=2;c.inventory.setStackInSlot(0,ItemStack.EMPTY);c.update();c.update();int end=c.energy;c.update();eq(c.fuelTicks,0,"fuel exhausted");eq(c.energy,end,"no free fuel");
  IEnergyStorage fe=c.getCapability(CapabilityEnergy.ENERGY,null);int before=c.energy;int simulated=fe.extractEnergy(100,true);eq(c.energy,before,"FE simulation");eq(fe.extractEnergy(100,false),simulated,"FE extraction");eq(c.energy,before-simulated,"FE conservation");
  System.out.println("NuclearReactorTest: PASS");
 }
}
