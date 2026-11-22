package com.mikael8367.nexustech.techtools;

import com.mikael8367.nexustech.NexusTech;
import java.io.IOException;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.lwjgl.input.Keyboard;

@Mod.EventBusSubscriber(modid = NexusTech.MODID, value = Side.CLIENT)
public final class TechToolsClient {
    private static final KeyBinding KEY = new KeyBinding("key.nexustech.techtool", Keyboard.KEY_G, "key.categories.nexustech.techtool");
    @SubscribeEvent public static void models(ModelRegistryEvent event) {
        for (Item item : new Item[] {TechToolsRegistry.PICKAXE, TechToolsRegistry.AXE, TechToolsRegistry.SHOVEL, TechToolsRegistry.SWORD, TechToolsRegistry.HOE, TechToolsRegistry.DRILL_3, TechToolsRegistry.DRILL_5, TechToolsRegistry.DRILL_10})
            ModelLoader.setCustomModelResourceLocation(item, 0, new ModelResourceLocation(item.getRegistryName(), "inventory"));
        ClientRegistry.registerKeyBinding(KEY);
    }
    @SubscribeEvent public static void key(InputEvent.KeyInputEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (KEY.isPressed() && mc.player != null && mc.currentScreen == null) {
            ItemStack held = mc.player.getHeldItemMainhand();
            UUID id = TechTool.id(held);
            if (!held.isEmpty() && held.getItem() instanceof TechTool && id != null)
                mc.displayGuiScreen(new Screen(mc.player.inventory.currentItem, id, held));
        }
    }
    private static final class Screen extends GuiScreen {
        final int slot; final UUID id; int speed, area;
        Screen(int slot, UUID id, ItemStack item) { this.slot = slot; this.id = id; speed = TechTool.speed(item); area = TechTool.area(item); }
        @Override public void initGui() {
            buttonList.clear();
            buttonList.add(new GuiButton(0, width / 2 - 100, height / 2 - 30, 200, 20, ""));
            buttonList.add(new GuiButton(1, width / 2 - 100, height / 2 - 5, 200, 20, ""));
            buttonList.add(new GuiButton(2, width / 2 - 100, height / 2 + 28, 200, 20, I18n.format("gui.done")));
            labels();
        }
        void labels() {
            buttonList.get(0).displayString = I18n.format("gui.nexustech.techtool.speed", new int[] {4, 6, 8}[speed]);
            buttonList.get(1).displayString = I18n.format("gui.nexustech.techtool.area", I18n.format("gui.nexustech.techtool.area." + area));
        }
        @Override protected void actionPerformed(GuiButton button) throws IOException {
            if (button.id == 2) { mc.displayGuiScreen(null); return; }
            if (button.id == 0) speed = (speed + 1) % 3;
            else if (button.id == 1) area = (area + 1) % 3;
            else return;
            labels();
            TechToolsNetwork.CHANNEL.sendToServer(new TechToolsNetwork.Config(slot, id, speed, area));
        }
        @Override public void updateScreen() {
            ItemStack held = mc.player.getHeldItemMainhand();
            if (mc.player.inventory.currentItem != slot || held.isEmpty() || !(held.getItem() instanceof TechTool) || !id.equals(TechTool.id(held))) mc.displayGuiScreen(null);
        }
        @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
            drawDefaultBackground();
            drawCenteredString(fontRenderer, I18n.format("gui.nexustech.techtool.title"), width / 2, height / 2 - 54, 0xffffff);
            super.drawScreen(mouseX, mouseY, partialTicks);
        }
        @Override public boolean doesGuiPauseGame() { return false; }
    }
    private TechToolsClient() { }
}
