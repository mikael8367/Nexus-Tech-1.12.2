package com.mikael8367.nexustech.ores;

import com.mikael8367.nexustech.NexusTech;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Discovered automatically on client only; do not invoke from common/server code. */
@SideOnly(Side.CLIENT)
@Mod.EventBusSubscriber(modid = NexusTech.MODID, value = Side.CLIENT)
public final class OreClient {
    private OreClient() { }
    @SubscribeEvent public static void models(ModelRegistryEvent event) {
        for (OreType type : OreType.values()) {
            model(Item.getItemFromBlock(OreRegistry.ORES.get(type)));
            model(OreRegistry.INGOTS.get(type));
        }
        model(OreRegistry.STEEL_INGOT);
    }
    private static void model(Item item) {
        ModelLoader.setCustomModelResourceLocation(item, 0, new ModelResourceLocation(item.getRegistryName(), "inventory"));
    }
}
