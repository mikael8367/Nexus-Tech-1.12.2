package br.com.mikael8367.projectestoragebridge;

import java.lang.reflect.Field;

import org.lwjgl.input.Mouse;

import mrriegel.storagenetwork.gui.GuiContainerStorageInventory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Captures deposit clicks before GuiContainer treats the cursor as a click
 * outside a vanilla slot (which can drop the carried stack).
 */
public final class BridgeGuiInputHandler {
    private static final int GUI_WIDTH = 176;
    private static final int GUI_HEIGHT = 256;
    private static final Field SIMPLE_FIELD = findSimpleField();

    private static Field findSimpleField() {
        try {
            Field field = GuiContainerStorageInventory.class.getDeclaredField("isSimple");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    @SubscribeEvent
    public void onMouseInput(GuiScreenEvent.MouseInputEvent.Pre event) {
        if (!(event.getGui() instanceof GuiContainerStorageInventory)) {
            return;
        }

        int button = Mouse.getEventButton();
        if (!Mouse.getEventButtonState() || (button != 0 && button != 1)) {
            return;
        }

        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.player == null) {
            return;
        }
        ItemStack carried = minecraft.player.inventory.getItemStack();
        if (carried == null || carried.isEmpty()) {
            return;
        }

        ScaledResolution resolution = new ScaledResolution(minecraft);
        int mouseX = Mouse.getEventX() * resolution.getScaledWidth() / minecraft.displayWidth;
        int mouseY = resolution.getScaledHeight()
                - Mouse.getEventY() * resolution.getScaledHeight() / minecraft.displayHeight - 1;

        int guiLeft = (resolution.getScaledWidth() - GUI_WIDTH) / 2;
        int guiTop = (resolution.getScaledHeight() - GUI_HEIGHT) / 2;
        boolean simple = isSimple((GuiContainerStorageInventory) event.getGui());
        int fieldBottom = guiTop + (simple ? 150 : 90);

        // Match SSN's inField() bounds. The displayed stack grid is a virtual
        // deposit surface, not a vanilla Slot; consume the click before vanilla
        // can interpret it as dropping the cursor stack outside the inventory.
        if (mouseX <= guiLeft + 7 || mouseX >= guiLeft + GUI_WIDTH - 7
                || mouseY <= guiTop + 7 || mouseY >= fieldBottom) {
            return;
        }

        if (playerHasStorageContainer(minecraft)) {
            BridgeNetwork.CHANNEL.sendToServer(new BridgeInsertMessage(button));
            event.setCanceled(true);
        }
    }

    private static boolean isSimple(GuiContainerStorageInventory gui) {
        if (SIMPLE_FIELD == null) {
            return false;
        }
        try {
            return SIMPLE_FIELD.getBoolean(gui);
        } catch (IllegalAccessException ignored) {
            return false;
        }
    }

    private static boolean playerHasStorageContainer(Minecraft minecraft) {
        return minecraft.player != null
                && minecraft.player.openContainer instanceof mrriegel.storagenetwork.gui.IStorageContainer;
    }
}
