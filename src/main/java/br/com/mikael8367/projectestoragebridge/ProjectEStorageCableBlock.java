package br.com.mikael8367.projectestoragebridge;

import mrriegel.storagenetwork.block.cable.BlockCable;
import mrriegel.storagenetwork.block.cable.UnlistedPropertyBlockNeighbors;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

public final class ProjectEStorageCableBlock extends BlockCable {

    private static final ResourceLocation TRANSMUTATION_TABLE =
            new ResourceLocation("projecte", "transmutation_table");

    public ProjectEStorageCableBlock(String registryName) {
        super(registryName);
        setTranslationKey(ProjectEStorageBridge.MOD_ID + "." + registryName);
        setCreativeTab(CreativeTabs.REDSTONE);
        setHardness(1.5F);
        setResistance(6.0F);
    }

    /**
     * ProjectE's 1.12.2 Transmutation Table is a plain block, not an
     * IConnectable or TileEntity. Mark it as a SPECIAL cable endpoint so
     * Simple Storage Network's own TESR draws the connector cap toward it.
     */
    @Override
    protected UnlistedPropertyBlockNeighbors.BlockNeighbors getBlockNeighbors(
            IBlockAccess world, BlockPos pos) {
        UnlistedPropertyBlockNeighbors.BlockNeighbors neighbors =
                super.getBlockNeighbors(world, pos);

        for (EnumFacing facing : EnumFacing.values()) {
            IBlockState adjacent = world.getBlockState(pos.offset(facing));
            if (isTransmutationTable(adjacent)) {
                neighbors.setNeighborType(
                        facing, UnlistedPropertyBlockNeighbors.EnumNeighborType.SPECIAL);
            }
        }
        return neighbors;
    }

    public static boolean isTransmutationTable(IBlockState state) {
        return state != null
                && TRANSMUTATION_TABLE.equals(state.getBlock().getRegistryName());
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new ProjectEStorageCableTile();
    }
}
