package br.com.mikael8367.nexustech;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;

/** Automated miner with a persisted raster cursor and a bounded 3x9 output inventory. */
public final class TileDigitalMiner extends AbstractEnergyTile implements ITickable {
    static final int FE_PER_BLOCK = 2048;
    static final int MAX_RADIUS = 16;
    private static final int DEFAULT_RADIUS = 5;
    private static final int MINING_DEPTH = 16;
    private static final int MAX_SCAN_PER_TICK = 256;

    private boolean isRunning;
    private int radius = DEFAULT_RADIUS;
    /** The next block to inspect; null means the scan has not started or is complete. */
    private BlockPos currentPos;

    private final ItemStackHandler inventory = new ItemStackHandler(27) {
        @Override protected void onContentsChanged(int slot) { markDirty(); }
    };

    /** The miner inventory is output-only: automation may extract drops but cannot inject items. */
    private final IItemHandler automationInventory = new IItemHandler() {
        @Override public int getSlots() { return inventory.getSlots(); }
        @Override public ItemStack getStackInSlot(int slot) { return inventory.getStackInSlot(slot); }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return stack;
        }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return inventory.extractItem(slot, amount, simulate);
        }
        @Override public int getSlotLimit(int slot) { return inventory.getSlotLimit(slot); }
    };

    public TileDigitalMiner() {
        super(10000000, 16384, 0);
    }

    public boolean isRunning() { return isRunning; }
    public int getRadius() { return radius; }
    public BlockPos getCurrentPos() { return currentPos; }

    /** The horizontal scan is circular, not a square. */
    static boolean isWithinRadius(int dx, int dz, int radius) {
        int boundedRadius = Math.max(0, Math.min(MAX_RADIUS, radius));
        return dx * dx + dz * dz <= boundedRadius * boundedRadius;
    }

    static int getFirstMiningY(int machineY) { return machineY - 2; }
    static int getLastMiningY(int machineY) { return machineY - 2 - (MINING_DEPTH - 1); }

    public void setRunning(boolean running) {
        if (running && currentPos == null) resetCursor();
        if (isRunning != running) {
            isRunning = running;
            markDirty();
        }
    }

    public void setRadius(int value) {
        int bounded = Math.max(1, Math.min(MAX_RADIUS, value));
        if (radius == bounded) return;
        radius = bounded;
        currentPos = null;
        if (isRunning) resetCursor();
        markDirty();
    }

    private void resetCursor() {
        currentPos = pos.add(-radius, -2, -radius);
    }

    private boolean canMine() {
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack.isEmpty() || stack.getCount() < Math.min(stack.getMaxStackSize(), inventory.getSlotLimit(slot))) {
                return true;
            }
        }
        return false;
    }

    @Override public void update() {
        if (world == null || world.isRemote) return;
        EnergyTransfer.pull(this, 16384);
        if (isRunning) {
            if (!canMine()) {
                setRunning(false);
            } else {
                // Scanning air/ignored positions is free; energy is checked only for a real block.
                mineNextBlock();
            }
        }
        syncIfChanged();
    }

    /** Inspects at most 256 positions per tick and mines at most one block per tick. */
    private boolean mineNextBlock() {
        if (currentPos == null) resetCursor();

        for (int checked = 0; checked < MAX_SCAN_PER_TICK && currentPos != null; checked++) {
            BlockPos target = currentPos;
            int dx = target.getX() - pos.getX();
            int dz = target.getZ() - pos.getZ();
            if (!isWithinRadius(dx, dz, radius)
                    || target.getY() < 1 || target.getY() >= world.getHeight()) {
                advanceCursor();
                continue;
            }

            // Never force-load chunks and never permanently skip an unloaded position.
            // The miner pauses here and resumes from the same cursor when the chunk is loaded.
            if (!world.isBlockLoaded(target)) return false;

            IBlockState state = world.getBlockState(target);
            Block block = state.getBlock();
            if (block.isAir(state, world, target) || state.getMaterial().isLiquid()
                    || world.getTileEntity(target) != null
                    || block.getBlockHardness(state, world, target) < 0.0F) {
                advanceCursor();
                continue;
            }

            NonNullList<ItemStack> drops = NonNullList.create();
            block.getDrops(drops, world, target, state, 0);
            if (!canStoreDrops(drops)) {
                // Keep the cursor on this block. The player can empty the inventory and resume.
                setRunning(false);
                return false;
            }
            if (energy.getEnergyStored() < FE_PER_BLOCK || !energy.consumeInternal(FE_PER_BLOCK)) {
                // Do not move the cursor: this exact block is retried when energy arrives.
                return false;
            }

            if (!world.destroyBlock(target, false)) {
                energy.addInternal(FE_PER_BLOCK);
                advanceCursor();
                continue;
            }

            for (ItemStack drop : drops) {
                ItemStack remainder = drop.copy();
                for (int slot = 0; slot < inventory.getSlots() && !remainder.isEmpty(); slot++) {
                    remainder = inventory.insertItem(slot, remainder, false);
                }
                // The simulation above should guarantee no remainder; preserve items if a handler changes.
                if (!remainder.isEmpty()) MachineItems.drop(world, pos, remainder);
            }
            advanceCursor();
            markDirty();
            return true;
        }
        return false;
    }

    private void advanceCursor() {
        if (currentPos == null) return;
        int minX = pos.getX() - radius;
        int maxX = pos.getX() + radius;
        int minZ = pos.getZ() - radius;
        int maxZ = pos.getZ() + radius;
        int minY = getLastMiningY(pos.getY());

        if (currentPos.getX() < maxX) {
            currentPos = currentPos.add(1, 0, 0);
        } else if (currentPos.getZ() < maxZ) {
            currentPos = new BlockPos(minX, currentPos.getY(), currentPos.getZ() + 1);
        } else if (currentPos.getY() > minY) {
            currentPos = new BlockPos(minX, currentPos.getY() - 1, minZ);
        } else {
            currentPos = null;
            isRunning = false;
        }
        markDirty();
    }

    private boolean canStoreDrops(NonNullList<ItemStack> drops) {
        ItemStackHandler simulated = new ItemStackHandler(inventory.getSlots());
        simulated.deserializeNBT(inventory.serializeNBT());
        for (ItemStack drop : drops) {
            ItemStack remaining = drop.copy();
            for (int slot = 0; slot < simulated.getSlots() && !remaining.isEmpty(); slot++) {
                remaining = simulated.insertItem(slot, remaining, false);
            }
            if (!remaining.isEmpty()) return false;
        }
        return true;
    }

    int getStoredItems() {
        int count = 0;
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            count += inventory.getStackInSlot(slot).getCount();
        }
        return count;
    }

    void dropContents() {
        if (world == null || world.isRemote) return;
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                MachineItems.drop(world, pos, stack.copy());
                inventory.setStackInSlot(slot, ItemStack.EMPTY);
            }
        }
    }

    @Override protected long syncState() {
        long state = energy.getEnergyStored();
        state = state * 31L + (isRunning ? 1L : 0L);
        state = state * 31L + radius;
        state = state * 31L + getStoredItems();
        if (currentPos != null) {
            state = state * 31L + currentPos.getX();
            state = state * 31L + currentPos.getY();
            state = state * 31L + currentPos.getZ();
        }
        return state;
    }

    @Override public boolean hasCapability(net.minecraftforge.common.capabilities.Capability<?> capability, EnumFacing side) {
        return capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY || super.hasCapability(capability, side);
    }

    @Override @SuppressWarnings("unchecked")
    public <T> T getCapability(net.minecraftforge.common.capabilities.Capability<T> capability, EnumFacing side) {
        return capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY
                ? (T) automationInventory : super.getCapability(capability, side);
    }

    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setTag("Inventory", inventory.serializeNBT());
        tag.setBoolean("IsRunning", isRunning);
        tag.setInteger("Radius", radius);
        tag.setBoolean("HasCurrentPos", currentPos != null);
        tag.setInteger("CurrentPosX", currentPos == null ? -1 : currentPos.getX());
        tag.setInteger("CurrentPosY", currentPos == null ? -1 : currentPos.getY());
        tag.setInteger("CurrentPosZ", currentPos == null ? -1 : currentPos.getZ());
        return tag;
    }

    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        if (tag.hasKey("Inventory")) inventory.deserializeNBT(tag.getCompoundTag("Inventory"));
        isRunning = tag.getBoolean("IsRunning");
        radius = Math.max(1, Math.min(MAX_RADIUS, tag.hasKey("Radius") ? tag.getInteger("Radius") : DEFAULT_RADIUS));
        currentPos = null;
        if (tag.getBoolean("HasCurrentPos")) {
            int x = tag.getInteger("CurrentPosX");
            int y = tag.getInteger("CurrentPosY");
            int z = tag.getInteger("CurrentPosZ");
            int minX = pos.getX() - radius;
            int maxX = pos.getX() + radius;
            int minZ = pos.getZ() - radius;
            int maxZ = pos.getZ() + radius;
            int minY = getLastMiningY(pos.getY());
            int maxY = getFirstMiningY(pos.getY());
            if (x >= minX && x <= maxX && z >= minZ && z <= maxZ
                    && y >= minY && y <= maxY) {
                currentPos = new BlockPos(x, y, z);
            }
        }
        if (isRunning && currentPos == null) resetCursor();
    }

    @Override public AxisAlignedBB getRenderBoundingBox() {
        return new AxisAlignedBB(pos.getX(), pos.getY() - 2, pos.getZ(),
                pos.getX() + 2, pos.getY() + 4, pos.getZ() + 2);
    }
}
