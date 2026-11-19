package com.mikael8367.nexustech.ores;

import java.lang.reflect.Proxy;
import java.util.*;
import com.mikael8367.nexustech.NexusTech;
import net.minecraft.block.Block;
import net.minecraft.init.Bootstrap;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.IForgeRegistryEntry;

/** Exercises actual Forge event handlers using capturing registries, without a running game. */
public final class OreRegistryTest {
    private static void check(boolean ok, String msg) { if (!ok) throw new AssertionError(msg); }
    @SuppressWarnings("unchecked")
    private static <T extends IForgeRegistryEntry<T>> IForgeRegistry<T> capture(List<T> entries, Class<T> type) {
        return (IForgeRegistry<T>) Proxy.newProxyInstance(IForgeRegistry.class.getClassLoader(),
                new Class<?>[]{IForgeRegistry.class}, (proxy, method, args) -> {
                    if (method.getName().equals("register")) { entries.add((T) args[0]); return null; }
                    if (method.getName().equals("getRegistrySuperType")) return type;
                    throw new AssertionError("unexpected registry method: " + method.getName());
                });
    }
    public static void main(String[] args) {
        Bootstrap.register();
        List<Block> blocks = new ArrayList<>(); List<Item> items = new ArrayList<>();
        OreRegistry.registerBlocks(new RegistryEvent.Register<>(new ResourceLocation("minecraft:blocks"), capture(blocks, Block.class)));
        OreRegistry.registerItems(new RegistryEvent.Register<>(new ResourceLocation("minecraft:items"), capture(items, Item.class)));
        check(blocks.size() == 10 && items.size() == 21, "registration counts");
        Set<ResourceLocation> blockIds = new HashSet<>(), itemIds = new HashSet<>();
        for (Block block : blocks) check(blockIds.add(block.getRegistryName()), "duplicate block");
        for (Item item : items) check(itemIds.add(item.getRegistryName()), "duplicate item");
        for (OreType type : OreType.values()) {
            Block ore = OreRegistry.ORES.get(type); Item ingot = OreRegistry.INGOTS.get(type);
            check(blocks.contains(ore) && items.contains(ingot), "registered " + type);
            check(ore.getRegistryName().equals(new ResourceLocation("nexustech", type.oreId())), "ore id");
            check(ingot.getRegistryName().equals(new ResourceLocation("nexustech", type.ingotId())), "ingot id");
            check(ore.getHarvestLevel(ore.getDefaultState()) == type.harvestLevel, "harvest tier");
            check("pickaxe".equals(ore.getHarvestTool(ore.getDefaultState())), "pickaxe");
            check(ore.getCreativeTabToDisplayOn() == NexusTech.CREATIVE_TAB && ingot.getCreativeTab() == NexusTech.CREATIVE_TAB, "creative tab");
            boolean found = false;
            for (Item item : items) if (item instanceof ItemBlock && ((ItemBlock)item).getBlock() == ore) {
                check(item.getRegistryName().equals(ore.getRegistryName()), "block item id"); found = true;
            }
            check(found, "block item " + type);
        }
        check(items.contains(OreRegistry.STEEL_INGOT) && OreRegistry.STEEL_INGOT.getCreativeTab() == NexusTech.CREATIVE_TAB, "steel item");
        System.out.println("PASS: Forge event handlers register ten blocks, ten block items and eleven ingots");
    }
}
