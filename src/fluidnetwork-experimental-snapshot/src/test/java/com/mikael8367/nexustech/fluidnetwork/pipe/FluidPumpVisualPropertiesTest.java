package com.mikael8367.nexustech.fluidnetwork.pipe;

import java.lang.reflect.Proxy;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

/** Isolated Forge regression: this does not register or activate the pump. */
public final class FluidPumpVisualPropertiesTest {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        Bootstrap.register();
        FluidPumpBlock pump = new FluidPumpBlock();
        check(pump.getDefaultState().getValue(FluidPumpBlock.FACING) == EnumFacing.NORTH,
                "default facing");
        check(pump.getBlockState().getValidStates().size() == 4, "four horizontal states");
        BlockPos origin = new BlockPos(0, 64, 0);
        for (EnumFacing facing : EnumFacing.Plane.HORIZONTAL) {
            IBlockState state = pump.getDefaultState().withProperty(FluidPumpBlock.FACING, facing);
            int meta = pump.getMetaFromState(state);
            check(meta == facing.getHorizontalIndex(), "metadata index " + facing);
            check(pump.getStateFromMeta(meta) == state, "metadata round trip " + facing);
            check(pump.getStateFromMeta(meta | 12) == state, "masked metadata " + facing);
            check(!pump.isOpaqueCube(state) && !state.isOpaqueCube(), "not opaque " + facing);
            check(!pump.isFullCube(state) && !state.isFullCube(), "not full cube " + facing);
            check(state.getLightOpacity() == 0 && pump.getLightOpacity(state) == 0,
                    "partial block must transmit light " + facing);
            check(state.getLightValue() == 0, "pump does not emit light " + facing);
            check(pump.hasTileEntity(state), "tile retained " + facing);
            IBlockAccess access = (IBlockAccess) Proxy.newProxyInstance(
                    IBlockAccess.class.getClassLoader(), new Class<?>[]{IBlockAccess.class},
                    (proxy, method, arguments) -> {
                        if (method.getName().equals("getBlockState")) return state;
                        throw new AssertionError("unexpected world access: " + method.getName());
                    });
            check(pump.getLightOpacity(state, access, origin) == 0, "Forge light opacity " + facing);
            for (EnumFacing side : EnumFacing.values()) {
                check(!pump.doesSideBlockRendering(state, access, origin, side),
                        "block culls side " + facing + "/" + side);
                check(!state.doesSideBlockRendering(access, origin, side),
                        "state culls side " + facing + "/" + side);
                check(Blocks.STONE.shouldSideBeRendered(Blocks.STONE.getDefaultState(), access, origin, side),
                        "neighbor stone face hidden " + facing + "/" + side);
            }
        }
        System.out.println("FluidPumpVisualPropertiesTest passed (4 facings, 6 sides each)");
    }
}
