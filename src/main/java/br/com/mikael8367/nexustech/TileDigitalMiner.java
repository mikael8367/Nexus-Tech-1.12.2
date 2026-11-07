package br.com.mikael8367.nexustech;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.ItemStackHandler;

/**
 * Mines ordinary blocks in a bounded cylinder below the machine.
 * Tile entities and unloaded chunks are never touched.
 */
public final class TileDigitalMiner extends AbstractEnergyTile implements ITickable {
    static final int ENERGY_PER_BLOCK = 2048;
    static final int MINING_RADIUS = 5;
    static final int MINING_DEPTH = 16;
    private static final int MAX_SCAN_PER_TICK = 256;
    private static final int WIDTH = MINING_RADIUS * 2 + 1;
    private static final int SCAN_VOLUME = WIDTH * WIDTH * MINING_DEPTH;

    private boolean isRunning;
    private int scanIndex;

    private final ItemStackHandler inventory = new ItemStackHandler(27) {
        @Override protected void onContentsChanged(int slot) { markDirty(); }
    };

    public TileDigitalMiner() { super(10000000, 8192, 0); }

    boolean isRunning() { return isRunning; }

    void setRunning(boolean running) {
        if (isRunning == running) return;
        isRunning = running;
        markDirty();
    }

    int getStoredItems() {
        int count = 0;
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            count += inventory.getStackInSlot(slot).getCount();
        }
        return count;
    }

    @Override public void update() {
        if (world == null || world.isRemote) return;
        EnergyTransfer.pull(this, 8192);
        if (isRunning && energy.getEnergyStored() >= ENERGY_PER_BLOCK) mineNextBlock();
        syncIfChanged();
    }

    private boolean mineNextBlock() {
        for (int checked = 0; checked < MAX_SCAN_PER_TICK; checked++) {
            int current = scanIndex;
            scanIndex = (scanIndex + 1) % SCAN_VOLUME;
            BlockPos target = positionForIndex(current);
            if (target == null || !world.isBlockLoaded(target)) continue;

            IBlockState state = world.getBlockState(target);
            Block block = state.getBlock();
            if (block.isAir(state, world, target) || world.getTileEntity(target) != null) continue;
            if (block.getBlockHardness(state, world, target) < 0.0F) continue;

            NonNullList<ItemStack> drops = NonNullList.create();
            block.getDrops(drops, world, target, state, 0);
            if (!canStoreDrops(drops)) {
                scanIndex = current;
                return false;
            }
            if (!energy.consumeInternal(ENERGY_PER_BLOCK)) return false;

            if (!world.destroyBlock(target, false)) {
                energy.addInternal(ENERGY_PER_BLOCK);
                continue;
            }

            for (ItemStack drop : drops) {
                ItemStack remainder = drop.copy();
                for (int slot = 0; slot < inventory.getSlots() && !remainder.isEmpty(); slot++) {
                    remainder = inventory.insertItem(slot, remainder, false);
                }
                if (!remainder.isEmpty()) MachineItems.drop(world, pos, remainder);
            }
            markDirty();
            return true;
        }
        return false;
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

    private BlockPos positionForIndex(int index) {
        int xOffset = index % WIDTH - MINING_RADIUS;
        int zOffset = (index / WIDTH) % WIDTH - MINING_RADIUS;
        int depth = index / (WIDTH * WIDTH);
        if (xOffset * xOffset + zOffset * zOffset > MINING_RADIUS * MINING_RADIUS) return null;
        int y = pos.getY() - 1 - depth;
        if (y < 1 || y >= world.getHeight()) return null;
        return pos.add(xOffset, -1 - depth, zOffset);
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
        state = state * 31L + (isRunning ? 1 : 0);
        state = state * 31L + scanIndex;
        return state * 31L + getStoredItems();
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
        tag.setTag("Inventory", inventory.serializeNBT());
        tag.setBoolean("IsRunning", isRunning);
        tag.setInteger("ScanIndex", scanIndex);
        return tag;
    }

    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        if (tag.hasKey("Inventory")) inventory.deserializeNBT(tag.getCompoundTag("Inventory"));
        isRunning = tag.getBoolean("IsRunning");
        scanIndex = Math.floorMod(tag.getInteger("ScanIndex"), SCAN_VOLUME);
    }
}
