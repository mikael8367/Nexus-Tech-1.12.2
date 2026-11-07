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

public final class TileElectricFurnace extends AbstractEnergyTile implements ITickable {
    static final int FE_PER_TICK = 10;
    static final int COOK_TICKS = 200;
    private int cookTime;
    private float storedXp;
    private final ItemStackHandler inventory = new ValidatedItemHandler(2) {
        @Override protected void onContentsChanged(int slot) { markDirty(); }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return slot == 0 && isSmeltable(stack); }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return slot == 1 ? super.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
        }
    };
    public TileElectricFurnace() { super(12000, 512, 0); }
    int getCookTime() { return cookTime; }
    int getInputCount() { return inventory.getStackInSlot(0).getCount(); }
    int getOutputCount() { return inventory.getStackInSlot(1).getCount(); }
    static boolean isSmeltable(ItemStack stack) {
        return stack != null && !stack.isEmpty() && !FurnaceRecipes.instance().getSmeltingResult(stack).isEmpty();
    }
    int insertInput(ItemStack stack) {
        if (!isSmeltable(stack)) return 0;
        int before = stack.getCount();
        ItemStack remainder = inventory.insertItem(0, stack.copy(), false);
        return before - remainder.getCount();
    }
    boolean takeOutput(EntityPlayer player) {
        ItemStack output = inventory.extractItem(1, 64, false);
        if (output.isEmpty()) return false;
        MachineItems.giveOrDrop(player, output);
        releaseXp();
        return true;
    }
    /** Electric smelting grants the same experience as the vanilla furnace when output is taken. */
    private void releaseXp() {
        int xp = MathHelper.floor(storedXp);
        float fraction = storedXp - xp;
        if (fraction > 0.0F && world.rand.nextFloat() < fraction) xp++;
        storedXp = 0.0F;
        markDirty();
        while (xp > 0) {
            int split = EntityXPOrb.getXPSplit(xp);
            xp -= split;
            world.spawnEntity(new EntityXPOrb(world, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, split));
        }
    }
    private boolean canSmelt() {
        ItemStack input = inventory.getStackInSlot(0);
        if (input.isEmpty() || energy.getEnergyStored() < FE_PER_TICK) return false;
        ItemStack result = FurnaceRecipes.instance().getSmeltingResult(input);
        if (result.isEmpty()) return false;
        ItemStack output = inventory.getStackInSlot(1);
        if (output.isEmpty()) return result.getCount() <= Math.min(result.getMaxStackSize(), inventory.getSlotLimit(1));
        if (!ItemStack.areItemsEqual(output, result) || !ItemStack.areItemStackTagsEqual(output, result)) return false;
        return output.getCount() + result.getCount() <= Math.min(output.getMaxStackSize(), inventory.getSlotLimit(1));
    }
    private void smeltOne() {
        ItemStack input = inventory.getStackInSlot(0);
        ItemStack result = FurnaceRecipes.instance().getSmeltingResult(input).copy();
        storedXp = Math.min(1000.0F, storedXp + FurnaceRecipes.instance().getSmeltingExperience(result));
        ItemStack output = inventory.getStackInSlot(1);
        if (output.isEmpty()) inventory.setStackInSlot(1, result);
        else {
            ItemStack merged = output.copy();
            merged.grow(result.getCount());
            inventory.setStackInSlot(1, merged);
        }
        ItemStack remaining = input.copy();
        remaining.shrink(1);
        inventory.setStackInSlot(0, remaining.isEmpty() ? ItemStack.EMPTY : remaining);
    }
    @Override public void update() {
        if (world == null || world.isRemote) return;
        EnergyTransfer.pull(this, 512);
        if (canSmelt() && energy.consumeInternal(FE_PER_TICK)) {
            cookTime++;
            if (cookTime >= COOK_TICKS) {
                smeltOne();
                cookTime = 0;
            }
        } else if (cookTime > 0) {
            cookTime = Math.max(0, cookTime - 2);
        }
        syncIfChanged();
    }
    @Override protected long syncState() {
        long h = energy.getEnergyStored();
        h = h * 31 + cookTime;
        h = h * 31 + inventory.getStackInSlot(0).getCount();
        h = h * 31 + inventory.getStackInSlot(1).getCount();
        return h;
    }
    void dropContents() {
        if (world == null || world.isRemote) return;
        for (int i = 0; i < inventory.getSlots(); i++) {
            MachineItems.drop(world, pos, inventory.getStackInSlot(i).copy());
            inventory.setStackInSlot(i, ItemStack.EMPTY);
        }
    }
    @Override public boolean hasCapability(net.minecraftforge.common.capabilities.Capability<?> cap, net.minecraft.util.EnumFacing side) {
        return cap == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY || super.hasCapability(cap, side);
    }
    @Override @SuppressWarnings("unchecked")
    public <T> T getCapability(net.minecraftforge.common.capabilities.Capability<T> cap, net.minecraft.util.EnumFacing side) {
        return cap == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY ? (T) inventory : super.getCapability(cap, side);
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        tag.setTag("Inventory", inventory.serializeNBT());
        tag.setInteger("CookTime", cookTime);
        tag.setFloat("StoredXp", storedXp);
        return super.writeToNBT(tag);
    }
    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        if (tag.hasKey("Inventory")) inventory.deserializeNBT(tag.getCompoundTag("Inventory"));
        cookTime = Math.max(0, Math.min(COOK_TICKS - 1, tag.getInteger("CookTime")));
        storedXp = Math.max(0.0F, Math.min(1000.0F, tag.getFloat("StoredXp")));
    }
}
