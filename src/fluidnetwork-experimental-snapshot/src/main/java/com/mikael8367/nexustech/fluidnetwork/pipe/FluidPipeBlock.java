package com.mikael8367.nexustech.fluidnetwork.pipe;

import com.mikael8367.nexustech.NexusTech;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.block.material.Material;
import net.minecraft.block.material.EnumPushReaction;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.Explosion;
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

    // Until network invalidation and fluid transfer are transactional, even an
    // apparently empty pipe cannot safely be removed through these hooks. This
    // cannot stop commands, direct world replacement, or mod-specific removal.
    @Override public boolean removedByPlayer(IBlockState state, World world, BlockPos pos,
                                              EntityPlayer player, boolean willHarvest) {
        return false;
    }
    @Override public boolean canDropFromExplosion(Explosion explosion) { return false; }
    @Override public void onBlockExploded(World world, BlockPos pos, Explosion explosion) { /* retain block */ }
    @Override public EnumPushReaction getMobilityFlag(IBlockState state) { return EnumPushReaction.BLOCK; }

    // The model is narrower than a cube; do not cull adjacent faces as solid.
    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }

    private static final double CORE_LO = 4.0 / 16.0, CORE_HI = 12.0 / 16.0;
    private static final double ARM_LO = 6.0 / 16.0, ARM_HI = 10.0 / 16.0;
    private static final AxisAlignedBB CORE = new AxisAlignedBB(CORE_LO, CORE_LO, CORE_LO, CORE_HI, CORE_HI, CORE_HI);
    private static final PropertyBool[] SIDES = {NORTH, SOUTH, EAST, WEST, UP, DOWN};
    private static final AxisAlignedBB[] ARMS = {
            new AxisAlignedBB(ARM_LO, ARM_LO, 0, ARM_HI, ARM_HI, CORE_LO),
            new AxisAlignedBB(ARM_LO, ARM_LO, CORE_HI, ARM_HI, ARM_HI, 1),
            new AxisAlignedBB(CORE_HI, ARM_LO, ARM_LO, 1, ARM_HI, ARM_HI),
            new AxisAlignedBB(0, ARM_LO, ARM_LO, CORE_LO, ARM_HI, ARM_HI),
            new AxisAlignedBB(ARM_LO, CORE_HI, ARM_LO, ARM_HI, 1, ARM_HI),
            new AxisAlignedBB(ARM_LO, 0, ARM_LO, ARM_HI, CORE_LO, ARM_HI)
    };

    // The Forge selection/legacy collision API admits only one AABB. Return the
    // envelope there; actual entity collisions and ray targeting use the union.
    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess access, BlockPos pos) {
        state = getActualState(state, access, pos);
        AxisAlignedBB result = CORE;
        for (int i = 0; i < SIDES.length; i++) {
            if (state.getValue(SIDES[i])) result = result.union(ARMS[i]);
        }
        return result;
    }

    @Override
    public void addCollisionBoxToList(IBlockState state, World world, BlockPos pos,
            AxisAlignedBB query, List<AxisAlignedBB> boxes, Entity entity, boolean isActualState) {
        if (!isActualState) state = getActualState(state, world, pos);
        addCollisionBoxToList(pos, query, boxes, CORE);
        for (int i = 0; i < SIDES.length; i++) {
            if (state.getValue(SIDES[i])) addCollisionBoxToList(pos, query, boxes, ARMS[i]);
        }
    }

    // Intersect and merge ray intervals: the common core/arm faces are internal,
    // not valid hits even when the ray begins inside the pipe.
    private static final class Interval {
        double enter = Double.NEGATIVE_INFINITY, exit = Double.POSITIVE_INFINITY;
        EnumFacing enterFace, exitFace;
    }

    private static Interval interval(AxisAlignedBB box, double[] start, double[] delta) {
        double[] min = {box.minX, box.minY, box.minZ};
        double[] max = {box.maxX, box.maxY, box.maxZ};
        EnumFacing[] low = {EnumFacing.WEST, EnumFacing.DOWN, EnumFacing.NORTH};
        EnumFacing[] high = {EnumFacing.EAST, EnumFacing.UP, EnumFacing.SOUTH};
        Interval result = new Interval();
        for (int axis = 0; axis < 3; axis++) {
            if (delta[axis] == 0) {
                if (start[axis] < min[axis] || start[axis] > max[axis]) return null;
                continue;
            }
            double a = (min[axis] - start[axis]) / delta[axis];
            double b = (max[axis] - start[axis]) / delta[axis];
            EnumFacing in = low[axis], out = high[axis];
            if (a > b) {
                double swap = a; a = b; b = swap;
                EnumFacing face = in; in = out; out = face;
            }
            if (a > result.enter) { result.enter = a; result.enterFace = in; }
            if (b < result.exit) { result.exit = b; result.exitFace = out; }
            if (result.enter > result.exit) return null;
        }
        return result;
    }

    @Override
    public RayTraceResult collisionRayTrace(IBlockState state, World world, BlockPos pos,
            Vec3d start, Vec3d end) {
        state = getActualState(state, world, pos);
        double[] origin = {start.x - pos.getX(), start.y - pos.getY(), start.z - pos.getZ()};
        double[] delta = {end.x - start.x, end.y - start.y, end.z - start.z};
        if ((delta[0] == 0 && delta[1] == 0 && delta[2] == 0)
                || !Double.isFinite(delta[0]) || !Double.isFinite(delta[1]) || !Double.isFinite(delta[2])
                || !Double.isFinite(origin[0]) || !Double.isFinite(origin[1]) || !Double.isFinite(origin[2])) return null;
        List<Interval> intervals = new ArrayList<>(7);
        Interval core = interval(CORE, origin, delta);
        if (core != null) intervals.add(core);
        for (int i = 0; i < SIDES.length; i++) if (state.getValue(SIDES[i])) {
            Interval arm = interval(ARMS[i], origin, delta);
            if (arm != null) intervals.add(arm);
        }
        Collections.sort(intervals, Comparator.comparingDouble(i -> i.enter));
        for (int i = 0; i < intervals.size(); i++) {
            Interval merged = intervals.get(i);
            while (i + 1 < intervals.size() && intervals.get(i + 1).enter <= merged.exit) {
                Interval next = intervals.get(++i);
                if (next.exit > merged.exit) { merged.exit = next.exit; merged.exitFace = next.exitFace; }
            }
            if (merged.exit < 0) continue;
            double t = merged.enter >= 0 ? merged.enter : merged.exit;
            EnumFacing face = merged.enter >= 0 ? merged.enterFace : merged.exitFace;
            if (t > 1 || face == null) return null;
            return new RayTraceResult(new Vec3d(start.x + delta[0] * t,
                    start.y + delta[1] * t, start.z + delta[2] * t), face, pos);
        }
        return null;
    }

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
