package br.com.mikael8367.projectestoragebridge;

import mrriegel.storagenetwork.block.cable.BlockCable;
import net.minecraft.block.state.IBlockState;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.tileentity.TileEntity;
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
}
