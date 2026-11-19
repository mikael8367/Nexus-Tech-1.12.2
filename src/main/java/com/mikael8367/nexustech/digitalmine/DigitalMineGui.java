package com.mikael8367.nexustech.digitalmine;

import java.io.IOException;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public final class DigitalMineGui extends GuiContainer {
    private static final ResourceLocation BG = new ResourceLocation("textures/gui/container/generic_54.png");
    private final DigitalMineTile tile;
    private GuiButton power, mode;
    public DigitalMineGui(InventoryPlayer player, DigitalMineTile tile) { super(new DigitalMineContainer(player, tile)); this.tile=tile; xSize=176; ySize=222; }
    @Override public void initGui() {
        super.initGui();
        power = addButton(new GuiButton(0, guiLeft+8, guiTop+94, 76, 20, ""));
        mode = addButton(new GuiButton(1, guiLeft+91, guiTop+94, 76, 20, ""));
    }
    @Override protected void actionPerformed(GuiButton b) throws IOException {
        if ((b.id == 0 || b.id == 1) && mc.playerController != null) mc.playerController.sendEnchantPacket(inventorySlots.windowId, b.id);
    }
    @Override protected void drawGuiContainerForegroundLayer(int x, int y) {
        String title=I18n.format("tile.nexustech.digital_mine_"+(tile.advanced?"advanced":"basic")+".name");
        fontRenderer.drawString(title,8,6,0x404040);
        fontRenderer.drawString("FE: "+tile.energy+" / "+tile.capacity,8,19,0x404040);
        fontRenderer.drawString(I18n.format("digital_mine.status."+tile.status),8,119,0x404040);
    }
    @Override protected void drawGuiContainerBackgroundLayer(float partial, int mouseX, int mouseY) {
        drawRect(guiLeft, guiTop, guiLeft + 176, guiTop + 222, 0xff373737);
        drawRect(guiLeft + 2, guiTop + 2, guiLeft + 174, guiTop + 220, 0xffc6c6c6);
        for (int row=0; row<3; row++) for (int col=0; col<9; col++) slotBackground(8+18*col, 32+18*row);
        for (int row=0; row<3; row++) for (int col=0; col<9; col++) slotBackground(8+18*col, 140+18*row);
        for (int col=0; col<9; col++) slotBackground(8+18*col, 198);
        power.displayString=I18n.format(tile.enabled?"digital_mine.on":"digital_mine.off");
        mode.displayString=I18n.format(tile.mode==0?"digital_mine.eco":"digital_mine.fast");
    }
    private void slotBackground(int x, int y) {
        drawRect(guiLeft+x-1, guiTop+y-1, guiLeft+x+17, guiTop+y+17, 0xff555555);
        drawRect(guiLeft+x, guiTop+y, guiLeft+x+16, guiTop+y+16, 0xff8b8b8b);
    }
    @Override public void drawScreen(int x, int y, float p) { drawDefaultBackground(); super.drawScreen(x,y,p); renderHoveredToolTip(x,y); }
}
