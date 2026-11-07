package br.com.mikael8367.nexustech;

import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ITickable;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.ItemStackHandler;

/** Nuclear reactor with spent-fuel recovery, redstone shutdown and thermal meltdown. */
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
    private boolean isRedstonePowered;
    /** True after a rod finishes burning but before its depleted item fits in output slot 1. */
    private boolean pendingDepletedRod;

    private final ItemStackHandler inventory = new ValidatedItemHandler(2) {
        @Override protected void onContentsChanged(int slot) { markDirty(); }

        @Override public int getSlotLimit(int slot) {
            return slot == 0 ? 1 : 16;
        }

        @Override public boolean isItemValid(int slot, ItemStack stack) {
            if (stack == null || stack.isEmpty()) return false;
            if (slot == 0) return stack.getItem() instanceof ItemFuelRod;
            return slot == 1 && stack.getItem() == TechItems.URANIUM_FUEL_ROD_DEPLETED;
        }

        /** Fuel is reactor-owned; only the spent-fuel output can be extracted by hoppers. */
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot != 1) return ItemStack.EMPTY;
            return super.extractItem(slot, amount, simulate);
        }
    };

    public TileNuclearReactorCore() {
        super(50000000, 0, 32768);
    }

    public int getFuelTicks() { return fuelTicks; }
    public int getHeat() { return heat; }
    public int getHeatLimit() { return MAX_HEAT; }
    public boolean isExploding() { return isExploding; }
    public boolean isRedstonePowered() { return isRedstonePowered; }
    public boolean hasPendingDepletedRod() { return pendingDepletedRod; }

    /** Inserts exactly one fresh rod. The caller shrinks the held stack only on success. */
    public boolean insertFuel(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof ItemFuelRod)) return false;
        ItemStack one = stack.copy();
        one.setCount(1);
        return inventory.insertItem(0, one, false).isEmpty();
    }

    private boolean hasRoomForDepletedRod() {
        ItemStack output = inventory.getStackInSlot(1);
        return output.isEmpty() || (output.getItem() == TechItems.URANIUM_FUEL_ROD_DEPLETED
                && output.getCount() < Math.min(output.getMaxStackSize(), inventory.getSlotLimit(1)));
    }

    /** Completes the spent-fuel transfer atomically; a full output leaves it pending. */
    private boolean tryStoreDepletedRod() {
        if (!pendingDepletedRod) return true;
        ItemStack remainder = inventory.insertItem(1,
                new ItemStack(TechItems.URANIUM_FUEL_ROD_DEPLETED, 1), false);
        if (!remainder.isEmpty()) return false;
        pendingDepletedRod = false;
        markDirty();
        return true;
    }

    private void beginFuelCycle() {
        if (fuelTicks > 0 || pendingDepletedRod || !hasRoomForDepletedRod()) return;
        ItemStack stored = inventory.getStackInSlot(0);
        if (stored.isEmpty() || !(stored.getItem() instanceof ItemFuelRod)) return;

        ItemStack remainder = stored.copy();
        remainder.shrink(1);
        inventory.setStackInSlot(0, remainder.isEmpty() ? ItemStack.EMPTY : remainder);
        fuelTicks = FUEL_TICKS;
        markDirty();
    }

    @Override public void update() {
        if (world == null || isExploding) return;

        // Client-only ambient particles. Machine state is synchronized by AbstractEnergyTile.
        if (world.isRemote) {
            if (fuelTicks > 0 && !isRedstonePowered && world.getTotalWorldTime() % 10L == 0L) {
                world.spawnParticle(EnumParticleTypes.SMOKE_NORMAL,
                        pos.getX() + 0.5D, pos.getY() + 1.1D, pos.getZ() + 0.5D,
                        0.0D, 0.1D, 0.0D);
                world.spawnParticle(EnumParticleTypes.FLAME,
                        pos.getX() + 0.5D, pos.getY() + 1.05D, pos.getZ() + 0.5D,
                        0.0D, 0.025D, 0.0D);
            }
            return;
        }

        isRedstonePowered = world.isBlockPowered(pos);

        // A completed rod is never discarded. If the output is full, generation remains stopped.
        if (pendingDepletedRod && !tryStoreDepletedRod()) {
            heat = Math.max(0, heat - COOLING_PER_TICK);
            if (heat >= MAX_HEAT) {
                meltdown();
                return;
            }
            EnergyTransfer.push(this, PUSH_PER_TICK);
            syncIfChanged();
            return;
        }

        if (!isRedstonePowered) {
            beginFuelCycle();

            if (fuelTicks > 0 && energy.getCapacity() - energy.getEnergyStored() >= FE_PER_TICK
                    && energy.addInternal(FE_PER_TICK) == FE_PER_TICK) {
                fuelTicks--;
                heat = Math.min(Integer.MAX_VALUE - HEAT_PER_TICK, heat) + HEAT_PER_TICK;
                if (fuelTicks == 0) {
                    pendingDepletedRod = true;
                    tryStoreDepletedRod();
                }
                markDirty();
            }
        }

        // Passive cooling applies while idle, redstone-disabled or blocked by a full output.
        heat = Math.max(0, heat - COOLING_PER_TICK);

        if (heat >= MAX_HEAT) {
            meltdown();
            return;
        }

        if (fuelTicks > 0 && !isRedstonePowered && world.getTotalWorldTime() % 40L == 0L) {
            world.playSound(null, pos, SoundEvents.BLOCK_FIRE_AMBIENT,
                    SoundCategory.BLOCKS, 0.5F, 0.8F);
        }

        EnergyTransfer.push(this, PUSH_PER_TICK);
        syncIfChanged();
    }

    private void meltdown() {
        if (world == null || world.isRemote || isExploding) return;
        isExploding = true;
        markDirty();

        double x = pos.getX() + 0.5D;
        double y = pos.getY() + 0.5D;
        double z = pos.getZ() + 0.5D;

        world.playSound(null, pos, SoundEvents.ENTITY_GENERIC_EXPLODE,
                SoundCategory.BLOCKS, 4.0F, 0.7F);
        if (world instanceof WorldServer) {
            WorldServer server = (WorldServer) world;
            server.spawnParticle(EnumParticleTypes.SMOKE_LARGE, true,
                    x, y, z, 36, 1.6D, 1.2D, 1.6D, 0.04D);
            server.spawnParticle(EnumParticleTypes.FLAME, true,
                    x, y, z, 28, 1.2D, 1.0D, 1.2D, 0.08D);
        }

        BlockPos reactorPos = pos;
        world.createExplosion(null, x, y, z, 8.0F, true);
        if (world.getTileEntity(reactorPos) == this) {
            world.setBlockToAir(reactorPos);
        }
    }

    /** Drops both inventory slots and any spent rod waiting for output space. */
    void dropContents() {
        if (world == null || world.isRemote) return;
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                MachineItems.drop(world, pos, stack.copy());
                inventory.setStackInSlot(slot, ItemStack.EMPTY);
            }
        }
        if (pendingDepletedRod) {
            MachineItems.drop(world, pos, new ItemStack(TechItems.URANIUM_FUEL_ROD_DEPLETED, 1));
            pendingDepletedRod = false;
        }
    }

    @Override protected long syncState() {
        long state = energy.getEnergyStored();
        state = state * 31L + fuelTicks;
        state = state * 31L + heat;
        state = state * 31L + (isRedstonePowered ? 1L : 0L);
        state = state * 31L + (pendingDepletedRod ? 1L : 0L);
        return state * 31L + (isExploding ? 1L : 0L);
    }

    @Override public boolean hasCapability(net.minecraftforge.common.capabilities.Capability<?> capability, EnumFacing side) {
        return capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY || super.hasCapability(capability, side);
    }

    @Override @SuppressWarnings("unchecked")
    public <T> T getCapability(net.minecraftforge.common.capabilities.Capability<T> capability, EnumFacing side) {
        return capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY
                ? (T) inventory : super.getCapability(capability, side);
    }

    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setInteger("FuelTicks", fuelTicks);
        tag.setInteger("Heat", heat);
        tag.setBoolean("IsExploding", isExploding);
        tag.setBoolean("IsRedstonePowered", isRedstonePowered);
        tag.setBoolean("PendingDepletedRod", pendingDepletedRod);
        tag.setTag("Inventory", inventory.serializeNBT());
        return tag;
    }

    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        fuelTicks = Math.max(0, Math.min(FUEL_TICKS, tag.getInteger("FuelTicks")));
        heat = Math.max(0, Math.min(MAX_HEAT, tag.getInteger("Heat")));
        isExploding = tag.getBoolean("IsExploding");
        isRedstonePowered = tag.getBoolean("IsRedstonePowered");
        pendingDepletedRod = tag.getBoolean("PendingDepletedRod");
        if (tag.hasKey("Inventory")) {
            inventory.deserializeNBT(tag.getCompoundTag("Inventory"));
        }

        // ItemStackHandler NBT loading bypasses insertion validation; sanitize both slots.
        ItemStack input = inventory.getStackInSlot(0);
        if (!input.isEmpty() && !(input.getItem() instanceof ItemFuelRod)) {
            inventory.setStackInSlot(0, ItemStack.EMPTY);
        } else if (!input.isEmpty() && input.getCount() > 1) {
            input.setCount(1);
        }
        ItemStack output = inventory.getStackInSlot(1);
        if (!output.isEmpty() && output.getItem() != TechItems.URANIUM_FUEL_ROD_DEPLETED) {
            inventory.setStackInSlot(1, ItemStack.EMPTY);
        } else if (!output.isEmpty() && output.getCount() > inventory.getSlotLimit(1)) {
            output.setCount(inventory.getSlotLimit(1));
        }
        if (fuelTicks > 0) pendingDepletedRod = false;
    }
}
