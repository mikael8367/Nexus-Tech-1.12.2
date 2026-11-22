package com.mikael8367.nexustech.fluidnetwork.tank;

import java.io.File;
import java.lang.reflect.Field;
import net.minecraft.block.state.IBlockState;
import net.minecraft.block.material.EnumPushReaction;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.PlayerCapabilities;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.GameType;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import sun.misc.Unsafe;

/** Isolated Forge API harness, not a substitute for Minecraft server/client tests. */
public final class FluidTankBlockTest {
    private static final BlockPos POS = new BlockPos(1, 2, 3);
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static final class FakeServer extends MinecraftServer {
        boolean onThread;
        private FakeServer() { super((File)null, null, null, null, null, null, null); }
        @Override public boolean isCallingFromMinecraftThread() { return onThread; }
        @Override public boolean init() { return false; }
        @Override public boolean canStructuresSpawn() { return false; }
        @Override public GameType getGameType() { return GameType.SURVIVAL; }
        @Override public EnumDifficulty getDifficulty() { return EnumDifficulty.NORMAL; }
        @Override public boolean isHardcore() { return false; }
        @Override public int getOpPermissionLevel() { return 0; }
        @Override public boolean shouldBroadcastRconToOps() { return false; }
        @Override public boolean shouldBroadcastConsoleToOps() { return false; }
        @Override public boolean isDedicatedServer() { return false; }
        @Override public boolean shouldUseNativeTransport() { return false; }
        @Override public boolean isCommandBlockEnabled() { return false; }
        @Override public String shareToLAN(GameType type, boolean cheats) { return null; }
    }
    private static final class FakeWorld extends World {
        TileEntity tile; IBlockState state; FakeServer server; boolean loaded; int writes; int reads;
        private FakeWorld() { super(null, null, null, null, false); }
        @Override protected IChunkProvider createChunkProvider() { return null; }
        @Override protected boolean isChunkLoaded(int x, int z, boolean allowEmpty) { return loaded; }
        @Override public boolean isBlockLoaded(BlockPos pos, boolean allowEmpty) { reads++; return loaded; }
        @Override public TileEntity getTileEntity(BlockPos pos) { reads++; return tile; }
        @Override public IBlockState getBlockState(BlockPos pos) { reads++; return state; }
        @Override public MinecraftServer getMinecraftServer() { return server; }
        @Override public boolean setBlockState(BlockPos pos, IBlockState next, int flags) {
            writes++; state = next; tile = null; return true;
        }
    }
    private static FluidTankTile tile(FakeWorld w, NBTTagCompound payload) {
        FluidTankTile t = new FluidTankTile();
        if (payload != null) { payload.setInteger("x", 1); payload.setInteger("y", 2); payload.setInteger("z", 3); t.readFromNBT(payload); }
        t.setWorld(w); t.setPos(POS); w.tile = t; return t;
    }
    private static NBTTagCompound withFluid(FluidStack fluid) {
        NBTTagCompound root = new NBTTagCompound(), data = new NBTTagCompound();
        data.setInteger("Version", 1); data.setTag("Fluid", fluid.writeToNBT(new NBTTagCompound()));
        root.setTag(SafeFluidTank.NBT_KEY, data); return root;
    }
    private static boolean remove(FluidTankBlock b, FakeWorld w, EntityPlayerMP p, boolean harvest) {
        return b.removedByPlayer(b.getDefaultState(), w, POS, p, harvest);
    }
    private static void protectedCase(FluidTankBlock b, FakeWorld w, EntityPlayerMP p, IBlockState supplied, String name) {
        TileEntity original = w.tile; IBlockState state = w.state;
        NBTTagCompound beforeNbt = original instanceof FluidTankTile ? original.writeToNBT(new NBTTagCompound()) : null;
        int writes = w.writes, reads = w.reads;
        for (boolean creative : new boolean[] { false, true }) {
            p.capabilities.isCreativeMode = creative;
            for (boolean harvest : new boolean[] { false, true }) {
                check(!b.removedByPlayer(supplied, w, POS, p, harvest), name + " player denied");
            }
        }
        check(!b.canDropFromExplosion(null), name + " explosion drops denied");
        check(b.getExplosionResistance(w, POS, null, null) == Float.MAX_VALUE, name + " resistance");
        b.onBlockExploded(w, POS, null);
        check(w.writes == writes && w.reads == reads && w.state == state && w.tile == original,
                name + " no world reads or writes; block and tile unchanged");
        if (beforeNbt != null) check(beforeNbt.equals(original.writeToNBT(new NBTTagCompound())), name + " NBT unchanged");
    }
    public static void main(String[] args) throws Exception {
        Bootstrap.register();
        // Test-process-only mapping, not registration in the mod.
        TileEntity.register("test:experimental_tank", FluidTankTile.class);
        Field f = Unsafe.class.getDeclaredField("theUnsafe"); f.setAccessible(true); Unsafe u = (Unsafe) f.get(null);
        FakeServer server = (FakeServer) u.allocateInstance(FakeServer.class); server.onThread = true;
        FakeWorld w = (FakeWorld) u.allocateInstance(FakeWorld.class); w.server = server; w.loaded = true;
        EntityPlayerMP p = (EntityPlayerMP) u.allocateInstance(EntityPlayerMP.class); p.capabilities = new PlayerCapabilities();
        FluidTankBlock b = new FluidTankBlock();
        check(b.hasTileEntity(b.getDefaultState()) && b.createTileEntity(w, b.getDefaultState()) instanceof FluidTankTile, "tile factory");
        check(b.getMobilityFlag(b.getDefaultState()) == EnumPushReaction.BLOCK
                && b.getDefaultState().getMobilityFlag() == EnumPushReaction.BLOCK, "vanilla piston blocked");
        w.state = b.getDefaultState(); tile(w, null);
        protectedCase(b, w, p, b.getDefaultState(), "empty");
        FluidStack tagged = new FluidStack(FluidRegistry.WATER, 123); tagged.tag = new NBTTagCompound(); tagged.tag.setString("variant", "kept");
        NBTTagCompound saved = withFluid(tagged); w.state = b.getDefaultState(); FluidTankTile full = tile(w, saved.copy());
        protectedCase(b, w, p, b.getDefaultState(), "tagged full");
        int before = w.writes;
        check(!remove(b, w, p, false) && !remove(b, w, p, true) && w.tile == full && w.writes == before, "tagged full creative/survival denied");
        NBTTagCompound after = new NBTTagCompound(); full.writeToNBT(after);
        check(after.getCompoundTag(SafeFluidTank.NBT_KEY).equals(saved.getCompoundTag(SafeFluidTank.NBT_KEY)), "fluid NBT unchanged");
        p.capabilities.isCreativeMode = false;
        check(!remove(b, w, p, true) && w.tile == full && w.writes == before, "tagged full survival denied");
        w.tile = null; protectedCase(b, w, p, b.getDefaultState(), "missing tile");
        check(!remove(b, w, p, true), "missing tile denied");
        w.tile = new TileEntity() {}; check(!remove(b, w, p, true), "wrong tile denied");
        FluidTankTile invalid = tile(w, new NBTTagCompound());
        check(invalid.isTankQuarantined() && !remove(b, w, p, true), "missing NBT denied");
        protectedCase(b, w, p, b.getDefaultState(), "quarantined invalid data");
        protectedCase(b, w, p, Blocks.STONE.getDefaultState(), "invalid supplied state");
        protectedCase(b, w, p, null, "null supplied state");
        NBTTagCompound malformed = withFluid(tagged); malformed.getCompoundTag(SafeFluidTank.NBT_KEY).getCompoundTag("Fluid").setInteger("Amount", 0);
        check(tile(w, malformed).isTankQuarantined() && !remove(b, w, p, true), "invalid amount denied");
        NBTTagCompound unknown = withFluid(tagged); unknown.getCompoundTag(SafeFluidTank.NBT_KEY).getCompoundTag("Fluid").setString("FluidName", "missing:unknown");
        check(tile(w, unknown).isTankQuarantined() && !remove(b, w, p, true), "unknown fluid denied");
        FluidTankTile unloadedTile = tile(w, null); unloadedTile.onChunkUnload();
        check(!remove(b, w, p, true), "unloaded tile denied");
        FluidTankTile invalidTile = tile(w, null); invalidTile.invalidate();
        check(!remove(b, w, p, true), "invalidated tile denied");
        tile(w, null); w.loaded = false; check(!remove(b, w, p, true), "unloaded denied"); w.loaded = true;
        server.onThread = false; check(!remove(b, w, p, true), "foreign thread denied"); server.onThread = true;
        long remoteOffset = u.objectFieldOffset(World.class.getDeclaredField("isRemote"));
        u.putBoolean(w, remoteOffset, true); check(!remove(b, w, p, true), "client denied"); u.putBoolean(w, remoteOffset, false);
        w.state = Blocks.STONE.getDefaultState(); check(!remove(b, w, p, true), "stale block denied"); w.state = b.getDefaultState();
        check(!remove(b, w, null, true), "absent player denied");
        tile(w, withFluid(tagged)); TileEntity original = w.tile; before = w.writes;
        check(b.getExplosionResistance(w, POS, null, null) == Float.MAX_VALUE && !b.canDropFromExplosion(null), "explosion resistance/drop policy");
        b.onBlockExploded(w, POS, null);
        check(w.writes == before && w.tile == original && w.state.getBlock() == b, "explosion callback retains tank");
        System.out.println("PASS FluidTankBlockTest");
    }
}
