package br.com.mikael8367.nexustech;

import net.minecraft.item.Item;

/** Registry-backed items used by Nexus Tech machines, tools and ore processing. */
public final class TechItems {
    public static final Item URANIUM_CHUNK = item("uranium_chunk", 64);
    public static final Item URANIUM_INGOT = item("uranium_ingot", 64);
    public static final Item COPPER_INGOT = item("copper_ingot", 64);
    public static final Item TIN_INGOT = item("tin_ingot", 64);
    public static final Item URANIUM_FUEL_ROD = new ItemFuelRod().setRegistryName(NexusTechMod.MOD_ID, "uranium_fuel_rod")
            .setTranslationKey(NexusTechMod.MOD_ID + ".uranium_fuel_rod").setCreativeTab(NexusTechCreativeTab.INSTANCE);
    public static final Item URANIUM_FUEL_ROD_DEPLETED = item("uranium_fuel_rod_depleted", 16);
    public static final Item WRENCH = new ItemWrench().setRegistryName(NexusTechMod.MOD_ID, "wrench")
            .setTranslationKey(NexusTechMod.MOD_ID + ".wrench").setCreativeTab(NexusTechCreativeTab.INSTANCE);
    public static final Item CABLE_CONFIGURATOR = new ItemCableConfigurator()
            .setRegistryName(NexusTechMod.MOD_ID, "cable_configurator")
            .setTranslationKey(NexusTechMod.MOD_ID + ".cable_configurator").setCreativeTab(NexusTechCreativeTab.INSTANCE);
    public static final Item BLOCK_SCANNER = new ItemBlockScanner().setRegistryName(NexusTechMod.MOD_ID, "block_scanner")
            .setTranslationKey(NexusTechMod.MOD_ID + ".block_scanner").setCreativeTab(NexusTechCreativeTab.INSTANCE);
    public static final Item MULTIMETER = new ItemMultimeter().setRegistryName(NexusTechMod.MOD_ID, "multimeter")
            .setTranslationKey(NexusTechMod.MOD_ID + ".multimeter").setCreativeTab(NexusTechCreativeTab.INSTANCE);
    public static final Item SPEED_UPGRADE = item("speed_upgrade",  stackSizeOne());
    public static final Item FURNACE_UPGRADE = item("furnace_upgrade", stackSizeOne());
    public static final Item ENERGY_UPGRADE = item("energy_upgrade", stackSizeOne());

    public static final Item[] ALL = {
        URANIUM_CHUNK, URANIUM_INGOT, COPPER_INGOT, TIN_INGOT, URANIUM_FUEL_ROD,
        URANIUM_FUEL_ROD_DEPLETED, WRENCH, CABLE_CONFIGURATOR, BLOCK_SCANNER, MULTIMETER,
        SPEED_UPGRADE, FURNACE_UPGRADE, ENERGY_UPGRADE
    };

    private TechItems() { }

    private static int stackSizeOne() { return 1; }

    private static Item item(String name, int stackSize) {
        return new Item().setRegistryName(NexusTechMod.MOD_ID, name)
                .setTranslationKey(NexusTechMod.MOD_ID + "." + name)
                .setCreativeTab(NexusTechCreativeTab.INSTANCE).setMaxStackSize(stackSize);
    }
}
