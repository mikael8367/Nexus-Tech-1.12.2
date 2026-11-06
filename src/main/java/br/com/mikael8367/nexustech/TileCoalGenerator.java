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
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.ItemStackHandler;

public final class TileCoalGenerator extends AbstractEnergyTile implements ITickable {
    static final int FE_PER_TICK = 10;
    static final int COAL_TICKS = 1600;
    static final int COAL_BLOCK_TICKS = 16000;
    private static final int MAX_FUEL_TICKS = 32000;
    private int fuelTicks;
    public TileCoalGenerator() { super(32000, 0, 40); }
    int getFuelTicks() { return fuelTicks; }
    static int fuelTicksFor(ItemStack fuel) {
        if (fuel == null || fuel.isEmpty()) return 0;
        if (fuel.getItem() == Items.COAL && (fuel.getMetadata() == 0 || fuel.getMetadata() == 1)) return COAL_TICKS;
        if (fuel.getItem() == Item.getItemFromBlock(Blocks.COAL_BLOCK)) return COAL_BLOCK_TICKS;
        return 0;
    }
    boolean addFuel(ItemStack fuel) {
        if (world == null || world.isRemote) return false;
        int ticks = fuelTicksFor(fuel);
        if (ticks == 0 || ticks > MAX_FUEL_TICKS - fuelTicks) return false;
        fuelTicks += ticks;
        markDirty();
        return true;
    }
    @Override public void update() {
        if (world == null || world.isRemote) return;
        if (fuelTicks > 0 && energy.getCapacity() - energy.getEnergyStored() >= FE_PER_TICK) {
            energy.addInternal(FE_PER_TICK);
            fuelTicks--;
        }
        EnergyTransfer.push(this, 40);
        syncIfChanged();
    }
    @Override protected long syncState() { return ((long) fuelTicks << 32) | (energy.getEnergyStored() & 0xFFFFFFFFL); }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        tag.setInteger("FuelTicks", fuelTicks);
        return super.writeToNBT(tag);
    }
    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        fuelTicks = Math.max(0, Math.min(MAX_FUEL_TICKS, tag.getInteger("FuelTicks")));
    }
}
