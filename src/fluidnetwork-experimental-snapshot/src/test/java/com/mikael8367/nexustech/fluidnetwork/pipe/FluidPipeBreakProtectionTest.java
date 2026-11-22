package com.mikael8367.nexustech.fluidnetwork.pipe;

import java.lang.reflect.Field;
import net.minecraft.block.material.EnumPushReaction;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Bootstrap;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Explosion;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;
import sun.misc.Unsafe;

/** Isolated Forge hook checks; this is not an in-game integration test. */
public final class FluidPipeBreakProtectionTest {
    private static final BlockPos POS = new BlockPos(0, 64, 0);
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    private static final class NoWorldAccess extends World {
        NoWorldAccess() { super(null, null, null, null, false); }
        @Override protected IChunkProvider createChunkProvider() { throw new AssertionError("provider access"); }
        @Override protected boolean isChunkLoaded(int x, int z, boolean allowEmpty) { throw new AssertionError("chunk access"); }
        @Override public boolean isBlockLoaded(BlockPos pos) { throw new AssertionError("loaded check"); }
        @Override public IBlockState getBlockState(BlockPos pos) { throw new AssertionError("state read"); }
        @Override public TileEntity getTileEntity(BlockPos pos) { throw new AssertionError("tile read"); }
        @Override public boolean setBlockToAir(BlockPos pos) { throw new AssertionError("block removal"); }
        @Override public boolean setBlockState(BlockPos pos, IBlockState state, int flags) { throw new AssertionError("block replacement"); }
        @Override public void notifyBlockUpdate(BlockPos pos, IBlockState oldState, IBlockState newState, int flags) { throw new AssertionError("world update"); }
    }
    public static void main(String[] args) throws Exception {
        Bootstrap.register();
        Field field = Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true);
        Unsafe unsafe = (Unsafe) field.get(null);
        NoWorldAccess world = (NoWorldAccess) unsafe.allocateInstance(NoWorldAccess.class);
        EntityPlayer player = (EntityPlayer) unsafe.allocateInstance(EntityPlayerMP.class);
        Explosion explosion = (Explosion) unsafe.allocateInstance(Explosion.class);
        FluidPipeBlock pipe = new FluidPipeBlock();
        PropertyBool[] sides = { FluidPipeBlock.NORTH, FluidPipeBlock.SOUTH, FluidPipeBlock.EAST,
                FluidPipeBlock.WEST, FluidPipeBlock.UP, FluidPipeBlock.DOWN };
        check(!pipe.canDropFromExplosion(null) && !pipe.canDropFromExplosion(explosion), "no explosion drops");
        check(pipe.getMobilityFlag(null) == EnumPushReaction.BLOCK, "null-state piston guard");
        check(!pipe.removedByPlayer(null, null, null, null, false), "null args, no harvest");
        check(!pipe.removedByPlayer(null, null, null, null, true), "null args, harvest");
        pipe.onBlockExploded(null, null, null);
        for (int mask = 0; mask < 64; mask++) {
            IBlockState state = pipe.getDefaultState();
            for (int i = 0; i < sides.length; i++) state = state.withProperty(sides[i], (mask & (1 << i)) != 0);
            for (boolean harvest : new boolean[] {false, true}) {
                check(!pipe.removedByPlayer(state, world, POS, player, harvest), "player removal mask " + mask);
                check(!pipe.removedByPlayer(state, world, POS, null, harvest), "null player mask " + mask);
            }
            check(pipe.getMobilityFlag(state) == EnumPushReaction.BLOCK, "block piston mask " + mask);
            check(state.getMobilityFlag() == EnumPushReaction.BLOCK, "state piston mask " + mask);
            pipe.onBlockExploded(world, POS, explosion);
            pipe.onBlockExploded(world, POS, null);
        }
        System.out.println("PASS pipe unconditional player/explosion/piston guards, 64 states, no world calls");
    }
}
