package br.com.mikael8367.nexustech;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.ItemStackHandler;

public final class TileCoalGenerator extends AbstractEnergyTile implements ITickable {
    static final int FE_PER_TICK = 10;
    static final int COAL_TICKS = 1600;
    static final int COAL_BLOCK_TICKS = 16000;
    private static final int MAX_FUEL_TICKS = 32000;
    private int fuelTicks;

    /**
     * Input-only fuel inventory. Hoppers and other item handlers may insert fuel,
     * but cannot extract it before the generator consumes it.
     */
    private final ItemStackHandler inventory = new ValidatedItemHandler(1) {
        @Override
        protected void onContentsChanged(int slot) {
            markDirty();
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot == 0 && fuelTicksFor(stack) > 0;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }
    };

    public TileCoalGenerator() {
        super(32000, 0, 40);
    }

    int getFuelTicks() {
        return fuelTicks;
    }

    static int fuelTicksFor(ItemStack fuel) {
        if (fuel == null || fuel.isEmpty()) return 0;
        if (fuel.getItem() == Items.COAL && (fuel.getMetadata() == 0 || fuel.getMetadata() == 1)) {
            return COAL_TICKS;
        }
        if (fuel.getItem() == Item.getItemFromBlock(Blocks.COAL_BLOCK)) {
            return COAL_BLOCK_TICKS;
        }
        return 0;
    }

    /**
     * Preserves the existing manual block interaction API. Exactly one item is
     * inserted; the caller remains responsible for shrinking the player's stack.
     */
    boolean addFuel(ItemStack fuel) {
        if (world == null || world.isRemote || fuel == null || fuel.isEmpty() || fuelTicksFor(fuel) <= 0) {
            return false;
        }
        ItemStack one = fuel.copy();
        one.setCount(1);
        return inventory.insertItem(0, one, false).isEmpty();
    }

    /** Drops stored fuel when the block is broken, without leaving it in the tile. */
    void dropContents() {
        if (world == null || world.isRemote) return;
        ItemStack stored = inventory.getStackInSlot(0);
        if (!stored.isEmpty()) {
            MachineItems.drop(world, pos, stored.copy());
            inventory.setStackInSlot(0, ItemStack.EMPTY);
        }
    }

    @Override
    public void update() {
        if (world == null || world.isRemote) return;

        // Do not consume fuel while the energy buffer cannot accept a full generation tick.
        if (energy.getCapacity() - energy.getEnergyStored() >= FE_PER_TICK) {
            if (fuelTicks <= 0) {
                ItemStack stored = inventory.getStackInSlot(0);
                int burnTime = fuelTicksFor(stored);
                if (burnTime > 0) {
                    ItemStack remainder = stored.copy();
                    remainder.shrink(1);
                    inventory.setStackInSlot(0, remainder);
                    fuelTicks = burnTime;
                    markDirty();
                }
            }

            if (fuelTicks > 0 && energy.addInternal(FE_PER_TICK) == FE_PER_TICK) {
                fuelTicks--;
            }
        }

        EnergyTransfer.push(this, 40);
        syncIfChanged();
    }

    @Override
    protected long syncState() {
        return ((long) fuelTicks << 32) | (energy.getEnergyStored() & 0xFFFFFFFFL);
    }

    @Override
    public boolean hasCapability(net.minecraftforge.common.capabilities.Capability<?> capability, EnumFacing side) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) return true;
        return super.hasCapability(capability, side);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getCapability(net.minecraftforge.common.capabilities.Capability<T> capability, EnumFacing side) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) return (T) inventory;
        return super.getCapability(capability, side);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setInteger("FuelTicks", fuelTicks);
        tag.setTag("Inventory", inventory.serializeNBT());
        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        fuelTicks = Math.max(0, Math.min(MAX_FUEL_TICKS, tag.getInteger("FuelTicks")));
        if (tag.hasKey("Inventory")) {
            inventory.deserializeNBT(tag.getCompoundTag("Inventory"));
        }
    }
}
