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

final class MachineItems {
    private MachineItems() { }
    /** Gives a stack to the player and drops whatever does not fit (addItemStackToInventory can be partial). */
    static void giveOrDrop(EntityPlayer player, ItemStack stack) {
        if (stack.isEmpty()) return;
        player.inventory.addItemStackToInventory(stack);
        if (!stack.isEmpty()) player.dropItem(stack, false);
        player.inventoryContainer.detectAndSendChanges();
    }
    static void drop(World world, net.minecraft.util.math.BlockPos pos, ItemStack stack) {
        if (stack.isEmpty()) return;
        world.spawnEntity(new EntityItem(world, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, stack));
    }
}

/** Item handler that actually enforces isItemValid (Forge 1.12 ItemStackHandler does not). */
class ValidatedItemHandler extends ItemStackHandler {
    ValidatedItemHandler(int slots) { super(slots); }
    @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty() || !isItemValid(slot, stack)) return stack;
        return super.insertItem(slot, stack, simulate);
    }
}

final class RepairMath {
    private RepairMath() { }
    static int points(int damage, int stored, int limit, int cost) {
        if (damage <= 0 || stored <= 0 || limit <= 0 || cost <= 0) return 0;
        return Math.min(damage, Math.min(limit, stored / cost));
    }
    static int energyCost(int points, int cost) {
        if (points <= 0 || cost <= 0) return 0;
        long result = (long) points * cost;
        return result > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) result;
    }
}
