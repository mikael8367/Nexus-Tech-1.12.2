package com.mikael8367.nexustech.digitalmine;

import com.mikael8367.nexustech.NexusTech;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;

/** Block/item events are discovered by Forge. Call registerTiles from preInit. */
@Mod.EventBusSubscriber(modid = NexusTech.MODID)
public final class DigitalMineRegistry {
    public static final DigitalMineBlock BASIC = new DigitalMineBlock("digital_mine_basic", false);
    public static final DigitalMineBlock ADVANCED = new DigitalMineBlock("digital_mine_advanced", true);
    public static final DigitalMineCasing CASING = new DigitalMineCasing();
    public static final Block[] BLOCKS = { BASIC, ADVANCED, CASING };
    public static final int GUI_ID = 71;
    private DigitalMineRegistry() {}
    @SubscribeEvent public static void blocks(RegistryEvent.Register<Block> e) { for (Block b : BLOCKS) e.getRegistry().register(b); }
    @SubscribeEvent public static void items(RegistryEvent.Register<Item> e) { for (Block b : BLOCKS) e.getRegistry().register(new ItemBlock(b).setRegistryName(b.getRegistryName())); }
    public static void registerTiles() {
        GameRegistry.registerTileEntity(DigitalMineTile.Basic.class, new ResourceLocation(NexusTech.MODID, "digital_mine_basic"));
        GameRegistry.registerTileEntity(DigitalMineTile.Advanced.class, new ResourceLocation(NexusTech.MODID, "digital_mine_advanced"));
    }
}
