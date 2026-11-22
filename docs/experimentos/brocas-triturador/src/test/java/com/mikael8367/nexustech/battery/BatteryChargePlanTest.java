package com.mikael8367.nexustech.battery;

import com.mikael8367.nexustech.techtools.TechToolsRegistry;
import com.mikael8367.nexustech.techtools.ToolEnergy;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/** Detached-plan tests only: does not cover tile commits, drops or network. */
public final class BatteryChargePlanTest {
    static void check(boolean v, String message) { if (!v) throw new AssertionError(message); }
    public static void main(String[] args) {
        Bootstrap.register();
        Item[] tools = { TechToolsRegistry.PICKAXE, TechToolsRegistry.AXE, TechToolsRegistry.SHOVEL,
                TechToolsRegistry.SWORD, TechToolsRegistry.HOE, TechToolsRegistry.DRILL_3,
                TechToolsRegistry.DRILL_5, TechToolsRegistry.DRILL_10 };
        for (Item tool : tools) {
            ItemStack stack = new ItemStack(tool);
            NBTTagCompound t = new NBTTagCompound(); t.setString("other", "kept"); stack.setTagCompound(t);
            BatteryChargePlan p = BatteryChargePlan.prepare(stack, 1234);
            check(p != null && p.amount() == 1234, "valid plan");
            check(p.matchesOriginal(stack), "same unchanged stack accepted");
            check(!p.matchesOriginal(stack.copy()), "equal replacement rejected by identity");
            check(!p.matchesOriginal(null) && !p.matchesOriginal(ItemStack.EMPTY), "missing stack rejected");
            t.setString("other", "changed by callback");
            check(!p.matchesOriginal(stack), "changed NBT rejected");
            t.setString("other", "kept");
            NBTTagCompound nested = new NBTTagCompound(); nested.setInteger("value", 1);
            t.setTag("nested", nested);
            p = BatteryChargePlan.prepare(stack, 1234);
            nested.setInteger("value", 2);
            check(!p.matchesOriginal(stack), "deep mutation rejected");
            nested.setInteger("value", 1);
            stack.setCount(2);
            check(!p.matchesOriginal(stack), "count mutation rejected");
            stack.setCount(1);
            stack.setItemDamage(1);
            check(!p.matchesOriginal(stack), "metadata mutation rejected");
            stack.setItemDamage(0);
            check(ToolEnergy.stored(stack) == 0, "input unchanged");
            ItemStack result = p.chargedStack();
            check(ToolEnergy.stored(result) == 1234, "exact credit");
            check("kept".equals(result.getTagCompound().getString("other")), "other data preserved");
            result.getTagCompound().setInteger("NexusToolEnergy", 9);
            check(ToolEnergy.stored(p.chargedStack()) == 1234, "result copy isolated");
            t.setInteger("NexusToolEnergy", ToolEnergy.CAPACITY - 3);
            p = BatteryChargePlan.prepare(stack, Integer.MAX_VALUE);
            check(p != null && p.amount() == 3 && ToolEnergy.stored(p.chargedStack()) == ToolEnergy.CAPACITY, "capacity bound");
            check(ToolEnergy.stored(stack) == ToolEnergy.CAPACITY - 3, "bounded plan did not mutate input");
            t.setInteger("NexusToolEnergy", ToolEnergy.CAPACITY);
            check(BatteryChargePlan.prepare(stack, 1) == null, "full tool");
            for (int invalid : new int[]{ -1, ToolEnergy.CAPACITY + 1, Integer.MIN_VALUE, Integer.MAX_VALUE }) {
                t.setInteger("NexusToolEnergy", invalid);
                check(BatteryChargePlan.prepare(stack, 50) == null, "malformed energy rejected");
            }
            t.setString("NexusToolEnergy", "1");
            check(BatteryChargePlan.prepare(stack, 1) == null, "wrong NBT type rejected");
            t.removeTag("NexusToolEnergy");
            check(BatteryChargePlan.prepare(stack, 0) == null, "zero available");
            check(BatteryChargePlan.prepare(stack, -1) == null, "negative available");
            stack.setCount(2);
            check(BatteryChargePlan.prepare(stack, 1) == null, "stacked tool rejected");
        }
        check(BatteryChargePlan.prepare(ItemStack.EMPTY, 100) == null, "empty");
        check(BatteryChargePlan.prepare(null, 100) == null, "null");
        check(BatteryChargePlan.prepare(new ItemStack(Items.DIAMOND_PICKAXE), 100) == null, "untrusted item");
        Bootstrap.printToSYSOUT("PASS detached battery charge planning; no tile integration tested");
    }
}
