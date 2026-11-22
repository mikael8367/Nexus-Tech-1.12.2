package com.mikael8367.nexustech.battery;

import com.mikael8367.nexustech.techtools.TechToolsRegistry;
import com.mikael8367.nexustech.techtools.ToolEnergy;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/**
 * Experimental, deliberately NOT connected to a tile or GUI. Plans a charge on a
 * detached stack only. A caller must atomically reserve/debit exactly amount from
 * the real battery, replace the same unchanged slot with chargedStack, respect the
 * real shared output budget, and persist both. Until then this is not a charger.
 *
 * This intentionally targets the eight exact built-in tools, not arbitrary Forge
 * energy capabilities: IEnergyStorage has no transactional failure contract.
 * The NBT key is an explicit dependency on the current ToolEnergy implementation;
 * integration must stop if that implementation changes.
 */
public final class BatteryChargePlan {
    private static final String ENERGY_KEY = "NexusToolEnergy";
    private final ItemStack charged;
    private final int amount;
    private final ItemStack original;
    private final Item item;
    private final int damage;
    private final NBTTagCompound tagSnapshot;

    private BatteryChargePlan(ItemStack original, Item item, int damage, NBTTagCompound tagSnapshot,
                              ItemStack charged, int amount) {
        this.original = original;
        this.item = item;
        this.damage = damage;
        this.tagSnapshot = tagSnapshot;
        this.charged = charged;
        this.amount = amount;
    }
    public int amount() { return amount; }
    /** Return a fresh copy, never a reference to the plan's private stack. */
    public ItemStack chargedStack() { return charged.copy(); }

    /** Necessary stale-slot check, not an atomic commit, capability snapshot,
     * lock, or protection against ABA/reentrancy. Use only with a trusted
     * inventory and a transaction that also validates the live tile and budget. */
    public boolean matchesOriginal(ItemStack current) {
        if (current != original || !supports(current) || current.getItem() != item
                || current.getItemDamage() != damage) return false;
        NBTTagCompound tag = current.getTagCompound();
        return tagSnapshot == null ? tag == null : tagSnapshot.equals(tag);
    }

    public static boolean supports(ItemStack stack) {
        if (stack == null || stack.isEmpty() || stack.getCount() != 1) return false;
        Item item = stack.getItem();
        return item == TechToolsRegistry.PICKAXE || item == TechToolsRegistry.AXE
                || item == TechToolsRegistry.SHOVEL || item == TechToolsRegistry.SWORD
                || item == TechToolsRegistry.HOE || item == TechToolsRegistry.DRILL_3
                || item == TechToolsRegistry.DRILL_5 || item == TechToolsRegistry.DRILL_10;
    }

    /** Empty or malformed input yields null. This code writes only to a copy;
     * external capability callbacks during copy may still mutate or throw. */
    public static BatteryChargePlan prepare(ItemStack stack, int available) {
        if (available <= 0 || !supports(stack)) return null;
        Item item = stack.getItem();
        int damage = stack.getItemDamage();
        NBTTagCompound tag = stack.getTagCompound();
        NBTTagCompound snapshot = tag == null ? null : tag.copy();
        int before = 0;
        if (tag != null && tag.hasKey(ENERGY_KEY)) {
            if (!tag.hasKey(ENERGY_KEY, 3)) return null;
            before = tag.getInteger(ENERGY_KEY);
            if (before < 0 || before > ToolEnergy.CAPACITY) return null;
        }
        int n = Math.min(available, ToolEnergy.CAPACITY - before);
        if (n <= 0) return null;
        ItemStack copy = stack.copy();
        BatteryChargePlan plan = new BatteryChargePlan(stack, item, damage, snapshot, copy, n);
        if (!plan.matchesOriginal(stack)) return null;
        NBTTagCompound next = copy.getTagCompound();
        if (next == null) { next = new NBTTagCompound(); copy.setTagCompound(next); }
        next.setInteger(ENERGY_KEY, before + n);
        return plan;
    }
}
