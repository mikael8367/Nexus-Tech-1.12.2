package br.com.mikael8367.nexustech;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.text.TextFormatting;

/** Compact status-only screen; item insertion/removal remains server-side on block interaction. */
public final class TechMachineScreen extends GuiScreen {
    private final TileEntity tile;
    private final String title;
    private final int panelWidth = 190;
    private final int panelHeight = 118;

    public TechMachineScreen(TileEntity tile, String title) {
        this.tile = tile;
        this.title = title;
    }

    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int left = (width - panelWidth) / 2;
        int top = (height - panelHeight) / 2;
        drawRect(left, top, left + panelWidth, top + panelHeight, 0xEE101820);
        drawRect(left, top, left + panelWidth, top + 2, 0xFF38C878);
        drawCenteredString(fontRenderer, TextFormatting.GREEN + title, width / 2, top + 10, 0xFFFFFF);
        int y = top + 34;
        if (tile instanceof AbstractEnergyTile) {
            AbstractEnergyTile energy = (AbstractEnergyTile) tile;
            drawString(fontRenderer, "Energia: " + energy.getStoredEnergy() + " / " + energy.getEnergyCapacity() + " FE", left + 12, y, 0xE5E5E5);
            y += 15;
        }
        if (tile instanceof TileCoalGenerator) {
            drawString(fontRenderer, "Combustível restante: " + ((TileCoalGenerator) tile).getFuelTicks() + " ticks", left + 12, y, 0xE5E5E5);
            y += 15;
            drawString(fontRenderer, "Insira carvão clicando com ele na mão.", left + 12, y, 0xAABBC8);
        } else if (tile instanceof TileSolarPanel) {
            drawString(fontRenderer, "Geração: até 4 FE/t durante o dia", left + 12, y, 0xE5E5E5);
            y += 15;
            drawString(fontRenderer, "Precisa de céu aberto e sem chuva.", left + 12, y, 0xAABBC8);
        } else if (tile instanceof TileEnergyCable) {
            drawString(fontRenderer, "Transferência: até 512 FE/t", left + 12, y, 0xE5E5E5);
            y += 15;
            drawString(fontRenderer, "Conecte a máquinas compatíveis.", left + 12, y, 0xAABBC8);
        } else if (tile instanceof TileElectricFurnace) {
            TileElectricFurnace furnace = (TileElectricFurnace) tile;
            drawString(fontRenderer, "Entrada: " + furnace.getInputCount() + " | Saída: " + furnace.getOutputCount(), left + 12, y, 0xE5E5E5);
            y += 15;
            drawString(fontRenderer, "Progresso: " + furnace.getCookTime() + " / 200", left + 12, y, 0xE5E5E5);
            y += 15;
            drawString(fontRenderer, "Insira item smeltável clicando com ele.", left + 12, y, 0xAABBC8);
            y += 12;
            drawString(fontRenderer, "Agache e clique para retirar a saída.", left + 12, y, 0xAABBC8);
        } else if (tile instanceof TileElectricAnvil) {
            TileElectricAnvil anvil = (TileElectricAnvil) tile;
            drawString(fontRenderer, anvil.hasRepairItem() ? "Dano restante: " + anvil.getRepairDamage() : "Nenhum item para reparar", left + 12, y, 0xE5E5E5);
            y += 15;
            drawString(fontRenderer, "Reparo: 4 pontos/tick, custo 25 FE/ponto", left + 12, y, 0xAABBC8);
            y += 15;
            drawString(fontRenderer, "Agache e clique para retirar o item.", left + 12, y, 0xAABBC8);
        }
        drawCenteredString(fontRenderer, "ESC para fechar", width / 2, top + panelHeight - 14, 0x8899AA);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override public boolean doesGuiPauseGame() { return false; }
}
