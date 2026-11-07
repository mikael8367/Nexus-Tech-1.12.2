package br.com.mikael8367.nexustech;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.ItemStackHandler;

public final class TileEnergyCable extends AbstractEnergyTile implements ITickable {
    private int enabledFaces = 0x3F;

    public TileEnergyCable() { super(4000, 512, 512); }

    boolean isFaceEnabled(EnumFacing side) {
        return side != null && (enabledFaces & (1 << side.getIndex())) != 0;
    }

    void setFaceEnabled(EnumFacing side, boolean enabled) {
        if (side == null) return;
        int bit = 1 << side.getIndex();
        int updated = enabled ? (enabledFaces | bit) : (enabledFaces & ~bit);
        if (updated == enabledFaces) return;
        enabledFaces = updated;
        markDirty();
        if (world != null && !world.isRemote) {
            world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
        }
    }

    @Override public boolean hasCapability(net.minecraftforge.common.capabilities.Capability<?> cap, EnumFacing side) {
        if (cap == net.minecraftforge.energy.CapabilityEnergy.ENERGY && side != null && !isFaceEnabled(side)) return false;
        return super.hasCapability(cap, side);
    }

    @Override public <T> T getCapability(net.minecraftforge.common.capabilities.Capability<T> cap, EnumFacing side) {
        if (cap == net.minecraftforge.energy.CapabilityEnergy.ENERGY && side != null && !isFaceEnabled(side)) return null;
        return super.getCapability(cap, side);
    }

    @Override public void update() {
        if (world == null || world.isRemote) return;
        EnergyTransfer.pull(this, 512);
        EnergyTransfer.push(this, 512);
        syncIfChanged();
    }

    @Override protected long syncState() {
        return (((long) enabledFaces) << 32) | (energy.getEnergyStored() & 0xFFFFFFFFL);
    }

    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        tag.setInteger("EnabledFaces", enabledFaces);
        return super.writeToNBT(tag);
    }

    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        enabledFaces = tag.hasKey("EnabledFaces") ? (tag.getInteger("EnabledFaces") & 0x3F) : 0x3F;
    }
}
