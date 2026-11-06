package br.com.mikael8367.nexustech;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.IGuiHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Read-only machine status windows. They are opened on the client only: there is no server
 * Container, so the player's own inventory window stays in sync while the screen is open.
 * The data shown comes from the tile entity packets sent by AbstractEnergyTile.
 */
public final class TechGuiHandler implements IGuiHandler {
    public static final int GUI_GENERATOR = 0;
    public static final int GUI_SOLAR = 1;
    public static final int GUI_CABLE = 2;
    public static final int GUI_FURNACE = 3;
    public static final int GUI_ANVIL = 4;

    @Override
    public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        return null;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        TileEntity tile = world.getTileEntity(new BlockPos(x, y, z));
        if (!isValid(id, tile)) return null;
        return new TechMachineScreen(tile, titleKey(id));
    }

    static boolean isValid(int id, TileEntity tile) {
        switch (id) {
            case GUI_GENERATOR: return tile instanceof TileCoalGenerator;
            case GUI_SOLAR: return tile instanceof TileSolarPanel;
            case GUI_CABLE: return tile instanceof TileEnergyCable;
            case GUI_FURNACE: return tile instanceof TileElectricFurnace;
            case GUI_ANVIL: return tile instanceof TileElectricAnvil;
            default: return false;
        }
    }

    /** Same keys as the block names, so the title always matches the localized block name. */
    static String titleKey(int id) {
        switch (id) {
            case GUI_GENERATOR: return "tile.nexustech.coal_generator.name";
            case GUI_SOLAR: return "tile.nexustech.solar_panel.name";
            case GUI_CABLE: return "tile.nexustech.energy_cable.name";
            case GUI_FURNACE: return "tile.nexustech.electric_furnace.name";
            case GUI_ANVIL: return "tile.nexustech.electric_anvil.name";
            default: return "itemGroup.nexustech";
        }
    }
}
