package com.mikael8367.nexustech.nuclear;

import com.mikael8367.nexustech.NexusTech;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;

/** Self-registering common registry. GUI dispatch must be installed separately. */
@Mod.EventBusSubscriber(modid = NexusTech.MODID)
public final class NuclearRegistry {
    public static final NuclearBlock CONTROLLER = new NuclearBlock("nuclear_controller", NuclearBlock.Type.CONTROLLER);
    public static final NuclearBlock CASING = new NuclearBlock("nuclear_casing", NuclearBlock.Type.CASING);
    public static final NuclearBlock CORE = new NuclearBlock("nuclear_core", NuclearBlock.Type.CORE);
    public static final Block[] BLOCKS = {CONTROLLER, CASING, CORE};
    public static final Item FUEL = new Item().setRegistryName(NexusTech.MODID, "nuclear_fuel").setUnlocalizedName(NexusTech.MODID + ".nuclear_fuel").setCreativeTab(NexusTech.CREATIVE_TAB);
    private NuclearRegistry() { }
    @SubscribeEvent public static void blocks(RegistryEvent.Register<Block> event) {
        for (Block b : BLOCKS) event.getRegistry().register(b);
        GameRegistry.registerTileEntity(NuclearController.class, new ResourceLocation(NexusTech.MODID, "nuclear_controller"));
        GameRegistry.registerTileEntity(NuclearPart.class, new ResourceLocation(NexusTech.MODID, "nuclear_part"));
    }
    @SubscribeEvent public static void items(RegistryEvent.Register<Item> event) {
        for (Block b : BLOCKS) event.getRegistry().register(new ItemBlock(b).setRegistryName(b.getRegistryName()));
        event.getRegistry().register(FUEL);
    }
}
