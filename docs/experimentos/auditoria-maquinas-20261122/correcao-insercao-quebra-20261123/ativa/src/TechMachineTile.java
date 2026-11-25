package com.mikael8367.nexustech.techmachines;

import javax.annotation.Nullable;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.init.PotionTypes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.PotionUtils;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;

/** Two slots: input and output. No client-authoritative mutation. */
public abstract class TechMachineTile extends TileEntity implements ITickable {
    public static final int CAPACITY = 20000, RATE = 1000, COST = 20, TIME = 80;
    private final int kind;
    private int energy, progress;
    private boolean busy;
    private final MachineInventory inventory = new MachineInventory();
    private final class MachineInventory extends ItemStackHandler {
        MachineInventory() { super(2); }
        @Override protected void onContentsChanged(int slot) {
            if (slot == 0) progress = 0;
            markDirty();
        }
        // Assignments contain no external notification; caller must prepare all copies first.
        void commitInput(ItemStack input) { stacks.set(0, input); }
        void commit(ItemStack input, ItemStack output) {
            stacks.set(0, input);
            stacks.set(1, output);
        }
    }
    private final IItemHandler access = new IItemHandler() {
        public int getSlots() { return 2; }
        public ItemStack getStackInSlot(int slot) { return slot < 0 || slot > 1 ? ItemStack.EMPTY : inventory.getStackInSlot(slot).copy(); }
        public int getSlotLimit(int slot) { return slot < 0 || slot > 1 ? 0 : inventory.getSlotLimit(slot); }
        public boolean isItemValid(int slot, ItemStack stack) { return slot == 0 && validInput(stack); }
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (busy || slot != 0) return stack;
            busy = true;
            try {
                if (!live() || !validInput(stack)) return stack;
                if (simulate) return inventory.insertItem(0, stack, true);
                // Preflight on copies, then notify BEFORE ownership is transferred. An
                // exception or break during notification cannot expose a new item twice.
                ItemStack offered = stack.copy();
                ItemStack before = inventory.getStackInSlot(0).copy();
                int oldProgress = progress;
                ItemStack remainder = inventory.insertItem(0, offered, true).copy();
                int accepted = offered.getCount() - remainder.getCount();
                if (accepted <= 0) return stack;
                ItemStack after = before.isEmpty() ? offered.copy() : before.copy();
                if (before.isEmpty()) after.setCount(accepted); else after.grow(accepted);
                markDirty();
                // Notification may drain, replace/unload the tile or mutate the offer.
                if (!live() || !validInput(stack) || progress != oldProgress
                        || !ItemStack.areItemStacksEqual(offered, stack)
                        || !ItemStack.areItemStacksEqual(before, inventory.getStackInSlot(0))) return stack;
                inventory.commitInput(after);
                progress = 0;
                return remainder;
            } finally { busy = false; }
        }
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (busy || slot < 0 || slot > 1 || amount <= 0) return ItemStack.EMPTY;
            busy = true;
            try { return live() ? inventory.extractItem(slot, amount, simulate) : ItemStack.EMPTY; }
            finally { busy = false; }
        }
    };
    private final IEnergyStorage power = new IEnergyStorage() {
        public int receiveEnergy(int amount, boolean simulate) {
            if (busy || amount <= 0) return 0;
            busy = true;
            try {
                if (!live()) return 0;
                int received = Math.min(Math.min(amount, RATE), CAPACITY - energy);
                if (!simulate && received > 0) { energy += received; markDirty(); }
                return received;
            } finally { busy = false; }
        }
        public int extractEnergy(int amount, boolean simulate) { return 0; }
        public int getEnergyStored() { return energy; }
        public int getMaxEnergyStored() { return CAPACITY; }
        public boolean canExtract() { return false; }
        public boolean canReceive() { return true; }
    };
    protected TechMachineTile(int kind) { this.kind = kind; }
    public static final class Sawmill extends TechMachineTile { public Sawmill() { super(0); } }
    public static final class Composter extends TechMachineTile { public Composter() { super(1); } }
    public static final class WaterBottler extends TechMachineTile { public WaterBottler() { super(2); } }
    int getEnergy() { return energy; }
    private boolean live() { return world != null && !world.isRemote && !isInvalid() && world.isBlockLoaded(pos) && world.getTileEntity(pos) == this; }
    private boolean validInput(ItemStack s) {
        if (s.isEmpty() || s.hasTagCompound() || s.getItem().hasContainerItem(s)) return false;
        Item item = s.getItem(); int meta = s.getMetadata();
        if (kind == 0) return (item == Item.getItemFromBlock(Blocks.LOG) && meta >= 0 && meta <= 15) || (item == Item.getItemFromBlock(Blocks.LOG2) && meta >= 0 && meta <= 15 && (meta & 3) <= 1);
        if (kind == 1) return (meta == 0 && (item == Items.WHEAT_SEEDS || item == Items.BEETROOT_SEEDS || item == Items.MELON_SEEDS || item == Items.PUMPKIN_SEEDS)) || (item == Item.getItemFromBlock(Blocks.SAPLING) && meta >= 0 && meta <= 5);
        return item == Items.GLASS_BOTTLE && meta == 0;
    }
    private int inputCount() { return kind == 1 ? 8 : 1; }
    private boolean adjacentWater() {
        for (EnumFacing face : EnumFacing.VALUES) {
            BlockPos next = pos.offset(face);
            if (!world.isBlockLoaded(next)) continue;
            net.minecraft.block.state.IBlockState state = world.getBlockState(next);
            Block block = state.getBlock();
            if ((block == Blocks.WATER || block == Blocks.FLOWING_WATER) && state.getValue(net.minecraft.block.BlockLiquid.LEVEL) == 0) return true;
        }
        return false;
    }
    private ItemStack result() {
        ItemStack in = inventory.getStackInSlot(0);
        if (!validInput(in) || in.getCount() < inputCount()) return ItemStack.EMPTY;
        if (kind == 0) {
            int wood = (in.getMetadata() & 3) + (in.getItem() == Item.getItemFromBlock(Blocks.LOG2) ? 4 : 0);
            return new ItemStack(Blocks.PLANKS, 6, wood);
        }
        if (kind == 1) return new ItemStack(Items.DYE, 1, 15);
        if (!adjacentWater()) return ItemStack.EMPTY;
        return PotionUtils.addPotionToItemStack(new ItemStack(Items.POTIONITEM), PotionTypes.WATER);
    }
    private boolean fits(ItemStack result) {
        if (result.isEmpty()) return false;
        ItemStack out = inventory.getStackInSlot(1);
        int limit = Math.min(inventory.getSlotLimit(1), result.getMaxStackSize());
        return out.isEmpty() ? result.getCount() <= limit : ItemStack.areItemsEqual(out, result)
                && ItemStack.areItemStackTagsEqual(out, result) && out.getCount() <= limit - result.getCount();
    }
    private void pullEnergy() {
        if (!live()) return;
        World expected = world; BlockPos origin = pos.toImmutable();
        for (EnumFacing side : EnumFacing.VALUES) {
                if (world != expected || !origin.equals(pos) || !live() || energy >= COST) break;
                BlockPos next = origin.offset(side);
                if (!expected.isBlockLoaded(next)) continue;
                try {
                    TileEntity source = expected.getTileEntity(next);
                    if (source == null || source.isInvalid()) continue;
                    IEnergyStorage handler = source.getCapability(CapabilityEnergy.ENERGY, side.getOpposite());
                    if (handler == null || !handler.canExtract()) continue;
                    if (world != expected || !origin.equals(pos) || !live() || !expected.isBlockLoaded(next) || expected.getTileEntity(next) != source || source.isInvalid() || source.getCapability(CapabilityEnergy.ENERGY, side.getOpposite()) != handler) continue;
                    // The second foreign capability lookup may itself invalidate either endpoint.
                    if (world != expected || !origin.equals(pos) || !live() || !expected.isBlockLoaded(next) || expected.getTileEntity(next) != source || source.isInvalid()) continue;
                    int request = Math.min(RATE, CAPACITY - energy);
                    int extracted = handler.extractEnergy(request, false);
                    if (world != expected || !origin.equals(pos) || !live()) break;
                    if (extracted > 0 && extracted <= request) { energy += extracted; markDirty(); }
                } catch (RuntimeException ignored) { /* Unknown foreign mutation must not credit FE. */ }
        }
    }
    @Override public void update() {
        if (busy) return;
        busy = true;
        try {
            if (!live()) return;
            ItemStack product = result();
            if (!live()) return;
            if (!fits(product)) { if (progress != 0) { progress = 0; markDirty(); } return; }
            if (energy < COST) {
                pullEnergy();
                if (!live()) return;
                product = result();
                if (!live()) return;
                if (!fits(product)) { if (progress != 0) { progress = 0; markDirty(); } return; }
            }
            if (energy < COST) return;
            // A live/world/water callback can drain input after result(). Verify a
            // fresh recipe and snapshots *after* the last world callback on every tick.
            ItemStack tickInput = inventory.getStackInSlot(0).copy();
            ItemStack tickOutput = inventory.getStackInSlot(1).copy();
            int tickEnergy = energy, tickProgress = progress;
            ItemStack checked = result();
            if (!ItemStack.areItemStacksEqual(product, checked) || !fits(checked) || !live()
                    || energy != tickEnergy || progress != tickProgress || energy < COST
                    || !ItemStack.areItemStacksEqual(tickInput, inventory.getStackInSlot(0))
                    || !ItemStack.areItemStacksEqual(tickOutput, inventory.getStackInSlot(1))) return;
            product = checked;
            if (progress + 1 < TIME) {
                energy -= COST;
                progress++;
            } else {
                // Prepare off-inventory; no mutation or notification until all copies succeed.
                ItemStack beforeInput = inventory.getStackInSlot(0).copy();
                ItemStack beforeOutput = inventory.getStackInSlot(1).copy();
                ItemStack input = beforeInput.copy(); input.shrink(inputCount());
                ItemStack output = beforeOutput.isEmpty() ? product.copy() : beforeOutput.copy();
                if (!beforeOutput.isEmpty()) output.grow(product.getCount());
                // Recheck result, endpoint and snapshots after any world/item callbacks.
                if (!ItemStack.areItemStacksEqual(product, result()) || !fits(product) || !live()
                        || progress + 1 < TIME || energy < COST
                        || !ItemStack.areItemStacksEqual(beforeInput, inventory.getStackInSlot(0))
                        || !ItemStack.areItemStacksEqual(beforeOutput, inventory.getStackInSlot(1))) return;
                // No callbacks between these assignments and accounting. markDirty follows the commit.
                inventory.commit(input.isEmpty() ? ItemStack.EMPTY : input, output);
                energy -= COST;
                progress = 0;
            }
            markDirty();
        } finally { busy = false; }
    }
    ItemStack drain(int slot) { return inventory.extractItem(slot, Integer.MAX_VALUE, false); }
    @Override public boolean hasCapability(Capability<?> cap, @Nullable EnumFacing side) {
        return cap == CapabilityEnergy.ENERGY || cap == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY || super.hasCapability(cap, side);
    }
    @Override public <T> T getCapability(Capability<T> cap, @Nullable EnumFacing side) {
        if (cap == CapabilityEnergy.ENERGY) return CapabilityEnergy.ENERGY.cast(power);
        if (cap == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(access);
        return super.getCapability(cap, side);
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag); tag.setInteger("Energy", energy); tag.setInteger("Progress", progress); tag.setTag("Inventory", inventory.serializeNBT()); return tag;
    }
    @Override public void readFromNBT(NBTTagCompound tag) {
        if (busy) return;
        busy = true;
        try {
            super.readFromNBT(tag);
            energy = Math.max(0, Math.min(CAPACITY, tag.getInteger("Energy")));
            if (tag.hasKey("Inventory", 10)) { NBTTagCompound saved = tag.getCompoundTag("Inventory").copy(); saved.setInteger("Size", 2); inventory.deserializeNBT(saved); }
            progress = Math.max(0, Math.min(TIME - 1, tag.getInteger("Progress")));
        } finally { busy = false; }
    }
}
