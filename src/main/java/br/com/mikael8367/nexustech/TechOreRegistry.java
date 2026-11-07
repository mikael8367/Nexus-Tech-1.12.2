package br.com.mikael8367.nexustech;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Dedicated declarations for ore blocks; the main TechRegistry owns registration events. */
final class TechOreRegistry {
    static final Block URANIUM_ORE = new BlockTechOre("uranium_ore", TechItems.URANIUM_CHUNK);
    static final Block COPPER_ORE = new BlockTechOre("copper_ore", TechItems.COPPER_INGOT);
    static final Block TIN_ORE = new BlockTechOre("tin_ore", TechItems.TIN_INGOT);

    private TechOreRegistry() { }
}
