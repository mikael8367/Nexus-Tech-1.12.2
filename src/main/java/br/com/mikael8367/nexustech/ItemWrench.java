package br.com.mikael8367.nexustech;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Safe wrench: rotates supported machines and never deletes their tile data. */
final class ItemWrench extends Item {
    ItemWrench() { setMaxStackSize(1); setMaxDamage(256); }

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand,
            EnumFacing side, float hitX, float hitY, float hitZ) {
        IBlockState state = world.getBlockState(pos);
        if (!state.getPropertyKeys().contains(BlockFacingMachine.FACING)) return EnumActionResult.PASS;
        if (!world.isRemote) {
            world.setBlockState(pos, state.withProperty(BlockFacingMachine.FACING,
                    state.getValue(BlockFacingMachine.FACING).rotateY()), 3);
            if (!player.capabilities.isCreativeMode) player.getHeldItem(hand).damageItem(1, player);
        }
        return EnumActionResult.SUCCESS;
    }
}
