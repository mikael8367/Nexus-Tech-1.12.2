package com.mikael8367.nexustech.ores;

import java.util.EnumMap;
import java.util.Map;
import com.mikael8367.nexustech.NexusTech;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.SoundType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.oredict.OreDictionary;

/** Registry events are subscribed automatically; lifecycle hooks run once per process. */
@Mod.EventBusSubscriber(modid = NexusTech.MODID)
public final class OreRegistry {
    public static final Map<OreType, Block> ORES = new EnumMap<>(OreType.class);
    public static final Map<OreType, Item> INGOTS = new EnumMap<>(OreType.class);
    public static final Item STEEL_INGOT = item("steel_ingot");
    private static boolean initialized;
    private static boolean worldGeneratorRegistered;
    static {
        for (OreType type : OreType.values()) {
            Block ore = new OreBlock(type);
            ORES.put(type, ore);
            INGOTS.put(type, item(type.ingotId()));
        }
    }
    private OreRegistry() { }
    private static Item item(String id) {
        return new Item().setRegistryName(NexusTech.MODID, id).setUnlocalizedName(id)
                .setCreativeTab(NexusTech.CREATIVE_TAB);
    }
    @SubscribeEvent public static void registerBlocks(RegistryEvent.Register<Block> event) {
        for (OreType type : OreType.values()) event.getRegistry().register(ORES.get(type));
    }
    @SubscribeEvent public static void registerItems(RegistryEvent.Register<Item> event) {
        for (OreType type : OreType.values()) {
            Block ore = ORES.get(type);
            event.getRegistry().register(new ItemBlock(ore).setRegistryName(ore.getRegistryName()));
            event.getRegistry().register(INGOTS.get(type));
        }
        event.getRegistry().register(STEEL_INGOT);
    }
    /** World generation is independent of ItemBlocks and can be registered in preInit. */
    public static void registerWorldGenerator() {
        if (worldGeneratorRegistered) return;
        GameRegistry.registerWorldGenerator(new OreWorldGenerator(), 0);
        worldGeneratorRegistered = true;
    }

    /** Called in init, after Forge's block and item registry events. */
    public static void init() {
        if (initialized) return;
        // Validate all inputs before changing the dictionary or furnace recipes.
        for (OreType type : OreType.values()) {
            if (new ItemStack(ORES.get(type)).isEmpty() || new ItemStack(INGOTS.get(type)).isEmpty()) {
                throw new IllegalStateException("Ore ItemBlock or ingot not ready: " + type);
            }
        }
        for (OreType type : OreType.values()) {
            OreDictionary.registerOre(type.oreKey(), ORES.get(type));
            OreDictionary.registerOre(type.ingotKey(), INGOTS.get(type));
            GameRegistry.addSmelting(ORES.get(type), new ItemStack(INGOTS.get(type)), 0.7F);
        }
        OreDictionary.registerOre("oreAluminium", ORES.get(OreType.ALUMINUM));
        OreDictionary.registerOre("ingotAluminium", INGOTS.get(OreType.ALUMINUM));
        OreDictionary.registerOre("ingotSteel", STEEL_INGOT);
        initialized = true;
    }
}
