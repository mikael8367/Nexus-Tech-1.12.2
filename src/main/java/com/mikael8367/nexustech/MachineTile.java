package com.mikael8367.nexustech;

import javax.annotation.Nullable;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.IItemHandler;

public abstract class MachineTile extends TileEntity implements ITickable {
    public enum Kind { CABLE, SOLAR, GENERATOR, TURBINE, BATTERY, FURNACE }
    public static final int FURNACE_TIME = 200, FURNACE_COST = 20;
    public final Kind kind;
    public final int capacity;
    int energy, burn, burnTotal, cook;
    private ItemStack cookingInput = ItemStack.EMPTY;
    public final ItemStackHandler inventory;
    // Automation shares the real inventory but cannot bypass its input/output rules.
    // GUI slots continue to use inventory directly, including removal of containers.
    private final IItemHandler automation = new IItemHandler() {
        @Override public int getSlots() { return inventory.getSlots(); }
        @Override public ItemStack getStackInSlot(int slot) { return inventory.getStackInSlot(slot); }
        @Override public int getSlotLimit(int slot) { return inventory.getSlotLimit(slot); }
        @Override public boolean isItemValid(int slot, ItemStack stack) {
            return slot == 0 && inventory.isItemValid(slot, stack);
        }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return slot == 0 ? inventory.insertItem(slot, stack, simulate) : stack;
        }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return kind == Kind.FURNACE && slot == 1 ? inventory.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
        }
    };
    private final IEnergyStorage storage = new Storage(null);
    private final IEnergyStorage[] sided = new IEnergyStorage[6];

    protected MachineTile(Kind kind, int capacity, int slots) {
        this.kind = kind; this.capacity = capacity;
        inventory = new ItemStackHandler(slots) {
            @Override protected void onContentsChanged(int slot) { if (world != null && !world.isRemote) markDirty(); }
            @Override public boolean isItemValid(int slot, ItemStack stack) {
                if (kind == Kind.GENERATOR) return slot == 0 && TileEntityFurnace.getItemBurnTime(stack) > 0;
                if (kind == Kind.FURNACE) return slot == 0 && !FurnaceRecipes.instance().getSmeltingResult(stack).isEmpty();
                return false;
            }
            @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                if (!isItemValid(slot, stack)) return stack;
                return super.insertItem(slot, stack, simulate);
            }
        };
        for (EnumFacing face : EnumFacing.VALUES) sided[face.ordinal()] = new Storage(face);
    }
    public static class Cable extends MachineTile { public Cable() { super(Kind.CABLE, 0, 0); } }
    public static class Solar extends MachineTile { public Solar() { super(Kind.SOLAR, 10000, 0); } }
    public static class Generator extends MachineTile { public Generator() { super(Kind.GENERATOR, 20000, 1); } }
    public static class Turbine extends MachineTile { public Turbine() { super(Kind.TURBINE, 10000, 0); } }
    public static class Battery extends MachineTile { public Battery() { super(Kind.BATTERY, 100000, 0); } }
    public static class Furnace extends MachineTile { public Furnace() { super(Kind.FURNACE, 20000, 2); } }

    @Override public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing facing) {
        return capability == CapabilityEnergy.ENERGY || (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY && inventory.getSlots() > 0) || super.hasCapability(capability, facing);
    }
    @Override public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityEnergy.ENERGY) return CapabilityEnergy.ENERGY.cast(facing == null ? storage : sided[facing.ordinal()]);
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY && inventory.getSlots() > 0) return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(automation);
        return super.getCapability(capability, facing);
    }
    private final class Storage implements IEnergyStorage {
        private final EnumFacing side;
        Storage(EnumFacing side) { this.side = side; }
        @Override public int receiveEnergy(int maxReceive, boolean simulate) {
            if (maxReceive <= 0 || world == null || world.isRemote || !canReceive()) return 0;
            if (kind == Kind.CABLE) return EnergyNetwork.send(world, pos, side == null ? null : pos.offset(side), maxReceive, simulate, false);
            int n = Math.min(Math.min(maxReceive, 1000), capacity - energy);
            if (!simulate && n > 0) { energy += n; markDirty(); }
            return n;
        }
        @Override public int extractEnergy(int maxExtract, boolean simulate) {
            if (maxExtract <= 0 || world == null || world.isRemote || !canExtract()) return 0;
            int n = Math.min(Math.min(maxExtract, 1000), energy);
            if (!simulate && n > 0) { energy -= n; markDirty(); }
            return n;
        }
        @Override public int getEnergyStored() { return energy; }
        @Override public int getMaxEnergyStored() { return capacity; }
        @Override public boolean canExtract() { return kind == Kind.BATTERY || kind == Kind.SOLAR || kind == Kind.GENERATOR || kind == Kind.TURBINE; }
        @Override public boolean canReceive() { return kind == Kind.CABLE || kind == Kind.BATTERY || kind == Kind.FURNACE; }
    }
    @Override public void update() {
        if (world == null || world.isRemote) return;
        switch (kind) {
            case SOLAR:
                if (world.provider.hasSkyLight() && world.isDaytime() && !world.isRaining() && !world.isThundering() && world.isBlockLoaded(pos.up()) && world.canSeeSky(pos.up()) && world.getLightFor(EnumSkyBlock.SKY, pos.up()) >= 15) generate(20);
                break;
            case TURBINE: if (flowingWater()) generate(30); break;
            case GENERATOR: tickGenerator(); break;
            case FURNACE: tickFurnace(); break;
            default: break;
        }
        if (kind == Kind.SOLAR || kind == Kind.TURBINE || kind == Kind.GENERATOR || kind == Kind.BATTERY) {
            int available = Math.min(energy, kind == Kind.BATTERY ? 128 : 100);
            if (available > 0) {
                int moved = EnergyNetwork.send(world, pos, pos, available, false, kind == Kind.BATTERY);
                if (moved > 0) { energy -= moved; markDirty(); }
            }
        }
    }
    private void generate(int amount) {
        int n = Math.min(amount, capacity - energy);
        if (n > 0) { energy += n; markDirty(); }
    }
    /** Any of six adjacent loaded blocks of vanilla water with LEVEL > 0 powers the turbine. */
    private boolean flowingWater() {
        for (EnumFacing face : EnumFacing.VALUES) {
            BlockPos p = pos.offset(face);
            if (!world.isBlockLoaded(p)) continue;
            IBlockState state = world.getBlockState(p);
            if ((state.getBlock() == Blocks.WATER || state.getBlock() == Blocks.FLOWING_WATER) && state.getValue(BlockLiquid.LEVEL) > 0) return true;
        }
        return false;
    }
    private void tickGenerator() {
        if (capacity - energy < 40) return; // pause both ignition and burn until a full tick fits
        if (burn <= 0) {
            ItemStack fuel = inventory.getStackInSlot(0);
            int duration = TileEntityFurnace.getItemBurnTime(fuel);
            if (duration <= 0) return;
            ItemStack remainder = fuel.getItem().getContainerItem(fuel);
            if (!remainder.isEmpty() && fuel.getCount() != 1) return;
            fuel.shrink(1);
            if (fuel.isEmpty()) inventory.setStackInSlot(0, remainder);
            burn = burnTotal = duration;
        }
        burn--; generate(40); markDirty();
    }
    private boolean canSmelt() {
        ItemStack input = inventory.getStackInSlot(0);
        if (input.isEmpty()) return false;
        ItemStack result = FurnaceRecipes.instance().getSmeltingResult(input);
        if (result.isEmpty()) return false;
        ItemStack out = inventory.getStackInSlot(1);
        if (out.isEmpty()) return result.getCount() <= Math.min(result.getMaxStackSize(), inventory.getSlotLimit(1));
        return ItemStack.areItemsEqual(out, result) && ItemStack.areItemStackTagsEqual(out, result)
                && out.getCount() + result.getCount() <= Math.min(out.getMaxStackSize(), inventory.getSlotLimit(1));
    }
    private void tickFurnace() {
        ItemStack current = inventory.getStackInSlot(0);
        if (!ItemStack.areItemsEqual(current, cookingInput) || !ItemStack.areItemStackTagsEqual(current, cookingInput)) {
            cookingInput = current.copy();
            if (!cookingInput.isEmpty()) cookingInput.setCount(1);
            cook = 0; markDirty();
        }
        if (!canSmelt()) { if (cook != 0) { cook = 0; markDirty(); } return; }
        if (energy < FURNACE_COST) return; // pause, do not lose progress
        energy -= FURNACE_COST; cook++;
        if (cook >= FURNACE_TIME) {
            ItemStack input = inventory.getStackInSlot(0);
            ItemStack result = FurnaceRecipes.instance().getSmeltingResult(input).copy();
            ItemStack out = inventory.getStackInSlot(1);
            if (out.isEmpty()) inventory.setStackInSlot(1, result); else out.grow(result.getCount());
            input.shrink(1); cook = 0;
        }
        markDirty();
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setInteger("Energy", energy); nbt.setInteger("Burn", burn); nbt.setInteger("BurnTotal", burnTotal); nbt.setInteger("Cook", cook);
        nbt.setTag("Inventory", inventory.serializeNBT());
        if (!cookingInput.isEmpty()) nbt.setTag("CookingInput", cookingInput.writeToNBT(new NBTTagCompound()));
        return nbt;
    }
    @Override public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        energy = Math.max(0, Math.min(capacity, nbt.getInteger("Energy")));
        burn = Math.max(0, nbt.getInteger("Burn")); burnTotal = Math.max(0, nbt.getInteger("BurnTotal"));
        cook = Math.max(0, Math.min(FURNACE_TIME - 1, nbt.getInteger("Cook")));
        if (nbt.hasKey("Inventory", 10)) {
            // Never let saved data resize the fixed-size machine inventory.
            NBTTagCompound savedInventory = nbt.getCompoundTag("Inventory").copy();
            savedInventory.setInteger("Size", kind == Kind.FURNACE ? 2 : kind == Kind.GENERATOR ? 1 : 0);
            inventory.deserializeNBT(savedInventory);
        }
        cookingInput = nbt.hasKey("CookingInput", 10) ? new ItemStack(nbt.getCompoundTag("CookingInput")) : ItemStack.EMPTY;
    }
    int field(int index) { switch (index) { case 0: return energy; case 1: return burn; case 2: return burnTotal; case 3: return cook; default: return 0; } }
    void setField(int index, int value) { switch (index) { case 0: energy = value; break; case 1: burn = value; break; case 2: burnTotal = value; break; case 3: cook = value; break; default: break; } }
}
