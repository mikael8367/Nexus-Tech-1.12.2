package com.mikael8367.nexustech.nuclear;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import java.io.IOException;
@SideOnly(Side.CLIENT)
public final class NuclearGui extends GuiContainer {
 private static final ResourceLocation TEX=new ResourceLocation("textures/gui/container/generic_54.png");
 private final NuclearController tile;
 public NuclearGui(InventoryPlayer p,NuclearController tile){super(new NuclearContainer(p,tile));this.tile=tile;xSize=176;ySize=184;}
 @Override public void initGui(){super.initGui();buttonList.clear();buttonList.add(new GuiButton(1,guiLeft+105,guiTop+53,61,20,I18n.format("nuclear.button.on")));buttonList.add(new GuiButton(0,guiLeft+105,guiTop+75,61,20,I18n.format("nuclear.button.off")));}
 @Override protected void actionPerformed(GuiButton button)throws IOException{if(button.id==0||button.id==1)mc.playerController.sendEnchantPacket(inventorySlots.windowId,button.id);}
 @Override protected void drawGuiContainerForegroundLayer(int mx,int my){
  fontRenderer.drawString(I18n.format("tile.nexustech.nuclear_controller.name"),8,5,4210752);
  fontRenderer.drawString("FE: "+tile.displayField(0)+" / "+NuclearController.CAPACITY,8,16,4210752);
  fontRenderer.drawString(I18n.format("nuclear.production")+": "+tile.displayField(1)+" FE/t",8,26,4210752);
  fontRenderer.drawString(I18n.format("nuclear.heat")+": "+tile.displayField(2)+" / "+NuclearController.SCRAM,8,36,4210752);
  fontRenderer.drawString(I18n.format("nuclear.fuel")+": "+tile.displayField(3)+" / "+NuclearController.FUEL_TICKS,8,76,4210752);
  fontRenderer.drawString(I18n.format("nuclear.water")+": "+tile.displayField(4)+" mB",8,87,4210752);
  String status=tile.scrammed?"nuclear.status.scram":!tile.formed?"nuclear.status.unformed":tile.enabled?"nuclear.status.on":"nuclear.status.off";
  fontRenderer.drawString(I18n.format(status),8,46,4210752);
 }
 @Override protected void drawGuiContainerBackgroundLayer(float part,int mx,int my){
  mc.getTextureManager().bindTexture(TEX);
  drawTexturedModalRect(guiLeft,guiTop,0,0,176,88);
  drawRect(guiLeft+7,guiTop+14,guiLeft+169,guiTop+88,0xffc6c6c6);
  // Vanilla inventory starts at texture y=140, 14 pixels below the y=126 crop.
  // At destination y=88 its slot interiors land at y=102, 120, 138 and 160.
  drawTexturedModalRect(guiLeft,guiTop+88,0,126,176,96);
  for(int x=7;x<=51;x+=22){drawRect(guiLeft+x,guiTop+53,guiLeft+x+18,guiTop+71,0xff8b8b8b);drawRect(guiLeft+x+1,guiTop+54,guiLeft+x+17,guiTop+70,0xffdddddd);}
 }
 @Override public void drawScreen(int mx,int my,float part){drawDefaultBackground();super.drawScreen(mx,my,part);renderHoveredToolTip(mx,my);}
}
