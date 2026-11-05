package br.com.mikael8367.projectestoragebridge;

import mrriegel.storagenetwork.block.cable.BlockCable;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

public final class ProjectEStorageCableBlock extends BlockCable {

    public ProjectEStorageCableBlock(String registryName) {
        super(registryName);
        setTranslationKey(ProjectEStorageBridge.MOD_ID + "." + registryName);
        setCreativeTab(CreativeTabs.REDSTONE);
        setHardness(1.5F);
        setResistance(6.0F);
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new ProjectEStorageCableTile();
    }

    @Override
    public EnumBlockRenderType getRenderType(IBlockState state) {
        return EnumBlockRenderType.MODEL;
    }

    @Override
    public boolean shouldSideBeRendered(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
        return true;
    }
}
