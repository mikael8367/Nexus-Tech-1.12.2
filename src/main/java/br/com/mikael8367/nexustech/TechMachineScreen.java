package br.com.mikael8367.nexustech;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Compact status-only screen; item insertion/removal remains server-side on block interaction. */
@SideOnly(Side.CLIENT)
public final class TechMachineScreen extends GuiScreen {
    private static final int TEXT = 0xE5E5E5, HINT = 0xAABBC8, LINE = 13;
    private final TileEntity tile;
    private final String titleKey;
    private final int panelWidth = 220;
    private final int panelHeight = 124;

    public TechMachineScreen(TileEntity tile, String titleKey) {
        this.tile = tile;
        this.titleKey = titleKey;
    }

    @Override public void updateScreen() {
        super.updateScreen();
        // Close when the block is broken/replaced or the player walks away.
        BlockPos pos = tile.getPos();
        if (mc.player == null || tile.isInvalid() || mc.world == null || mc.world.getTileEntity(pos) != tile
                || mc.player.getDistanceSq(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) > 64.0D) {
            mc.displayGuiScreen(null);
        }
    }

    private int line(String key, int x, int y, int color, Object... args) {
        drawString(fontRenderer, I18n.format(key, args), x, y, color);
        return y + LINE;
    }

    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int left = (width - panelWidth) / 2;
        int top = (height - panelHeight) / 2;
        int x = left + 10;
        drawRect(left, top, left + panelWidth, top + panelHeight, 0xEE101820);
        drawRect(left, top, left + panelWidth, top + 2, 0xFF38C878);
        drawCenteredString(fontRenderer, TextFormatting.GREEN + I18n.format(titleKey), width / 2, top + 10, 0xFFFFFF);
        int y = top + 30;
        if (tile instanceof AbstractEnergyTile) {
            AbstractEnergyTile energy = (AbstractEnergyTile) tile;
            y = line("gui.nexustech.energy", x, y, TEXT, energy.getStoredEnergy(), energy.getEnergyCapacity());
        }
        if (tile instanceof TileCoalGenerator) {
            TileCoalGenerator generator = (TileCoalGenerator) tile;
            y = line("gui.nexustech.generator.fuel", x, y, TEXT, generator.getFuelTicks() / 20);
            y = line(generator.getFuelTicks() > 0 ? "gui.nexustech.generator.burning" : "gui.nexustech.generator.idle",
                    x, y, TEXT, TileCoalGenerator.FE_PER_TICK);
            line("gui.nexustech.generator.hint", x, y + 2, HINT);
        } else if (tile instanceof TileSolarPanel) {
            TileSolarPanel solar = (TileSolarPanel) tile;
            y = line(solar.isGenerating() ? "gui.nexustech.solar.generating" : "gui.nexustech.solar.idle",
                    x, y, TEXT, TileSolarPanel.FE_PER_TICK);
            line("gui.nexustech.solar.hint", x, y + 2, HINT);
        } else if (tile instanceof TileEnergyCable) {
            y = line("gui.nexustech.cable.rate", x, y, TEXT, 512);
            line("gui.nexustech.cable.hint", x, y + 2, HINT);
        } else if (tile instanceof TileElectricFurnace) {
            TileElectricFurnace furnace = (TileElectricFurnace) tile;
            y = line("gui.nexustech.furnace.items", x, y, TEXT, furnace.getInputCount(), furnace.getOutputCount());
            y = line("gui.nexustech.furnace.progress", x, y, TEXT,
                    furnace.getCookTime() * 100 / TileElectricFurnace.COOK_TICKS);
            y = line("gui.nexustech.furnace.hint", x, y + 2, HINT);
            line("gui.nexustech.take_hint", x, y, HINT);
        } else if (tile instanceof TileElectricAnvil) {
            TileElectricAnvil anvil = (TileElectricAnvil) tile;
            if (anvil.hasRepairItem()) {
                y = line(anvil.isFullyRepaired() ? "gui.nexustech.anvil.done" : "gui.nexustech.anvil.damage",
                        x, y, TEXT, anvil.getRepairDamage());
            } else {
                y = line("gui.nexustech.anvil.empty", x, y, TEXT);
            }
            y = line("gui.nexustech.anvil.rate", x, y, HINT,
                    TileElectricAnvil.REPAIR_PER_TICK, TileElectricAnvil.ENERGY_PER_DURABILITY);
            line("gui.nexustech.take_hint", x, y + 2, HINT);
        }
        drawCenteredString(fontRenderer, I18n.format("gui.nexustech.close"), width / 2, top + panelHeight - 14, 0x8899AA);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override public boolean doesGuiPauseGame() { return false; }
}
