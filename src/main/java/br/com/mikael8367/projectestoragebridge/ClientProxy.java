package br.com.mikael8367.projectestoragebridge;

import mrriegel.storagenetwork.block.cable.TesrCable;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

public final class ClientProxy extends CommonProxy {
    private static final ResourceLocation CABLE_TEXTURE = new ResourceLocation(
            ProjectEStorageBridge.MOD_ID, "textures/tile/projecte_storage_cable.png");

    private static void registerCableTexture() {
        if (BridgeRegistry.CABLE != null) {
            TesrCable.addCableRender(BridgeRegistry.CABLE, CABLE_TEXTURE);
        }
    }

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
        // Use the dedicated renderer: SSN's render map is static and shared by
        // all cable blocks, so the Bridge entry must be restored before each draw.
        ClientRegistry.bindTileEntitySpecialRenderer(
                ProjectEStorageCableTile.class, new BridgeCableRenderer());
    }

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        // Block registry events have completed by init; this installs the
        // initial mapping. BridgeCableRenderer refreshes it before every render.
        registerCableTexture();
    }
}
