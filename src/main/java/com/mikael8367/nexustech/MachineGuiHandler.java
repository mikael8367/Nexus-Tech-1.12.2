package com.mikael8367.nexustech;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.IGuiHandler;

public final class MachineGuiHandler implements IGuiHandler {
    @Override public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        if (id != 0 || !world.isBlockLoaded(pos)) return null;
        TileEntity tile = world.getTileEntity(pos);
        if (!(tile instanceof MachineTile) || ((MachineTile)tile).kind == MachineTile.Kind.CABLE) return null;
        MachineContainer container = new MachineContainer(player.inventory, (MachineTile)tile);
        return container.canInteractWith(player) ? container : null;
    }
    @Override public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        return NexusTech.proxy.clientGui(id, player, world, x, y, z);
    }
}
