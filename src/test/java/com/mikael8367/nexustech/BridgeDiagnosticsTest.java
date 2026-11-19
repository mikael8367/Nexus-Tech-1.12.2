package com.mikael8367.nexustech;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Bootstrap;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraftforge.energy.CapabilityEnergy;
import sun.misc.Unsafe;

/** Standalone checks; no external mods or live game are required. */
public final class BridgeDiagnosticsTest {
    private static void check(boolean b, String message) { if (!b) throw new AssertionError(message); }
    private static void classification(String id, int expected) {
        check(BridgeDiagnostics.classify(new ResourceLocation(id)) == expected, id);
    }
    static final class TestWorld extends World {
        Set<BlockPos> unloaded;
        IBlockState state;
        int stateReads;
        private TestWorld() { super(null, null, null, null, false); }
        @Override protected IChunkProvider createChunkProvider() { return null; }
        @Override protected boolean isChunkLoaded(int x, int z, boolean allowEmpty) { return true; }
        @Override public boolean isBlockLoaded(BlockPos pos) { return !unloaded.contains(pos); }
        @Override public IBlockState getBlockState(BlockPos pos) {
            if (unloaded.contains(pos)) throw new AssertionError("read from unloaded position");
            stateReads++; return state;
        }
    }
    public static void main(String[] args) throws Exception {
        Bootstrap.register();
        classification("projecte:transmutation_table", BridgeDiagnostics.TABLE);
        classification("projecte:condenser", BridgeDiagnostics.OTHER_PE);
        classification("storagenetwork:master", BridgeDiagnostics.MASTER);
        classification("storagenetwork:request", BridgeDiagnostics.REQUEST);
        classification("storagenetwork:inventory", BridgeDiagnostics.INVENTORY);
        for (String name : new String[] {"simple_kabel", "kabel", "storage_kabel", "ex_kabel", "im_kabel", "process_kabel"})
            classification("storagenetwork:" + name, BridgeDiagnostics.SSN_CABLE);
        classification("storagenetwork:controller", BridgeDiagnostics.OTHER_SSN);
        classification("minecraft:chest", BridgeDiagnostics.NONE);
        classification("other:master", BridgeDiagnostics.NONE);
        check(BridgeDiagnostics.classify(null) == BridgeDiagnostics.NONE, "unregistered block");
        Field f = Unsafe.class.getDeclaredField("theUnsafe"); f.setAccessible(true);
        TestWorld world = (TestWorld)((Unsafe)f.get(null)).allocateInstance(TestWorld.class);
        world.unloaded = new HashSet<>();
        world.state = new Block(Material.IRON).setRegistryName("storagenetwork", "master").getDefaultState();
        BlockPos origin = new BlockPos(0, 64, 0);
        for (EnumFacing direction : EnumFacing.values()) {
            world.unloaded.add(origin.offset(direction));
            int before = world.stateReads;
            check(BridgeDiagnostics.inspect(world, origin, direction) == BridgeDiagnostics.UNLOADED, "unloaded " + direction);
            check(world.stateReads == before, "no unloaded read " + direction);
            world.unloaded.clear();
            check(BridgeDiagnostics.inspect(world, origin, direction) == BridgeDiagnostics.MASTER, "loaded " + direction);
        }
        BridgeBlock block = ModRegistry.BRIDGE;
        check(block.getRegistryName().toString().equals("nexustech:storage_bridge"), "registry name");
        check(block.getCreativeTabToDisplayOn() == NexusTech.CREATIVE_TAB, "creative tab");
        check(!block.isOpaqueCube(block.getDefaultState()) && !block.isFullCube(block.getDefaultState()), "partial model");
        check(block.getDefaultState().getLightOpacity() == 0, "does not block skylight");
        BridgeTile tile = (BridgeTile)block.createTileEntity(world, block.getDefaultState());
        NexusEnergyTestSupport.setupCapability();
        for (EnumFacing direction : EnumFacing.values())
            check(!tile.hasCapability(CapabilityEnergy.ENERGY, direction), "no FE " + direction);
        BridgeContainer container = new BridgeContainer(null, tile);
        check(container.inventorySlots.isEmpty(), "no item slots");
        check(container.getValue(0) == BridgeDiagnostics.WAITING, "waiting initial state");
        container.updateProgressBar(0, BridgeDiagnostics.TABLE);
        check(container.getValue(0) == BridgeDiagnostics.TABLE, "server value accepted");
        container.updateProgressBar(0, 300);
        check(container.getValue(0) == BridgeDiagnostics.TABLE, "invalid status rejected");
        container.updateProgressBar(6, 3);
        container.updateProgressBar(6, 9);
        check(container.getValue(6) == 3, "invalid mod flags rejected");
        Bootstrap.printToSYSOUT("PASS bridge IDs, loaded checks, block flags, FE absence and container values");
    }
}
