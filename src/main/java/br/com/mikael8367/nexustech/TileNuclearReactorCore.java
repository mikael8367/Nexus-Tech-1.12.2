package br.com.mikael8367.nexustech;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.ItemStackHandler;

/**
 * High-output uranium reactor. Heat is deliberately bounded and persisted;
 * reaching the critical threshold causes an in-game meltdown explosion.
 */
public final class TileNuclearReactorCore extends AbstractEnergyTile implements ITickable {
    static final int FE_PER_TICK = 32768;
    static final int FUEL_TICKS = ItemFuelRod.MAX_BURN_TIME;
    static final int MAX_HEAT = 48000;
    private static final int PASSIVE_COOLING = 2;

    private int fuelTicks;
    private int heat;

    private final ItemStackHandler inventory = new ValidatedItemHandler(1) {
        @Override protected void onContentsChanged(int slot) { markDirty(); }
        @Override public int getSlotLimit(int slot) { return 1; }
        @Override public boolean isItemValid(int slot, ItemStack stack) {
            return slot == 0 && stack != null && !stack.isEmpty()
                    && stack.getItem() == TechItems.URANIUM_FUEL_ROD;
        }
    };

    public TileNuclearReactorCore() { super(50000000, 0, FE_PER_TICK); }

    int getHeat() { return heat; }
    int getHeatLimit() { return MAX_HEAT; }

    int getFuelTicks() {
        ItemStack fuel = inventory.getStackInSlot(0);
        if (!fuel.isEmpty() && fuel.getItem() instanceof ItemFuelRod) {
            return ((ItemFuelRod) fuel.getItem()).getBurnTimeRemaining(fuel);
        }
        return fuelTicks;
    }

    @Override public void update() {
        if (world == null || world.isRemote) return;
        if (heat >= MAX_HEAT) { meltdown(); return; }

        ItemStack fuel = inventory.getStackInSlot(0);
        boolean hasFuel = !fuel.isEmpty() && fuel.getItem() instanceof ItemFuelRod;
        boolean hasRoom = energy.getCapacity() - energy.getEnergyStored() >= FE_PER_TICK;

        if (hasFuel && hasRoom) {
            ItemFuelRod rod = (ItemFuelRod) fuel.getItem();
            int burned = rod.consumeBurnTime(fuel, 1);
            fuelTicks = rod.getBurnTimeRemaining(fuel);
            if (burned > 0 && energy.addInternal(FE_PER_TICK) == FE_PER_TICK) {
                heat = Math.min(MAX_HEAT, heat + 1);
                if (fuelTicks <= 0) {
                    inventory.setStackInSlot(0, new ItemStack(TechItems.URANIUM_FUEL_ROD_DEPLETED));
                    fuelTicks = 0;
                } else {
                    markDirty();
                }
            }
        } else {
            fuelTicks = hasFuel ? ((ItemFuelRod) fuel.getItem()).getBurnTimeRemaining(fuel) : 0;
            heat = Math.max(0, heat - PASSIVE_COOLING);
        }

        if (heat >= MAX_HEAT) { meltdown(); return; }
        EnergyTransfer.push(this, FE_PER_TICK);
        syncIfChanged();
    }

    private void meltdown() {
        if (world == null || world.isRemote) return;
        BlockPos reactorPos = pos;
        world.createExplosion(null, reactorPos.getX() + 0.5D, reactorPos.getY() + 0.5D,
                reactorPos.getZ() + 0.5D, 4.0F, true);
        if (world.getTileEntity(reactorPos) == this) world.setBlockToAir(reactorPos);
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
        state = state * 31L + heat;
        state = state * 31L + getFuelTicks();
        return state * 31L + (inventory.getStackInSlot(0).isEmpty() ? 0 : 1);
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
        tag.setInteger("FuelTicks", getFuelTicks());
        tag.setInteger("Heat", heat);
        tag.setTag("Inventory", inventory.serializeNBT());
        return tag;
    }

    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        if (tag.hasKey("Inventory")) inventory.deserializeNBT(tag.getCompoundTag("Inventory"));
        heat = Math.max(0, Math.min(MAX_HEAT, tag.getInteger("Heat")));
        fuelTicks = Math.max(0, Math.min(FUEL_TICKS, tag.getInteger("FuelTicks")));
        ItemStack fuel = inventory.getStackInSlot(0);
        if (!fuel.isEmpty() && fuel.getItem() instanceof ItemFuelRod) {
            fuelTicks = ((ItemFuelRod) fuel.getItem()).getBurnTimeRemaining(fuel);
        } else {
            fuelTicks = 0;
        }
    }
}
