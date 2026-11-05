package br.com.mikael8367.projectestoragebridge;

import mrriegel.storagenetwork.StorageNetwork;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.registries.IForgeRegistry;

public final class BridgeRegistry {
    public static Block CABLE;

    @SubscribeEvent
    public void registerBlocks(RegistryEvent.Register<Block> event) {
        CABLE = new ProjectEStorageCableBlock("projecte_storage_cable");
        CABLE.setRegistryName(ProjectEStorageBridge.MOD_ID, "projecte_storage_cable");
        event.getRegistry().register(CABLE);
        GameRegistry.registerTileEntity(
                ProjectEStorageCableTile.class,
                new ResourceLocation(ProjectEStorageBridge.MOD_ID, "projecte_storage_cable"));
    }

    @SubscribeEvent
    public void registerItems(RegistryEvent.Register<Item> event) {
        if (CABLE == null) {
            ProjectEStorageBridge.logger().error("Cable block was not registered before its ItemBlock.");
            return;
        }
        IForgeRegistry<Item> registry = event.getRegistry();
        registry.register(new ItemBlock(CABLE).setRegistryName(CABLE.getRegistryName()));
    }

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public void registerModels(ModelRegistryEvent event) {
        if (CABLE != null) {
            ModelLoader.setCustomModelResourceLocation(
                    Item.getItemFromBlock(CABLE), 0,
                    new ModelResourceLocation(ProjectEStorageBridge.MOD_ID + ":projecte_storage_cable", "inventory"));
        }
    }
}
