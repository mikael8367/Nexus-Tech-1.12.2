package com.mikael8367.nexustech.nuclear;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.block.state.IBlockState;
import net.minecraft.world.World;
/** Passive ownership marker, never ticks and holds no items or energy. */
public final class NuclearPart extends TileEntity {
    BlockPos owner;
    void bind(BlockPos p) { if(!p.equals(owner)) { owner=p.toImmutable(); markDirty(); } }
    void unbind(BlockPos p) { if(p.equals(owner)) { owner=null; markDirty(); } }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound n) { super.writeToNBT(n); if(owner!=null){ n.setBoolean("Bound",true);n.setLong("Owner",owner.toLong()); } return n; }
    @Override public void readFromNBT(NBTTagCompound n) { super.readFromNBT(n); owner=n.getBoolean("Bound")?BlockPos.fromLong(n.getLong("Owner")):null; }
    @Override public boolean shouldRefresh(World w,BlockPos p,IBlockState oldState,IBlockState newState) { return oldState.getBlock()!=newState.getBlock(); }
}
