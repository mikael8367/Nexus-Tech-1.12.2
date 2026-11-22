package com.mikael8367.nexustech.fluidnetwork.pipe;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.block.Block;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;
import sun.misc.Unsafe;

/** Isolated regression; does not register or activate experimental blocks. */
public final class FluidPipeVisualStateTest {
    static final BlockPos ORIGIN = new BlockPos(15, 64, 15);
    static final EnumFacing[] SIDES = {EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.EAST,
            EnumFacing.WEST, EnumFacing.UP, EnumFacing.DOWN};
    static final PropertyBool[] PROPS = {FluidPipeBlock.NORTH, FluidPipeBlock.SOUTH,
            FluidPipeBlock.EAST, FluidPipeBlock.WEST, FluidPipeBlock.UP, FluidPipeBlock.DOWN};
    static void check(boolean ok, String description) { if (!ok) throw new AssertionError(description); }
    static final class W extends World {
        Map<BlockPos, IBlockState> blocks;
        Set<BlockPos> unloaded;
        int reads, tiles;
        RuntimeException failure;
        Error fatal;
        W() { super(null, null, null, null, false); }
        @Override protected IChunkProvider createChunkProvider() { return null; }
        @Override protected boolean isChunkLoaded(int x, int z, boolean empty) { return true; }
        @Override public boolean isBlockLoaded(BlockPos pos) { return !unloaded.contains(pos); }
        @Override public IBlockState getBlockState(BlockPos pos) {
            check(isBlockLoaded(pos), "read unloaded position");
            reads++;
            if (failure != null) throw failure;
            if (fatal != null) throw fatal;
            IBlockState state = blocks.get(pos);
            return state == null ? Blocks.AIR.getDefaultState() : state;
        }
        @Override public TileEntity getTileEntity(BlockPos pos) { tiles++; throw new AssertionError("tile access"); }
    }
    static W world() throws Exception {
        Field f = Unsafe.class.getDeclaredField("theUnsafe"); f.setAccessible(true);
        W w = (W) ((Unsafe) f.get(null)).allocateInstance(W.class);
        w.blocks = new HashMap<>(); w.unloaded = new HashSet<>();
        return w;
    }
    static void mask(IBlockState state, int expected) {
        for (int i = 0; i < 6; i++) check(state.getValue(PROPS[i]) == ((expected & (1 << i)) != 0), "side " + SIDES[i] + " mask " + expected);
    }
    public static void main(String[] args) throws Exception {
        Bootstrap.register();
        FluidPipeBlock pipe = new FluidPipeBlock(); FluidPumpBlock pump = new FluidPumpBlock();
        IBlockState base = pipe.getDefaultState();
        check(base.getPropertyKeys().size() == 6, "six properties");
        mask(base, 0);
        check(pipe.getMetaFromState(base) == 0 && pipe.getStateFromMeta(0).equals(base), "metadata is not used for visual state");
        W w = world();
        for (int bits = 0; bits < 64; bits++) {
            w.blocks.clear();
            for (int i = 0; i < 6; i++) if ((bits & (1 << i)) != 0)
                w.blocks.put(ORIGIN.offset(SIDES[i]), (i % 2 == 0 ? pipe : pump).getDefaultState());
            w.reads = 0;
            IBlockState actual = pipe.getActualState(base, w, ORIGIN);
            mask(actual, bits);
            check(pipe.getMetaFromState(actual) == 0, "all visual states serialize to zero");
            check(w.reads == 6 && w.tiles == 0, "six block reads, no tile access");
        }
        IBlockState all = pipe.getActualState(base, w, ORIGIN);
        w.blocks.clear();
        mask(pipe.getActualState(all, w, ORIGIN), 0);
        for (int i = 0; i < 6; i++) {
            BlockPos neighbor = ORIGIN.offset(SIDES[i]);
            w.blocks.put(neighbor, pipe.getDefaultState()); w.unloaded.add(neighbor);
            w.reads = 0; mask(pipe.getActualState(all, w, ORIGIN), 0);
            check(w.reads == 5, "no read of unloaded neighbor");
            w.blocks.clear(); w.unloaded.clear();
        }
        w.unloaded.add(ORIGIN); w.reads = 0;
        mask(pipe.getActualState(all, w, ORIGIN), 0); check(w.reads == 0, "unloaded origin");
        w.unloaded.clear();
        w.blocks.put(ORIGIN.up(), Blocks.STONE.getDefaultState());
        mask(pipe.getActualState(all, w, ORIGIN), 0);
        mask(pipe.getActualState(all, null, ORIGIN), 0);
        mask(pipe.getActualState(all, w, null), 0);
        IBlockAccess unsupported = (IBlockAccess) Proxy.newProxyInstance(IBlockAccess.class.getClassLoader(),
                new Class<?>[]{IBlockAccess.class}, (p, m, a) -> { throw new AssertionError("unsupported view accessed"); });
        mask(pipe.getActualState(all, unsupported, ORIGIN), 0);
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe"); unsafeField.setAccessible(true);
        IBlockAccess renderCache = (IBlockAccess) ((Unsafe) unsafeField.get(null))
                .allocateInstance(net.minecraft.world.ChunkCache.class);
        mask(pipe.getActualState(all, renderCache, ORIGIN), 0);
        for (EnumFacing facing : EnumFacing.Plane.HORIZONTAL) {
            w.blocks.clear();
            w.blocks.put(ORIGIN.up(), pump.getDefaultState().withProperty(FluidPumpBlock.FACING, facing));
            mask(pipe.getActualState(base, w, ORIGIN), 1 << 4);
        }
        w.failure = new IllegalStateException("corruption");
        try { pipe.getActualState(base, w, ORIGIN); throw new AssertionError("runtime failure masked"); }
        catch (IllegalStateException expected) { check(expected == w.failure, "original runtime exception"); }
        w.failure = null; w.fatal = new LinkageError("fatal");
        try { pipe.getActualState(base, w, ORIGIN); throw new AssertionError("fatal error masked"); }
        catch (LinkageError expected) { check(expected == w.fatal, "original fatal error"); }
        check(pipe.isOpaqueCube(base) && pipe.isFullCube(base), "existing full-cube occlusion unchanged");
        System.out.println("PASS 64 visual masks, pipe/pump, stale state, unloaded, unknown view, failures, full cube");
    }
}
