package br.com.mikael8367.projectestoragebridge;

import mrriegel.storagenetwork.block.cable.BlockCable;
import mrriegel.storagenetwork.block.cable.UnlistedPropertyBlockNeighbors;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
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
        setCreativeTab(BridgeCreativeTab.INSTANCE);
        setHardness(1.5F);
        setResistance(6.0F);
    }

    @Override
    protected UnlistedPropertyBlockNeighbors.BlockNeighbors getBlockNeighbors(
            IBlockAccess world, BlockPos pos) {
        UnlistedPropertyBlockNeighbors.BlockNeighbors neighbors =
                super.getBlockNeighbors(world, pos);
        for (EnumFacing facing : EnumFacing.values()) {
            if (isTransmutationTable(world.getBlockState(pos.offset(facing)))) {
                neighbors.setNeighborType(facing,
                        UnlistedPropertyBlockNeighbors.EnumNeighborType.SPECIAL);
            }
        }
        return neighbors;
    }

    public static boolean isTransmutationTable(IBlockState state) {
        return state != null
                && TRANSMUTATION_TABLE.equals(state.getBlock().getRegistryName());
    }

    @Override
    public void onBlockClicked(World world, BlockPos pos, EntityPlayer player) {
        // SSN facade painting is disabled for this fixed-texture cable.
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new ProjectEStorageCableTile();
    }
}
