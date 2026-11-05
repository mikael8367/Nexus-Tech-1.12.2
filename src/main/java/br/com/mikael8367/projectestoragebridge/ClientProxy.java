package br.com.mikael8367.projectestoragebridge;

import mrriegel.storagenetwork.block.cable.TesrCable;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

/** Client-only registration for Simple Storage Network's special cable renderer. */
public final class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
        ClientRegistry.bindTileEntitySpecialRenderer(
                ProjectEStorageCableTile.class, new TesrCable());
    }

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        if (BridgeRegistry.CABLE != null) {
            TesrCable.addCableRender(
                    BridgeRegistry.CABLE,
                    new ResourceLocation(ProjectEStorageBridge.MOD_ID,
                            "textures/tile/projecte_storage_cable.png"));
        }
    }
}
