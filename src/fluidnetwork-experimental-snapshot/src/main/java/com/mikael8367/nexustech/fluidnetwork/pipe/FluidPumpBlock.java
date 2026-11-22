package com.mikael8367.nexustech.fluidnetwork.pipe;

import com.mikael8367.nexustech.NexusTech;
import net.minecraft.block.Block;
import net.minecraft.block.BlockHorizontal;
import net.minecraft.block.material.Material;
import net.minecraft.block.material.EnumPushReaction;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.Explosion;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** The facing denotes the reserved output side; the opposite side is reserved for input. */
public final class FluidPumpBlock extends Block {
    public static final PropertyDirection FACING = BlockHorizontal.FACING;
    public FluidPumpBlock() {
        super(Material.IRON);
        setRegistryName(NexusTech.MODID, "fluid_pump");
        setUnlocalizedName(NexusTech.MODID + ".fluid_pump");
        setCreativeTab(NexusTech.CREATIVE_TAB);
        setHardness(3.0F);
        setResistance(10.0F);
        setHarvestLevel("pickaxe", 1);
        setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH));
    }
    // A partial model must not hide the faces of blocks next to it.
    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }
    @Override protected BlockStateContainer createBlockState() { return new BlockStateContainer(this, FACING); }
    @Override public IBlockState getStateFromMeta(int meta) { return getDefaultState().withProperty(FACING, EnumFacing.getHorizontal(meta & 3)); }
    @Override public int getMetaFromState(IBlockState state) { return state.getValue(FACING).getHorizontalIndex(); }
    @Override public IBlockState getStateForPlacement(World w, BlockPos p, EnumFacing side, float x, float y, float z, int meta, EntityLivingBase placer, EnumHand hand) {
        return getDefaultState().withProperty(FACING, placer.getHorizontalFacing().getOpposite());
    }
    @Override public boolean hasTileEntity(IBlockState state) { return true; }
    @Override public TileEntity createTileEntity(World world, IBlockState state) { return new FluidPumpTile(); }
    @Override public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state,
                                          EntityLivingBase placer, ItemStack stack) {
        super.onBlockPlacedBy(world, pos, state, placer, stack);
        if (world.isRemote || !world.isBlockLoaded(pos) || world.getBlockState(pos).getBlock() != this) return;
        TileEntity tile = world.getTileEntity(pos);
        if (tile instanceof FluidPumpTile && tile.getWorld() == world && pos.equals(tile.getPos())) {
            ((FluidPumpTile) tile).initializeNewPlacement();
        }
    }

    /** Guard player, vanilla piston, and explosion hooks, including empty pumps.
     * Commands, setBlockState and mod-specific removal bypass these hooks. Break
     * events/tool callbacks may already have run; this is not crash persistence. */
    @Override public boolean removedByPlayer(IBlockState state, World world, BlockPos pos,
                                              EntityPlayer player, boolean willHarvest) {
        // Best-effort server resync after speculative client actions; never query a tile.
        if (world != null && !world.isRemote && pos != null) {
            try {
                if (world.isBlockLoaded(pos)) {
                    IBlockState actual = world.getBlockState(pos);
                    world.notifyBlockUpdate(pos, actual, actual, 3);
                }
            } catch (RuntimeException unavailable) { /* rejection remains authoritative */ }
        }
        return false;
    }

    @Override public EnumPushReaction getMobilityFlag(IBlockState state) { return EnumPushReaction.BLOCK; }
    // Explosion drops are evaluated before onBlockExploded, including for empty pumps.
    @Override public boolean canDropFromExplosion(Explosion explosion) { return false; }
    @Override public void onBlockExploded(World world, BlockPos pos, Explosion explosion) { /* retain block */ }
}
