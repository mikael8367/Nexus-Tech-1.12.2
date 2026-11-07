package br.com.mikael8367.nexustech;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

/** Stores the registry id and metadata of the last scanned block in the tool's NBT. */
final class ItemBlockScanner extends Item {
    ItemBlockScanner() { setMaxStackSize(1); }

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand,
            EnumFacing side, float hitX, float hitY, float hitZ) {
        if (world.isRemote) return EnumActionResult.SUCCESS;
        ItemStack stack = player.getHeldItem(hand);
        NBTTagCompound tag = stack.hasTagCompound() ? stack.getTagCompound() : new NBTTagCompound();
        tag.setString("ScannedBlock", world.getBlockState(pos).getBlock().getRegistryName().toString());
        tag.setInteger("ScannedMeta", world.getBlockState(pos).getBlock().getMetaFromState(world.getBlockState(pos)));
        stack.setTagCompound(tag);
        player.sendMessage(new TextComponentString("Bloco analisado: " + tag.getString("ScannedBlock")
                + " (meta " + tag.getInteger("ScannedMeta") + ")"));
        return EnumActionResult.SUCCESS;
    }
}
