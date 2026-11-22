package com.mikael8367.nexustech.fluidnetwork.pipe;

import com.mikael8367.nexustech.NexusTech;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

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

    @Override
    public IBlockState getActualState(IBlockState state, IBlockAccess access, BlockPos pos) {
        // Recompute every side, including when the caller passes an old actual state.
        state = state.withProperty(NORTH, false).withProperty(SOUTH, false)
                .withProperty(EAST, false).withProperty(WEST, false)
                .withProperty(UP, false).withProperty(DOWN, false);
        // IBlockAccess (including the usual render ChunkCache) has no public
        // loaded-chunk query. Do not guess or force-load through an unknown view.
        if (!(access instanceof World) || pos == null) return state;
        World world = (World) access;
        if (!world.isBlockLoaded(pos)) return state;
        return state.withProperty(NORTH, connects(world, pos, EnumFacing.NORTH))
                .withProperty(SOUTH, connects(world, pos, EnumFacing.SOUTH))
                .withProperty(EAST, connects(world, pos, EnumFacing.EAST))
                .withProperty(WEST, connects(world, pos, EnumFacing.WEST))
                .withProperty(UP, connects(world, pos, EnumFacing.UP))
                .withProperty(DOWN, connects(world, pos, EnumFacing.DOWN));
    }

    private static boolean connects(World world, BlockPos pos, EnumFacing side) {
        BlockPos neighbor = pos.offset(side);
        if (!world.isBlockLoaded(neighbor)) return false;
        Block block = world.getBlockState(neighbor).getBlock();
        return block instanceof FluidPipeBlock || block instanceof FluidPumpBlock;
    }
}
