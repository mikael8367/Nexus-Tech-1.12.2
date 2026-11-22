package com.mikael8367.nexustech.fluidnetwork.pipe;

import com.mikael8367.nexustech.NexusTech;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.ChunkCache;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;

/** Passive, bufferless pipe. It deliberately exposes no fluid capability. */
public final class FluidPipeBlock extends Block {
    public static final PropertyBool NORTH = PropertyBool.create("north");
    public static final PropertyBool SOUTH = PropertyBool.create("south");
    public static final PropertyBool EAST = PropertyBool.create("east");
    public static final PropertyBool WEST = PropertyBool.create("west");
    public static final PropertyBool UP = PropertyBool.create("up");
    public static final PropertyBool DOWN = PropertyBool.create("down");

    public FluidPipeBlock() {
        super(Material.IRON);
        setRegistryName(NexusTech.MODID, "fluid_pipe");
        setUnlocalizedName(NexusTech.MODID + ".fluid_pipe");
        setCreativeTab(NexusTech.CREATIVE_TAB);
        setHardness(2.0F);
        setResistance(10.0F);
        setHarvestLevel("pickaxe", 1);
        setDefaultState(blockState.getBaseState()
                .withProperty(NORTH, false).withProperty(SOUTH, false)
                .withProperty(EAST, false).withProperty(WEST, false)
                .withProperty(UP, false).withProperty(DOWN, false));
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, NORTH, SOUTH, EAST, WEST, UP, DOWN);
    }

    // Connections are derived, not persisted in the four metadata bits.
    @Override public IBlockState getStateFromMeta(int meta) { return getDefaultState(); }
    @Override public int getMetaFromState(IBlockState state) { return 0; }

    // The model is narrower than a cube; do not cull adjacent faces as solid.
    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }

    @Override
    public IBlockState getActualState(IBlockState state, IBlockAccess access, BlockPos pos) {
        // Recompute every side, including when the caller passes an old actual state.
        state = state.withProperty(NORTH, false).withProperty(SOUTH, false)
                .withProperty(EAST, false).withProperty(WEST, false)
                .withProperty(UP, false).withProperty(DOWN, false);
        if (access == null || pos == null || pos.getY() < 0 || pos.getY() >= 256) return state;
        try {
            if (access instanceof World) {
                if (!((World) access).isBlockLoaded(pos)) return state;
            } else if (access.getClass() == ChunkCache.class) {
                // Exact Forge 1.12.2 cache: cached chunks only; absent or out of
                // bounds returns AIR. Also reject an absent/out-of-bounds origin.
                IBlockState origin = access.getBlockState(pos);
                if (origin == null || origin.getBlock() != this) return state;
            } else {
                // Unknown wrappers/subclasses may force-load during a read.
                return state;
            }
        } catch (RuntimeException unavailable) {
            return state;
        }
        return state.withProperty(NORTH, connects(access, pos, EnumFacing.NORTH))
                .withProperty(SOUTH, connects(access, pos, EnumFacing.SOUTH))
                .withProperty(EAST, connects(access, pos, EnumFacing.EAST))
                .withProperty(WEST, connects(access, pos, EnumFacing.WEST))
                .withProperty(UP, connects(access, pos, EnumFacing.UP))
                .withProperty(DOWN, connects(access, pos, EnumFacing.DOWN));
    }

    private static boolean connects(IBlockAccess access, BlockPos pos, EnumFacing side) {
        try {
            BlockPos neighbor = pos.offset(side);
            IBlockState neighborState;
            if (access instanceof World) {
                World world = (World) access;
                if (neighbor.getY() < 0 || neighbor.getY() >= 256 || !world.isBlockLoaded(neighbor)) return false;
                // World.getBlockState can call provideChunk. Read only an existing
                // chunk instead, so a change after isBlockLoaded cannot load one.
                IChunkProvider provider = world.getChunkProvider();
                if (provider == null) return false;
                Chunk chunk = provider.getLoadedChunk(neighbor.getX() >> 4, neighbor.getZ() >> 4);
                if (chunk == null) return false;
                neighborState = chunk.getBlockState(neighbor);
            } else {
                neighborState = access.getBlockState(neighbor);
            }
            if (neighborState == null) return false;
            Block block = neighborState.getBlock();
            return block instanceof FluidPipeBlock || block instanceof FluidPumpBlock;
        } catch (RuntimeException unavailable) {
            return false;
        }
    }
}
