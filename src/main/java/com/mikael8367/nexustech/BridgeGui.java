package com.mikael8367.nexustech;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public final class BridgeGui extends GuiContainer {
    private final BridgeContainer bridge;
    private final EntityPlayer viewer;
    private static final String[] SIDES = {"down", "up", "north", "south", "west", "east"};
    private static final String[] STATUS = {"none", "table", "other_pe", "master", "request", "inventory", "cable", "other_ssn", "unloaded", "waiting"};
    public BridgeGui(EntityPlayer viewer, BridgeTile tile) {
        super(new BridgeContainer(viewer, tile));
        this.bridge = (BridgeContainer)inventorySlots; this.viewer = viewer;
        xSize = 250; ySize = 229;
    }
    private String text(String key) { return I18n.format("gui.nexustech.bridge." + key); }
    @Override protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        drawRect(guiLeft, guiTop, guiLeft + xSize, guiTop + ySize, 0xff202c37);
        drawRect(guiLeft + 3, guiTop + 3, guiLeft + xSize - 3, guiTop + ySize - 3, 0xffd8e0e4);
        drawRect(guiLeft + 7, guiTop + 31, guiLeft + xSize - 7, guiTop + 146, 0xfff1f4f4);
    }
    @Override protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        int color = 0x26323a;
        fontRenderer.drawString(text("title"), 9, 8, color);
        fontRenderer.drawString(text("player") + " " + viewer.getName(), 9, 20, color);
        int mods = bridge.getValue(6);
        String modText = mods == BridgeDiagnostics.WAITING ? text("waiting") :
            "ProjectE: " + text((mods & 1) != 0 ? "loaded" : "absent") + " | SSN: " + text((mods & 2) != 0 ? "loaded" : "absent");
        fontRenderer.drawString(modText, 10, 37, color);
        for (int i = 0; i < SIDES.length; i++) {
            int value = bridge.getValue(i);
            fontRenderer.drawString(text(SIDES[i]) + ": " + text(STATUS[value]), 10, 52 + i * 14, color);
        }
        int state = bridge.getPeState();
        if (state == ProjectEReadOnly.OK || state == ProjectEReadOnly.FULL) {
            fontRenderer.drawString(text("emc") + " " + bridge.getEmc(), 9, 151, color);
            fontRenderer.drawString(text(state == ProjectEReadOnly.FULL ? "tome_count" : "learned") + " " + bridge.getLearnedCount(), 9, 164, color);
            if (state == ProjectEReadOnly.FULL) fontRenderer.drawString(text("full"), 9, 177, color);
        } else {
            String key = state == ProjectEReadOnly.WAITING ? "waiting" : state == ProjectEReadOnly.ERROR ? "pe_error" : "pe_unavailable";
            fontRenderer.drawString(text("pe_data") + " " + text(key), 9, 151, color);
        }
        fontRenderer.drawString(text("notice1"), 9, 191, 0x963f18);
        fontRenderer.drawString(text("notice2"), 9, 204, 0x963f18);
        fontRenderer.drawString(text("notice3"), 9, 217, 0x963f18);
    }
    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground(); super.drawScreen(mouseX, mouseY, partialTicks);
    }
}
