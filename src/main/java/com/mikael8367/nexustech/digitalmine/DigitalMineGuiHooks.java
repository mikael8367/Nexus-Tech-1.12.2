package com.mikael8367.nexustech.digitalmine;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Delegate GUI ID 71 here from the existing server IGuiHandler. */
public final class DigitalMineGuiHooks {
    private DigitalMineGuiHooks() {}
    public static Object server(int id, EntityPlayer player, World world, int x, int y, int z) {
        BlockPos pos = new BlockPos(x,y,z);
        if (id != DigitalMineRegistry.GUI_ID || !world.isBlockLoaded(pos)) return null;
        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof DigitalMineTile)) return null;
        DigitalMineContainer c = new DigitalMineContainer(player.inventory, (DigitalMineTile)te);
        return c.canInteractWith(player) ? c : null;
    }
}
