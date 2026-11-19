package com.mikael8367.nexustech;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public final class MachineGui extends GuiContainer {
    private static final ResourceLocation TEXTURE = new ResourceLocation("textures/gui/container/furnace.png");
    private final MachineTile tile;
    public MachineGui(InventoryPlayer player, MachineTile tile) {
        super(new MachineContainer(player, tile));
        this.tile = tile; xSize = 176; ySize = 166;
    }
    @Override protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        String title = I18n.format("tile.nexustech." + tile.kind.name().toLowerCase(java.util.Locale.ROOT) + ".name");
        // Registry names are not the same as enum names for several machines.
        switch (tile.kind) {
            case SOLAR: title = I18n.format("tile.nexustech.solar_panel.name"); break;
            case GENERATOR: title = I18n.format("tile.nexustech.coal_generator.name"); break;
            case TURBINE: title = I18n.format("tile.nexustech.electric_turbine.name"); break;
            case FURNACE: title = I18n.format("tile.nexustech.electric_furnace.name"); break;
            case CRUSHER: title = I18n.format("tile.nexustech.electric_crusher.name"); break;
            case COMPRESSOR: title = I18n.format("tile.nexustech.electric_compressor.name"); break;
            default: break;
        }
        fontRenderer.drawString(title, 8, 6, 4210752);
        fontRenderer.drawString("FE: " + tile.energy + " / " + tile.capacity, 8, 71, 4210752);
        if ((tile.kind == MachineTile.Kind.GENERATOR || tile.kind == MachineTile.Kind.LAVA_GENERATOR)) fontRenderer.drawString("Burn: " + tile.burn + " / " + tile.burnTotal, 8, 58, 4210752);
        if ((tile.kind == MachineTile.Kind.FURNACE || tile.kind == MachineTile.Kind.CRUSHER || tile.kind == MachineTile.Kind.COMPRESSOR)) fontRenderer.drawString("Progress: " + tile.cook + " / " + MachineTile.FURNACE_TIME, 8, 58, 4210752);
    }
    @Override protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        mc.getTextureManager().bindTexture(TEXTURE);
        drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, ySize);
        // Clear the vanilla furnace's unused slot and flame artwork, then draw only
        // the actual slots for this machine using the same vanilla texture.
        drawRect(guiLeft + 7, guiTop + 16, guiLeft + 169, guiTop + 70, 0xffc6c6c6);
        if (tile.kind == MachineTile.Kind.GENERATOR) {
            drawTexturedModalRect(guiLeft + 79, guiTop + 34, 55, 16, 18, 18);
        } else if (tile.kind == MachineTile.Kind.FURNACE || tile.kind == MachineTile.Kind.CRUSHER || tile.kind == MachineTile.Kind.COMPRESSOR || tile.kind == MachineTile.Kind.LAVA_GENERATOR) {
            drawTexturedModalRect(guiLeft + 55, guiTop + 16, 55, 16, 18, 18);
            drawTexturedModalRect(guiLeft + 115, guiTop + 34, 55, 16, 18, 18);
            drawTexturedModalRect(guiLeft + 79, guiTop + 34, 79, 34, 24, 17);
            int progress = tile.kind == MachineTile.Kind.LAVA_GENERATOR ? 0 : Math.min(24, Math.max(0, tile.cook) * 24 / MachineTile.FURNACE_TIME);
            if (progress > 0) drawTexturedModalRect(guiLeft + 79, guiTop + 34, 176, 14, progress, 16);
        }
    }
    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground(); super.drawScreen(mouseX, mouseY, partialTicks); renderHoveredToolTip(mouseX, mouseY);
    }
}
