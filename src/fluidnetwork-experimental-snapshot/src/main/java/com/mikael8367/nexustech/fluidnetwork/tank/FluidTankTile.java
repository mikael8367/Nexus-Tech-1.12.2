package com.mikael8367.nexustech.fluidnetwork.tank;

import javax.annotation.Nullable;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;

/**
 * Deliberately unregistered: no block is safe until all removal paths are addressed.
 * Future integration, only after safety review and in-world tests: register this tile
 * with a stable ResourceLocation, e.g. GameRegistry.registerTileEntity(FluidTankTile.class,
 * new ResourceLocation("nexustech", "fluid_tank")); create a matching block and assets.
 * Ordinary player-break denial alone cannot protect explosion, replacement, commands,
 * automation or other mods. Do not enable a block until every supported removal path
 * is prevented or recoverable, including quarantined data, and worlds are backed up.
 * Do not treat an absent NexusFluidTank tag on read as an empty legacy tank.
 */
public final class FluidTankTile extends TileEntity {
    private final SafeFluidTank tank = new SafeFluidTank(this::serverLive, this::markDirty);
    private volatile boolean chunkUnloaded;

    // Forge otherwise refreshes the tile on any state change of this block.
    @Override public boolean shouldRefresh(World world, BlockPos pos, IBlockState oldState, IBlockState newState) {
        return oldState.getBlock() != newState.getBlock();
    }

    @Override public void onChunkUnload() {
        chunkUnloaded = true;
        super.onChunkUnload();
    }
    @Override public void onLoad() {
        super.onLoad();
        chunkUnloaded = false;
    }

    private boolean serverLive() {
        if (chunkUnloaded || world == null || world.isRemote || isInvalid()) return false;
        MinecraftServer server = world.getMinecraftServer();
        // Never access chunk internals from a foreign capability-caller thread.
        return server != null && server.isCallingFromMinecraftThread() && world.isBlockLoaded(pos, false)
                && world.getTileEntity(pos) == this;
    }
    /** A precondition, NOT a universal block-removal protection. */
    public boolean mayRemoveSafely() { return tank.mayRemove(); }
    public boolean isTankQuarantined() { return tank.isQuarantined(); }

    @Override public boolean hasCapability(Capability<?> cap, @Nullable EnumFacing side) {
        if (cap == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) return serverLive();
        return super.hasCapability(cap, side);
    }
    @Nullable @Override public <T> T getCapability(Capability<T> cap, @Nullable EnumFacing side) {
        if (cap == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY)
            return serverLive() ? CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY.cast(tank) : null;
        return super.getCapability(cap, side);
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag); tank.write(tag); return tag;
    }
    @Override public void readFromNBT(NBTTagCompound tag) {
        // Reject a repeated read before super can mutate position/other tile state.
        tank.read(tag); super.readFromNBT(tag);
    }
}
