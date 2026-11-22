package com.mikael8367.nexustech.fluidnetwork.pipe;

import javax.annotation.Nullable;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;

/** Unregistered, receive-only energy placeholder. No ticking or fluid transfers. Server-thread only. */
public final class FluidPumpTile extends TileEntity {
    public static final int CAPACITY = 10000;
    public static final int MAX_RECEIVE = 200;
    private static final String ENERGY_KEY = "Energy";
    private int energy;
    // Forge may silently recreate a missing tile during getTileEntity. It must not
    // become a trusted empty pump merely because its constructor ran.
    private boolean receiving, breaking, quarantined = true, rawPresent, fresh = true;
    private NBTBase rawEnergy;

    // Forge's non-vanilla default refreshes on any state change, including FACING.
    @Override public boolean shouldRefresh(World world, BlockPos pos, IBlockState oldState, IBlockState newState) {
        return oldState.getBlock() != newState.getBlock();
    }

    private boolean live() {
        return world != null && !world.isRemote && !isInvalid()
                && world.isBlockLoaded(pos) && world.getTileEntity(pos) == this;
    }
    /** Unknown or missing persisted energy is preserved, never silently replaced. */
    public boolean isEnergyQuarantined() { return quarantined; }

    // Only a genuine placement may initialize a brand-new tile. A tile recreated
    // by Forge on lookup stays quarantined, including across a save/reload.
    void initializeNewPlacement() {
        if (!fresh || receiving || breaking || isInvalid()) return;
        fresh = false;
        quarantined = false;
        markDirty();
    }

    // Removal callbacks must not receive energy or reload NBT midway through a break.
    boolean beginEmptyBreak() {
        if (receiving || breaking || quarantined || isInvalid() || energy != 0) return false;
        breaking = true;
        return true;
    }
    void endEmptyBreak() { breaking = false; }

    private final IEnergyStorage power = new IEnergyStorage() {
        @Override public int receiveEnergy(int amount, boolean simulate) {
            if (receiving || breaking || quarantined || amount <= 0) return 0;
            receiving = true;
            try {
                if (!live()) return 0;
                int accepted = Math.min(amount, Math.min(MAX_RECEIVE, CAPACITY - energy));
                if (simulate || accepted <= 0) return accepted;
                markDirty();
                if (!live() || quarantined) return 0;
                energy += accepted;
                // Re-mark after commit: a callback might have saved/cleared dirty state
                // during the first notification. This is not crash-safe persistence.
                try { markDirty(); }
                catch (RuntimeException failure) {
                    // The caller must observe the committed receipt, not a false failure.
                    java.util.logging.Logger.getLogger(FluidPumpTile.class.getName()).log(
                            java.util.logging.Level.SEVERE, "Pump dirty notification failed after receipt", failure);
                }
                return accepted;
            } finally { receiving = false; }
        }
        @Override public int extractEnergy(int amount, boolean simulate) { return 0; }
        @Override public int getEnergyStored() { return energy; }
        @Override public int getMaxEnergyStored() { return CAPACITY; }
        @Override public boolean canExtract() { return false; }
        @Override public boolean canReceive() { return !quarantined && !breaking; }
    };

    @Override public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing side) {
        return CapabilityEnergy.ENERGY != null && capability == CapabilityEnergy.ENERGY || super.hasCapability(capability, side);
    }
    @Override public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing side) {
        if (CapabilityEnergy.ENERGY != null && capability == CapabilityEnergy.ENERGY) return CapabilityEnergy.ENERGY.cast(power);
        return super.getCapability(capability, side);
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        if (receiving) throw new IllegalStateException("reentrant pump serialization");
        super.writeToNBT(tag);
        if (quarantined) {
            if (rawPresent) tag.setTag(ENERGY_KEY, rawEnergy.copy());
            else tag.removeTag(ENERGY_KEY);
        } else tag.setInteger(ENERGY_KEY, energy);
        return tag;
    }
    @Override public void readFromNBT(NBTTagCompound tag) {
        if (receiving || breaking) throw new IllegalStateException("reentrant pump deserialization");
        super.readFromNBT(tag);
        fresh = false;
        rawPresent = tag.hasKey(ENERGY_KEY);
        rawEnergy = rawPresent ? tag.getTag(ENERGY_KEY).copy() : null;
        energy = 0;
        quarantined = true;
        if (!tag.hasKey(ENERGY_KEY, 3)) return;
        int value = tag.getInteger(ENERGY_KEY);
        if (value < 0 || value > CAPACITY) return;
        energy = value;
        quarantined = false;
        rawEnergy = null;
    }
}
