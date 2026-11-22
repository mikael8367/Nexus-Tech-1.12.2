package com.mikael8367.nexustech.nuclear;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
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
 public NuclearGui(InventoryPlayer p,NuclearController tile){super(new NuclearContainer(p,tile));this.tile=tile;xSize=NuclearGuiLayout.WIDTH;ySize=NuclearGuiLayout.HEIGHT;}
 @Override public void initGui(){
  super.initGui();buttonList.clear();
  buttonList.add(new GuiButton(1,guiLeft+8,guiTop+NuclearGuiLayout.BUTTON_Y,NuclearGuiLayout.BUTTON_WIDTH,NuclearGuiLayout.BUTTON_HEIGHT,I18n.format("nuclear.button.on")));
  buttonList.add(new GuiButton(0,guiLeft+90,guiTop+NuclearGuiLayout.BUTTON_Y,NuclearGuiLayout.BUTTON_WIDTH,NuclearGuiLayout.BUTTON_HEIGHT,I18n.format("nuclear.button.off")));
  buttonList.add(new GuiButton(2,guiLeft+90,guiTop+58,78,18,I18n.format("nuclear.guard.off")));
 }
 @Override protected void actionPerformed(GuiButton button)throws IOException{if(button.id==0||button.id==1||button.id==2)mc.playerController.sendEnchantPacket(inventorySlots.windowId,button.id);}
 // Fit a long translation or unusual numeric value without cutting it off or drawing over the frame.
 private void text(String value,int y){
  int width=fontRenderer.getStringWidth(value);
  float scale=width>160?160.0F/width:1.0F;
  GlStateManager.pushMatrix();
  GlStateManager.translate(8.0F,(float)y,0.0F);
  GlStateManager.scale(scale,scale,1.0F);
  fontRenderer.drawString(value,0,0,4210752);
  GlStateManager.popMatrix();
 }
 private void meter(int y,int value,int capacity,int color){
  int x=guiLeft+NuclearGuiLayout.METER_X,top=guiTop+y;
  drawRect(x,top,x+NuclearGuiLayout.METER_WIDTH,top+NuclearGuiLayout.METER_HEIGHT,0xffe4e4e4);
  int width=NuclearGuiLayout.meterWidth(value,capacity);
  if(width>0)drawRect(x,top,x+width,top+NuclearGuiLayout.METER_HEIGHT,color);
 }
 private String percent(String label,int field,int capacity){
  int value=tile.displayField(field), percentage=NuclearGuiLayout.percent(value,capacity);
  return label+": "+(NuclearGuiLayout.bounded(value,capacity)>0 && percentage==0?"<1":Integer.toString(percentage))+"%";
 }
 @Override protected void drawGuiContainerForegroundLayer(int mx,int my){
  text(I18n.format("tile.nexustech.nuclear_controller.name"),5);
  text("FE: "+tile.displayField(0)+" / "+NuclearController.CAPACITY,16);
  text(I18n.format("nuclear.production")+": "+tile.displayField(1)+" FE/t",26);
  text(I18n.format("nuclear.heat")+": "+tile.displayField(2)+" / "+NuclearController.SCRAM,36);
  String status=tile.scrammed?"nuclear.status.scram":!tile.formed?"nuclear.status.unformed":tile.coolantGuard() && tile.displayField(4)==0?"nuclear.status.no_water":!tile.hasFuel()?"nuclear.status.no_fuel":tile.enabled && tile.displayField(0)>NuclearController.CAPACITY-NuclearController.FE_PER_TICK?"nuclear.status.full":tile.enabled?"nuclear.status.on":"nuclear.status.off";
  text(I18n.format(status),46);
  text(I18n.format("nuclear.fuel")+": "+tile.displayField(3)+" / "+NuclearController.FUEL_TICKS,80);
  text(I18n.format("nuclear.water")+": "+tile.displayField(4)+" / "+NuclearController.WATER_CAPACITY+" mB",90);
 }
 @Override protected void drawGuiContainerBackgroundLayer(float part,int mx,int my){
  // A taller top panel leaves each row, slot and button its own space.
  drawRect(guiLeft,guiTop,guiLeft+176,guiTop+NuclearGuiLayout.INVENTORY_TOP,0xff373737);
  drawRect(guiLeft+1,guiTop+1,guiLeft+175,guiTop+NuclearGuiLayout.INVENTORY_TOP,0xffeeeeee);
  drawRect(guiLeft+2,guiTop+2,guiLeft+174,guiTop+NuclearGuiLayout.INVENTORY_TOP,0xffc6c6c6);
  meter(NuclearGuiLayout.ENERGY_Y,tile.displayField(0),NuclearController.CAPACITY,0xffffe2a8);
  meter(NuclearGuiLayout.HEAT_Y,tile.displayField(2),NuclearController.SCRAM,
        NuclearGuiLayout.heatColor(tile.displayField(2),NuclearController.RESET,NuclearController.SCRAM));
  meter(NuclearGuiLayout.WATER_Y,tile.displayField(4),NuclearController.WATER_CAPACITY,0xffb8dcff);
  GlStateManager.color(1.0F,1.0F,1.0F,1.0F);
  mc.getTextureManager().bindTexture(TEX);
  // Vanilla inventory texture y=126 has slots at +14, +32, +50 and +72.
  drawTexturedModalRect(guiLeft,guiTop+NuclearGuiLayout.INVENTORY_TOP,0,126,176,96);
  for(int x=7;x<=51;x+=22){
   int y=NuclearGuiLayout.MACHINE_SLOT_Y-1;
   drawRect(guiLeft+x,guiTop+y,guiLeft+x+18,guiTop+y+18,0xff8b8b8b);
   drawRect(guiLeft+x+1,guiTop+y+1,guiLeft+x+17,guiTop+y+17,0xffdddddd);
  }
  GlStateManager.color(1.0F,1.0F,1.0F,1.0F);
 }
 @Override public void drawScreen(int mx,int my,float part){
  for(GuiButton b:buttonList)if(b.id==2)b.displayString=I18n.format(tile.coolantGuard()?"nuclear.guard.on":"nuclear.guard.off");
  drawDefaultBackground();super.drawScreen(mx,my,part);renderHoveredToolTip(mx,my);
  if(isPointInRegion(8,NuclearGuiLayout.MACHINE_SLOT_Y,16,16,mx,my) && tile.inventory.getStackInSlot(0).isEmpty())
   drawHoveringText(java.util.Arrays.asList(I18n.format("nuclear.fuel.hint"),I18n.format("nuclear.fuel.duration")),mx,my);
  else if(isPointInRegion(90,58,78,18,mx,my))
   drawHoveringText(java.util.Arrays.asList(I18n.format("nuclear.guard.hint1"),I18n.format("nuclear.guard.hint2")),mx,my);
  else if(isPointInRegion(8,36,160,9,mx,my))
   drawHoveringText(java.util.Arrays.asList(percent(I18n.format("nuclear.heat"),2,NuclearController.SCRAM),I18n.format("nuclear.heat.hint1"),I18n.format("nuclear.heat.hint2")),mx,my);
  else if(isPointInRegion(8,90,160,9,mx,my))
   drawHoveringText(java.util.Arrays.asList(percent(I18n.format("nuclear.water"),4,NuclearController.WATER_CAPACITY),I18n.format("nuclear.water.hint1"),I18n.format("nuclear.water.hint2")),mx,my);
  else if(isPointInRegion(8,16,160,9,mx,my))
   drawHoveringText(java.util.Arrays.asList(percent("FE",0,NuclearController.CAPACITY),I18n.format("nuclear.energy.hint")),mx,my);
 }
}
