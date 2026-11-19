package com.mikael8367.nexustech;

import javax.annotation.Nullable;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.world.EnumSkyBlock;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.IItemHandler;

public abstract class MachineTile extends TileEntity implements ITickable {
    public enum Kind { CABLE, SOLAR, GENERATOR, TURBINE, BATTERY, FURNACE, CRUSHER, COMPRESSOR, LAVA_GENERATOR }
    public static final int FURNACE_TIME = 200, FURNACE_COST = 20;
    public static final int LAVA_BURN_TIME = 1000, LAVA_FE_PER_TICK = 40;
    public final Kind kind;
    public final int capacity;
    int energy, burn, burnTotal, cook;
    private boolean energyTransfer;
    ItemStack cookingInput = ItemStack.EMPTY;
    public final ItemStackHandler inventory;
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
            return isOutputMachine() && slot == 1 ? inventory.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
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
                if (kind == Kind.CRUSHER || kind == Kind.COMPRESSOR) return slot == 0 && !MachineTileLogic.processResult(kind, stack).isEmpty();
                if (kind == Kind.LAVA_GENERATOR) return slot == 0 && !stack.isEmpty() && stack.getItem() == Items.LAVA_BUCKET;
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
    public static class Crusher extends MachineTile { public Crusher() { super(Kind.CRUSHER, 20000, 2); } }
    public static class Compressor extends MachineTile { public Compressor() { super(Kind.COMPRESSOR, 20000, 2); } }
    public static class LavaGenerator extends MachineTile { public LavaGenerator() { super(Kind.LAVA_GENERATOR, 20000, 2); } }

    final net.minecraft.world.World logicWorld() { return world; }
    final net.minecraft.util.math.BlockPos logicPos() { return pos; }

    private boolean isOutputMachine() { return kind == Kind.FURNACE || kind == Kind.CRUSHER || kind == Kind.COMPRESSOR || kind == Kind.LAVA_GENERATOR; }

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
            if (maxReceive <= 0 || world == null || world.isRemote || !world.isBlockLoaded(pos) || energyTransfer || !canReceive()) return 0;
            if (kind == Kind.CABLE) return EnergyNetwork.send(world, pos, side == null ? null : pos.offset(side), maxReceive, simulate, false);
            int n = Math.min(Math.min(maxReceive, 1000), capacity - energy);
            if (!simulate && n > 0) { energy += n; markDirty(); }
            return n;
        }
        @Override public int extractEnergy(int maxExtract, boolean simulate) {
            if (maxExtract <= 0 || world == null || world.isRemote || !world.isBlockLoaded(pos) || energyTransfer || !canExtract()) return 0;
            // Without a side the requesting block cannot be identified safely.
            if (kind == Kind.CABLE) return side == null ? 0
                    : EnergyNetwork.pull(world, pos, pos.offset(side), maxExtract, simulate, true);
            int n = Math.min(Math.min(maxExtract, 1000), energy);
            if (!simulate && n > 0) { energy -= n; markDirty(); }
            return n;
        }
        @Override public int getEnergyStored() { return energy; }
        @Override public int getMaxEnergyStored() { return capacity; }
        @Override public boolean canExtract() { return kind == Kind.CABLE || kind == Kind.BATTERY || kind == Kind.SOLAR || kind == Kind.GENERATOR || kind == Kind.TURBINE || kind == Kind.LAVA_GENERATOR; }
        @Override public boolean canReceive() { return kind == Kind.CABLE || kind == Kind.BATTERY || kind == Kind.FURNACE || kind == Kind.CRUSHER || kind == Kind.COMPRESSOR; }
    }
    @Override public void update() {
        if (world == null || world.isRemote || !world.isBlockLoaded(pos) || energyTransfer) return;
        switch (kind) {
            case SOLAR:
                if (world.provider.hasSkyLight() && world.isDaytime() && !world.isRaining() && !world.isThundering() && world.isBlockLoaded(pos.up()) && world.canSeeSky(pos.up()) && world.getLightFor(EnumSkyBlock.SKY, pos.up()) >= 15) MachineTileLogic.generate(this, 20);
                break;
            case TURBINE: if (MachineTileLogic.flowingWater(this)) MachineTileLogic.generate(this, 30); break;
            case GENERATOR: MachineTileLogic.tickGenerator(this); break;
            case FURNACE: MachineTileLogic.tickFurnace(this); break;
            case CRUSHER: case COMPRESSOR: MachineTileLogic.tickProcessor(this); break;
            case LAVA_GENERATOR: MachineTileLogic.tickLavaGenerator(this); break;
            default: break;
        }
        if (kind == Kind.BATTERY || kind == Kind.FURNACE || kind == Kind.CRUSHER || kind == Kind.COMPRESSOR) {
            int request = Math.min(capacity - energy, kind == Kind.BATTERY ? 128 : 100);
            if (request > 0) {
                energyTransfer = true;
                try {
                    int received = EnergyNetwork.pull(world, pos, pos, request, false, kind != Kind.BATTERY);
                    if (received > 0) { energy += received; markDirty(); }
                } finally { energyTransfer = false; }
            }
        }
        if (kind == Kind.SOLAR || kind == Kind.TURBINE || kind == Kind.GENERATOR || kind == Kind.BATTERY || kind == Kind.LAVA_GENERATOR) {
            int available = Math.min(energy, kind == Kind.BATTERY ? 128 : 100);
            if (available > 0) {
                // Reserve before callbacks; no callback can use the same FE twice
                // or fill the reserved space before the unused portion is returned.
                energyTransfer = true;
                energy -= available;
                markDirty();
                int moved = 0;
                try {
                    moved = EnergyNetwork.send(world, pos, pos, available, false, kind == Kind.BATTERY);
                } finally {
                    energy += available - moved;
                    markDirty();
                    energyTransfer = false;
                }
            }
        }
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
            savedInventory.setInteger("Size", inventory.getSlots());
            inventory.deserializeNBT(savedInventory);
        }
        cookingInput = nbt.hasKey("CookingInput", 10) ? new ItemStack(nbt.getCompoundTag("CookingInput")) : ItemStack.EMPTY;
    }
    int field(int index) { switch (index) { case 0: return energy; case 1: return burn; case 2: return burnTotal; case 3: return cook; default: return 0; } }
    void setField(int index, int value) { switch (index) { case 0: energy = value; break; case 1: burn = value; break; case 2: burnTotal = value; break; case 3: cook = value; break; default: break; } }
}
