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

public final class TileElectricAnvil extends AbstractEnergyTile implements ITickable {
    static final int ENERGY_PER_DURABILITY = 25;
    static final int REPAIR_PER_TICK = 4;
    private final ItemStackHandler inventory = new ValidatedItemHandler(1) {
        @Override protected void onContentsChanged(int slot) { markDirty(); }
        @Override public int getSlotLimit(int slot) { return 1; }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return slot == 0 && isRepairable(stack); }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return slot == 0 && isFullyRepaired() ? super.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
        }
    };
    public TileElectricAnvil() { super(100000, 1024, 0); }
    int getRepairDamage() { ItemStack stack = inventory.getStackInSlot(0); return stack.isEmpty() ? 0 : stack.getItemDamage(); }
    boolean hasRepairItem() { return !inventory.getStackInSlot(0).isEmpty(); }
    static boolean isRepairable(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getCount() == 1
                && stack.isItemStackDamageable() && stack.getItemDamage() > 0;
    }
    boolean isEmpty() { return inventory.getStackInSlot(0).isEmpty(); }
    boolean insertItem(ItemStack stack) {
        if (!isEmpty() || !isRepairable(stack)) return false;
        return inventory.insertItem(0, stack.copy(), false).isEmpty();
    }
    boolean isFullyRepaired() {
        ItemStack stack = inventory.getStackInSlot(0);
        return !stack.isEmpty() && stack.getItemDamage() == 0;
    }
    boolean takeOutput(EntityPlayer player) {
        ItemStack out = inventory.extractItem(0, 1, false);
        if (out.isEmpty()) return false;
        MachineItems.giveOrDrop(player, out);
        return true;
    }
    @Override public void update() {
        if (world == null || world.isRemote) return;
        EnergyTransfer.pull(this, 1024);
        ItemStack item = inventory.getStackInSlot(0);
        if (!item.isEmpty() && item.isItemStackDamageable() && item.getItemDamage() > 0) {
            // Prepare the repaired result before charging energy. The server tick is single-threaded,
            // but re-check the slot snapshot so a changed/removed stack is never charged for.
            int originalDamage = item.getItemDamage();
            int points = RepairMath.points(originalDamage, energy.getEnergyStored(),
                    REPAIR_PER_TICK, ENERGY_PER_DURABILITY);
            if (points > 0) {
                ItemStack repaired = item.copy();
                repaired.setItemDamage(Math.max(0, originalDamage - points));
                ItemStack current = inventory.getStackInSlot(0);
                int cost = RepairMath.energyCost(points, ENERGY_PER_DURABILITY);
                if (ItemStack.areItemStacksEqual(current, item) && energy.consumeInternal(cost)) {
                    inventory.setStackInSlot(0, repaired);
                }
            }
        }
        syncIfChanged();
    }
    @Override protected long syncState() {
        long h = energy.getEnergyStored();
        h = h * 31 + getRepairDamage();
        h = h * 31 + (hasRepairItem() ? 1 : 0);
        return h;
    }
    void dropContents() {
        if (world == null || world.isRemote) return;
        MachineItems.drop(world, pos, inventory.getStackInSlot(0).copy());
        inventory.setStackInSlot(0, ItemStack.EMPTY);
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
        return super.writeToNBT(tag);
    }
    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        if (tag.hasKey("Inventory")) inventory.deserializeNBT(tag.getCompoundTag("Inventory"));
    }
}
