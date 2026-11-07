package br.com.mikael8367.nexustech;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraft.tileentity.TileEntity;

/** Reads Forge Energy through the machine's public tile API. */
final class ItemMultimeter extends Item {
    ItemMultimeter() { setMaxStackSize(1); }

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand,
            EnumFacing side, float hitX, float hitY, float hitZ) {
        if (world.isRemote) return EnumActionResult.SUCCESS;
        TileEntity tile = world.getTileEntity(pos);
        if (!(tile instanceof AbstractEnergyTile)) {
            player.sendMessage(new TextComponentString("Este bloco não possui medidor de energia Nexus Tech."));
            return EnumActionResult.SUCCESS;
        }
        AbstractEnergyTile energy = (AbstractEnergyTile) tile;
        int stored = energy.getStoredEnergy();
        int capacity = energy.getEnergyCapacity();
        int percent = capacity <= 0 ? 0 : (int) ((long) stored * 100L / capacity);
        player.sendMessage(new TextComponentString("Energia: " + stored + " / " + capacity + " FE (" + percent + "%)"));
        return EnumActionResult.SUCCESS;
    }
}
