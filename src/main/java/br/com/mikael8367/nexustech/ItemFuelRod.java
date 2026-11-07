package br.com.mikael8367.nexustech;

import java.util.List;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Uranium fuel rod with bounded, persistent burn progress stored on the stack. */
final class ItemFuelRod extends Item {
    static final int MAX_BURN_TIME = 24000;
    private static final String BURN_TIME_KEY = "BurnTime";

    ItemFuelRod() { setMaxStackSize(1); }

    int getBurnTimeRemaining(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.hasTagCompound()) return MAX_BURN_TIME;
        int consumed = Math.max(0, Math.min(MAX_BURN_TIME, stack.getTagCompound().getInteger(BURN_TIME_KEY)));
        return MAX_BURN_TIME - consumed;
    }

    /** Consumes at most the remaining burn time; malformed NBT is clamped, never trusted. */
    int consumeBurnTime(ItemStack stack, int ticks) {
        if (stack == null || stack.isEmpty() || ticks <= 0) return 0;
        int remaining = getBurnTimeRemaining(stack);
        int used = Math.min(remaining, ticks);
        NBTTagCompound tag = stack.hasTagCompound() ? stack.getTagCompound() : new NBTTagCompound();
        tag.setInteger(BURN_TIME_KEY, MAX_BURN_TIME - remaining + used);
        stack.setTagCompound(tag);
        return used;
    }

    @SideOnly(Side.CLIENT)
    @Override public double getDurabilityForDisplay(ItemStack stack) {
        return 1.0D - (double) getBurnTimeRemaining(stack) / (double) MAX_BURN_TIME;
    }

    @SideOnly(Side.CLIENT)
    @Override public int getRGBDurabilityForDisplay(ItemStack stack) {
        return 0x65C94B;
    }

    @SideOnly(Side.CLIENT)
    @Override public void addInformation(ItemStack stack, World world, List<String> tooltip, ITooltipFlag flag) {
        int remaining = getBurnTimeRemaining(stack);
        int percent = (int) ((long) remaining * 100L / MAX_BURN_TIME);
        tooltip.add(TextFormatting.GREEN + "Combustível restante: " + percent + "%");
        tooltip.add(TextFormatting.GRAY.toString() + (remaining / 20) + " segundos");
    }
}
