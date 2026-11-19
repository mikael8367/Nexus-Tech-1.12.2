package com.mikael8367.nexustech;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;

@Mod.EventBusSubscriber(modid = NexusTech.MODID)
public final class ModRegistry {
    public static final MachineBlock CABLE = new MachineBlock("energy_cable", MachineTile.Kind.CABLE);
    public static final MachineBlock SOLAR = new MachineBlock("solar_panel", MachineTile.Kind.SOLAR);
    public static final MachineBlock GENERATOR = new MachineBlock("coal_generator", MachineTile.Kind.GENERATOR);
    public static final MachineBlock TURBINE = new MachineBlock("electric_turbine", MachineTile.Kind.TURBINE);
    public static final MachineBlock BATTERY = new MachineBlock("battery", MachineTile.Kind.BATTERY);
    public static final MachineBlock FURNACE = new MachineBlock("electric_furnace", MachineTile.Kind.FURNACE);
    public static final MachineBlock CRUSHER = new MachineBlock("electric_crusher", MachineTile.Kind.CRUSHER);
    public static final MachineBlock COMPRESSOR = new MachineBlock("electric_compressor", MachineTile.Kind.COMPRESSOR);
    public static final MachineBlock LAVA_GENERATOR = new MachineBlock("lava_generator", MachineTile.Kind.LAVA_GENERATOR);
    public static final MachineBlock[] BLOCKS = { CABLE, SOLAR, GENERATOR, TURBINE, BATTERY, FURNACE, CRUSHER, COMPRESSOR, LAVA_GENERATOR };
    private ModRegistry() { }
    @SubscribeEvent public static void blocks(RegistryEvent.Register<Block> event) {
        for (Block block : BLOCKS) event.getRegistry().register(block);
    }
    @SubscribeEvent public static void items(RegistryEvent.Register<Item> event) {
        for (Block block : BLOCKS) event.getRegistry().register(new ItemBlock(block).setRegistryName(block.getRegistryName()));
    }
    public static void registerTiles() {
        GameRegistry.registerTileEntity(MachineTile.Cable.class, id("energy_cable"));
        GameRegistry.registerTileEntity(MachineTile.Solar.class, id("solar_panel"));
        GameRegistry.registerTileEntity(MachineTile.Generator.class, id("coal_generator"));
        GameRegistry.registerTileEntity(MachineTile.Turbine.class, id("electric_turbine"));
        GameRegistry.registerTileEntity(MachineTile.Battery.class, id("battery"));
        GameRegistry.registerTileEntity(MachineTile.Furnace.class, id("electric_furnace"));
        GameRegistry.registerTileEntity(MachineTile.Crusher.class, id("electric_crusher"));
        GameRegistry.registerTileEntity(MachineTile.Compressor.class, id("electric_compressor"));
        GameRegistry.registerTileEntity(MachineTile.LavaGenerator.class, id("lava_generator"));
    }
    private static ResourceLocation id(String path) { return new ResourceLocation(NexusTech.MODID, path); }
}
