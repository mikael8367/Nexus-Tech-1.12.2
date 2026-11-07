package br.com.mikael8367.nexustech;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.ItemStackHandler;

/** High-output uranium reactor with persistent fuel and passive heat management. */
public final class TileNuclearReactorCore extends AbstractEnergyTile implements ITickable {
    static final int FE_PER_TICK = 4096;
    static final int FUEL_TICKS = 24000;
    static final int HEAT_PER_TICK = 5;
    static final int COOLING_PER_TICK = 2;
    static final int MAX_HEAT = 20000;
    private static final int PUSH_PER_TICK = 32768;

    private int fuelTicks;
    private int heat;
    private boolean isExploding;

    private final ItemStackHandler inventory = new ValidatedItemHandler(1) {
        @Override protected void onContentsChanged(int slot) { markDirty(); }
        @Override public int getSlotLimit(int slot) { return 1; }
        @Override public boolean isItemValid(int slot, ItemStack stack) {
            return slot == 0 && stack != null && !stack.isEmpty()
                    && stack.getItem() instanceof ItemFuelRod;
        }
        /** Fuel is consumed only by the reactor; hoppers cannot extract it. */
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }
    };

    public TileNuclearReactorCore() {
        super(50000000, 0, 32768);
    }

    public int getFuelTicks() { return fuelTicks; }
    public int getHeat() { return heat; }
    public int getHeatLimit() { return MAX_HEAT; }
    public boolean isExploding() { return isExploding; }

    /** Inserts exactly one fuel rod. The caller shrinks the held stack only on success. */
    public boolean insertFuel(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof ItemFuelRod)) return false;
        ItemStack one = stack.copy();
        one.setCount(1);
        return inventory.insertItem(0, one, false).isEmpty();
    }

    @Override public void update() {
        if (world == null || world.isRemote || isExploding) return;

        if (fuelTicks <= 0) {
            ItemStack stored = inventory.getStackInSlot(0);
            if (!stored.isEmpty() && stored.getItem() instanceof ItemFuelRod) {
                ItemStack consumed = stored.copy();
                consumed.shrink(1);
                inventory.setStackInSlot(0, consumed.isEmpty() ? ItemStack.EMPTY : consumed);
                fuelTicks = FUEL_TICKS;
                markDirty();
            }
        }

        if (fuelTicks > 0) {
            if (energy.getEnergyStored() < energy.getCapacity()) {
                energy.addInternal(FE_PER_TICK);
            }
            fuelTicks--;
            heat += HEAT_PER_TICK;
            markDirty();
        }

        heat = Math.max(0, heat - COOLING_PER_TICK);
        if (heat >= MAX_HEAT) {
            explode();
            return;
        }

        EnergyTransfer.push(this, PUSH_PER_TICK);
        syncIfChanged();
    }

    private void explode() {
        if (world == null || world.isRemote || isExploding) return;
        isExploding = true;
        markDirty();
        BlockPos reactorPos = pos;
        world.createExplosion(null, reactorPos.getX() + 0.5D, reactorPos.getY() + 0.5D,
                reactorPos.getZ() + 0.5D, 12.0F, true);
        if (world.getTileEntity(reactorPos) == this) {
            world.setBlockToAir(reactorPos);
        }
    }

    void dropContents() {
        if (world == null || world.isRemote) return;
        ItemStack stack = inventory.getStackInSlot(0);
        if (!stack.isEmpty()) {
            MachineItems.drop(world, pos, stack.copy());
            inventory.setStackInSlot(0, ItemStack.EMPTY);
        }
    }

    @Override protected long syncState() {
        long state = energy.getEnergyStored();
        state = state * 31L + fuelTicks;
        state = state * 31L + heat;
        return state * 31L + (isExploding ? 1L : 0L);
    }

    @Override public boolean hasCapability(net.minecraftforge.common.capabilities.Capability<?> capability, EnumFacing side) {
        return capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY || super.hasCapability(capability, side);
    }

    @Override @SuppressWarnings("unchecked")
    public <T> T getCapability(net.minecraftforge.common.capabilities.Capability<T> capability, EnumFacing side) {
        return capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY ? (T) inventory : super.getCapability(capability, side);
    }

    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setInteger("FuelTicks", fuelTicks);
        tag.setInteger("Heat", heat);
        tag.setBoolean("IsExploding", isExploding);
        tag.setTag("Inventory", inventory.serializeNBT());
        return tag;
    }

    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        fuelTicks = Math.max(0, Math.min(FUEL_TICKS, tag.getInteger("FuelTicks")));
        heat = Math.max(0, Math.min(MAX_HEAT, tag.getInteger("Heat")));
        isExploding = tag.getBoolean("IsExploding");
        if (tag.hasKey("Inventory")) inventory.deserializeNBT(tag.getCompoundTag("Inventory"));
    }
}
