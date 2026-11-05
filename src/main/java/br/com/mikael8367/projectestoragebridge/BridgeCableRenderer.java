package br.com.mikael8367.projectestoragebridge;

import mrriegel.storagenetwork.block.cable.TesrCable;
import mrriegel.storagenetwork.block.cable.TileCable;
import net.minecraft.util.ResourceLocation;

public final class BridgeCableRenderer extends TesrCable {
    private static final ResourceLocation TEXTURE = new ResourceLocation(
            ProjectEStorageBridge.MOD_ID, "textures/tile/projecte_storage_cable.png");

    @Override
    public void render(TileCable tile, double x, double y, double z,
                       float partialTicks, int destroyStage, float partial) {
        if (!(tile instanceof ProjectEStorageCableTile)) {
            return;
        }
        // SSN's renderer has a shared static texture map. Refresh our entry before
        // every draw so a stale/missing mapping cannot reuse another cable's texture.
        TesrCable.addCableRender(tile.getBlockType(), TEXTURE);
        super.render(tile, x, y, z, partialTicks, destroyStage, partial);
    }
}
