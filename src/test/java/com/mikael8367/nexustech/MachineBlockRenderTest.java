package com.mikael8367.nexustech;

import java.lang.reflect.Proxy;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

/** Standalone Forge 1.12.2 checks; run main after Bootstrap. */
public final class MachineBlockRenderTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        Bootstrap.register();
        int partialCount = 0;
        for (MachineBlock block : ModRegistry.BLOCKS) {
            MachineTile.Kind kind = block.kind;
            boolean cable = kind == MachineTile.Kind.CABLE;
            boolean partial = kind == MachineTile.Kind.SOLAR || kind == MachineTile.Kind.GENERATOR
                    || kind == MachineTile.Kind.TURBINE || kind == MachineTile.Kind.BATTERY;
            boolean nonCube = cable || partial;
            IBlockState state = block.getDefaultState();
            String label = block.getRegistryName().toString();
            if (partial) partialCount++;
            check(state.isOpaqueCube() == !nonCube, label + " opaque");
            check(state.isFullCube() == !nonCube, label + " full cube");
            check(state.isFullBlock() == !nonCube, label + " cached full block");
            check(state.getLightOpacity() == (cable ? 0 : partial ? 15 : 255), label + " opacity");
            check(state.getAmbientOcclusionLightValue() == (nonCube ? 1.0F : 0.2F), label + " AO");
            check(block.getUseNeighborBrightness(state) == partial, label + " neighbor brightness");
            check(state.isBlockNormalCube() == !nonCube, label + " normal cube");
            for (EnumFacing side : EnumFacing.values()) {
                check(block.doesSideBlockRendering(state, null, BlockPos.ORIGIN, side) == !nonCube,
                        label + " side occlusion " + side);
                IBlockAccess world = (IBlockAccess) Proxy.newProxyInstance(
                        IBlockAccess.class.getClassLoader(), new Class<?>[]{IBlockAccess.class}, (proxy, method, values) -> {
                            if (method.getName().equals("getBlockState")) {
                                check(values[0].equals(BlockPos.ORIGIN.offset(side)), "neighbor position");
                                return state;
                            }
                            throw new AssertionError("unexpected world access " + method.getName());
                        });
                IBlockState stone = Blocks.STONE.getDefaultState();
                check(Blocks.STONE.shouldSideBeRendered(stone, world, BlockPos.ORIGIN, side) == nonCube,
                        label + " adjacent stone face " + side);
            }
            check(block.getBoundingBox(state, null, BlockPos.ORIGIN).equals(Block.FULL_BLOCK_AABB), label + " bounds preserved");
            check(block.getCreativeTabToDisplayOn() == NexusTech.CREATIVE_TAB, label + " creative tab");
        }
        check(ModRegistry.BLOCKS.length == 9 && partialCount == 4, "expected block kinds");
        Bootstrap.printToSYSOUT("PASS render flags, six sides, light, AO, bounds and creative tab for nine blocks");
    }
}
