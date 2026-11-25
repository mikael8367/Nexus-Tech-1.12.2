package com.mikael8367.nexustech.experiment.tenmachines;
import static com.mikael8367.nexustech.experiment.tenmachines.TenMachinesTest.*;
import java.lang.reflect.Field;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldProviderSurface;
import net.minecraft.world.chunk.IChunkProvider;
import sun.misc.Unsafe;
/** Negative probe: direct requests bypass player/explosion guard. Expected FAIL. */
public final class SpawnFailureProbe {
 static final class W extends World {
  ProcessingTile t; boolean throwsSpawn; int attempted;
  private W(){super(null,null,null,null,false);}
  protected IChunkProvider createChunkProvider(){return null;}
  protected boolean isChunkLoaded(int x,int z,boolean allow){return true;}
  public boolean isBlockLoaded(BlockPos p){return true;}
  public TileEntity getTileEntity(BlockPos p){return t;}
  public IBlockState getBlockState(BlockPos p){return Blocks.AIR.getDefaultState();}
  public void markChunkDirty(BlockPos p,TileEntity tile){}
  public boolean spawnEntity(Entity e){attempted++;if(throwsSpawn)throw new IllegalStateException("simulated spawn exception");return false;}
 }
 static W newWorld(boolean throwing)throws Exception{
  Field f=Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);Unsafe u=(Unsafe)f.get(null);W w=(W)u.allocateInstance(W.class);w.throwsSpawn=throwing;u.putObject(w,u.objectFieldOffset(World.class.getField("provider")),new WorldProviderSurface());return w;
 }
 public static void main(String[] args)throws Exception{
  Bootstrap.register();caps();ExperimentalRegistry.registerTiles();W w=newWorld(args[0].equals("throw"));ProcessingTile t=(ProcessingTile)ExperimentalRegistry.createTile(MachineKind.BONE_MILL);w.t=t;t.setWorld(w);t.setPos(POS);inv(t).insertItem(0,new ItemStack(Items.BONE,5),false);
  ProcessingBlock b=(ProcessingBlock)ExperimentalRegistry.blocks().get(MachineKind.BONE_MILL);
  ok(!b.removedByPlayer(b.getDefaultState(),w,POS,null,false),"player removal rejected before spawn");b.onBlockExploded(w,POS,null);eq(w.attempted,0,"neither spawn false nor exception invoked during guarded removal");
  boolean threw=false;try{t.requestDrops(w,POS);}catch(IllegalStateException e){threw=true;}int count=t.writeToNBT(new net.minecraft.nbt.NBTTagCompound()).getTagList("Inventory",10).tagCount();Bootstrap.printToSYSOUT("OBSERVED direct request: spawn attempts="+w.attempted+", exception="+threw+", serialized slots="+count);t.requestDrops(w,POS);eq(w.attempted,1,"repeat does not spawn again");
  ok(count>0,"FAIL: direct removal loses all serialized items when spawn fails");
 }
}
