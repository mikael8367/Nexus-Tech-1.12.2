package com.mikael8367.nexustech;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** A diagnostic block, deliberately unrelated to the FE cable. */
public final class BridgeBlock extends Block {
    public BridgeBlock() {
        super(Material.IRON);
        setRegistryName(NexusTech.MODID, "storage_bridge");
        setUnlocalizedName(NexusTech.MODID + ".storage_bridge");
        setCreativeTab(NexusTech.CREATIVE_TAB);
        setHardness(3.0F); setResistance(10.0F);
        fullBlock = false; setLightOpacity(0);
    }
    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }
    @Override public boolean hasTileEntity(IBlockState state) { return true; }
    @Override public TileEntity createTileEntity(World world, IBlockState state) { return new BridgeTile(); }
    @Override public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand, EnumFacing side, float x, float y, float z) {
        if (!world.isRemote && world.getTileEntity(pos) instanceof BridgeTile)
            player.openGui(NexusTech.INSTANCE, 1, world, pos.getX(), pos.getY(), pos.getZ());
        return true;
    }
}
