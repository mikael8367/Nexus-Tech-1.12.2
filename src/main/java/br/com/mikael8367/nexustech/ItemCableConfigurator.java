package br.com.mikael8367.nexustech;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.tileentity.TileEntity;

/** Toggles the clicked cable face without breaking or replacing the cable. */
final class ItemCableConfigurator extends Item {
    ItemCableConfigurator() { setMaxStackSize(1); setMaxDamage(512); }

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand,
            EnumFacing side, float hitX, float hitY, float hitZ) {
        TileEntity tile = world.getTileEntity(pos);
        if (!(tile instanceof TileEnergyCable)) return EnumActionResult.PASS;
        if (!world.isRemote) {
            TileEnergyCable cable = (TileEnergyCable) tile;
            cable.setFaceEnabled(side, !cable.isFaceEnabled(side));
            if (!player.capabilities.isCreativeMode) player.getHeldItem(hand).damageItem(1, player);
        }
        return EnumActionResult.SUCCESS;
    }
}
