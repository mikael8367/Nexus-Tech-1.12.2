package com.mikael8367.nexustech.fluidnetwork.pipe;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldProviderSurface;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;
import sun.misc.Unsafe;

/** Standalone regression; deliberately uses an in-memory world, not a game server. */
public final class FluidNetworkSafetyTest {
    static void check(boolean value, String what) { if (!value) throw new AssertionError(what); }
    static final BlockPos ORIGIN = new BlockPos(0, 64, 0);
    static final class W extends World {
        Map<BlockPos, IBlockState> blocks;
        Map<BlockPos, TileEntity> tiles;
        BlockPos blocked;
        int reads, tileReads, dirtyCalls, failAtDirty;
        boolean failDirty, failBlockRead;
        Runnable dirtyCallback;
        IEnergyStorage reentrant;
        IEnergyStorage reenterOnLookup;
        int lookupReceipt;
        int reentrantReceipt;
        W() { super(null, null, null, null, false); }
        @Override protected IChunkProvider createChunkProvider() { return null; }
        @Override protected boolean isChunkLoaded(int x, int z, boolean empty) { return true; }
        @Override public boolean isBlockLoaded(BlockPos p) { return !p.equals(blocked); }
        @Override public IBlockState getBlockState(BlockPos p) {
            if (!isBlockLoaded(p)) throw new AssertionError("unloaded block read");
            reads++;
            if (failBlockRead) throw new IllegalStateException("block read failed");
            IBlockState state = blocks.get(p);
            return state == null ? Blocks.AIR.getDefaultState() : state;
        }
        @Override public TileEntity getTileEntity(BlockPos p) {
            if (!isBlockLoaded(p)) throw new AssertionError("unloaded tile read");
            tileReads++;
            if (reenterOnLookup != null) lookupReceipt = reenterOnLookup.receiveEnergy(10, false);
            return tiles.get(p);
        }
        @Override public void updateComparatorOutputLevel(BlockPos p, net.minecraft.block.Block block) { }
        @Override public void markChunkDirty(BlockPos p, TileEntity tile) {
            dirtyCalls++;
            if (dirtyCallback != null) dirtyCallback.run();
            if (reentrant != null) reentrantReceipt = reentrant.receiveEnergy(10, false);
            if (failDirty || dirtyCalls == failAtDirty) throw new IllegalStateException("dirty failure");
        }
    }
    static W world(boolean remote) throws Exception {
        Field f = Unsafe.class.getDeclaredField("theUnsafe"); f.setAccessible(true);
        Unsafe u = (Unsafe) f.get(null);
        W w = (W) u.allocateInstance(W.class);
        w.blocks = new HashMap<>(); w.tiles = new HashMap<>();
        u.putBoolean(w, u.objectFieldOffset(World.class.getField("isRemote")), remote);
        Field rand = World.class.getDeclaredField("rand"); rand.setAccessible(true); rand.set(w, new Random(1));
        Field provider = World.class.getDeclaredField("provider"); provider.setAccessible(true); provider.set(w, new WorldProviderSurface());
        return w;
    }
    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        Bootstrap.register();
        Constructor<?> c = Capability.class.getDeclaredConstructors()[0]; c.setAccessible(true);
        CapabilityEnergy.ENERGY = (Capability<IEnergyStorage>) c.newInstance("test:energy", null, (Callable<IEnergyStorage>) () -> null);
        TileEntity.register("nexustech:fluid_pump_test", FluidPumpTile.class);
        FluidPipeBlock pipe = new FluidPipeBlock(); FluidPumpBlock block = new FluidPumpBlock();
        for (EnumFacing f : EnumFacing.Plane.HORIZONTAL) {
            IBlockState s = block.getDefaultState().withProperty(FluidPumpBlock.FACING, f);
            check(block.getStateFromMeta(block.getMetaFromState(s)).getValue(FluidPumpBlock.FACING) == f, "facing round trip");
        }
        W w = world(false);
        w.failBlockRead = true;
        check(PipeTopology.inspect(w, ORIGIN, pipe).status == PipeTopology.Status.UNAVAILABLE, "failed lookup rejects");
        w.failBlockRead = false;
        check(PipeTopology.inspect(w, ORIGIN, pipe).status == PipeTopology.Status.NO_PIPE, "no pipe");
        w.reads = 0;
        for (int x = 0; x < 256; x++) w.blocks.put(ORIGIN.add(x, 0, 0), pipe.getDefaultState());
        PipeTopology.Result r = PipeTopology.inspect(w, ORIGIN, pipe);
        check(r.status == PipeTopology.Status.COMPLETE && r.pipeCount == 256, "exact limit");
        check(r.distanceTo(ORIGIN) == 0 && r.distanceTo(ORIGIN.add(255, 0, 0)) == 255 && r.distanceTo(null) == -1, "bounded shortest distances");
        check(w.tileReads == 0 && w.reads <= 1 + 6 * 256, "bounded block reads; no endpoint callbacks");
        w.blocks.put(ORIGIN.add(256, 0, 0), pipe.getDefaultState());
        r = PipeTopology.inspect(w, ORIGIN, pipe);
        check(r.status == PipeTopology.Status.TOO_LARGE && r.pipeCount == 0, "too large rejects partial result");
        check(r.distanceTo(ORIGIN) == -1, "no partial snapshot");
        w.blocked = ORIGIN.offset(EnumFacing.UP);
        r = PipeTopology.inspect(w, ORIGIN, pipe);
        check(r.status == PipeTopology.Status.UNLOADED && r.pipeCount == 0, "unloaded neighbour rejects");
        check(PipeTopology.inspect(world(true), ORIGIN, pipe).status == PipeTopology.Status.UNAVAILABLE, "no client scan");
        W cycle = world(false);
        cycle.blocks.put(ORIGIN, pipe.getDefaultState());
        cycle.blocks.put(ORIGIN.add(1, 0, 0), pipe.getDefaultState());
        cycle.blocks.put(ORIGIN.add(0, 0, 1), pipe.getDefaultState());
        cycle.blocks.put(ORIGIN.add(1, 0, 1), pipe.getDefaultState());
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos(ORIGIN);
        PipeTopology.Result snapshot = PipeTopology.inspect(cycle, mutable, pipe);
        mutable.setPos(100, 64, 100);
        check(snapshot.pipeCount == 4 && snapshot.distanceTo(ORIGIN.add(1, 0, 1)) == 2 && snapshot.distanceTo(ORIGIN) == 0, "cycle, shortest path and immutable start");
        cycle.blocks.clear();
        check(snapshot.pipeCount == 4 && snapshot.distanceTo(ORIGIN) == 0, "snapshot historical, not authorization");
        w.blocked = null;
        FluidPumpTile pump = new FluidPumpTile(); pump.setWorld(w); pump.setPos(ORIGIN); w.tiles.put(ORIGIN, pump);
        // Only the placement hook makes a freshly constructed tile trusted; a
        // recreated tile with unknown persisted data must remain quarantined.
        w.blocks.put(ORIGIN, block.getDefaultState());
        block.onBlockPlacedBy(w, ORIGIN, block.getDefaultState(), null, net.minecraft.item.ItemStack.EMPTY);
        IEnergyStorage energy = pump.getCapability(CapabilityEnergy.ENERGY, EnumFacing.UP);
        check(energy.receiveEnergy(-1, false) == 0 && energy.receiveEnergy(300, true) == 200 && energy.getEnergyStored() == 0, "simulate and negative");
        w.reentrant = energy; w.reenterOnLookup = energy;
        check(energy.receiveEnergy(Integer.MAX_VALUE, false) == 200 && w.reentrantReceipt == 0 && w.lookupReceipt == 0, "bounded receive and reentry");
        w.reenterOnLookup = null; w.reentrant = null; w.failDirty = true;
        try { energy.receiveEnergy(10, false); throw new AssertionError("dirty failure not surfaced"); }
        catch (IllegalStateException expected) { check(energy.getEnergyStored() == 200, "no mutation on dirty failure"); }
        w.failDirty = false;
        w.dirtyCallback = () -> pump.writeToNBT(new NBTTagCompound());
        try { energy.receiveEnergy(10, false); throw new AssertionError("reentrant save not rejected"); }
        catch (IllegalStateException expected) { check(energy.getEnergyStored() == 200, "reentrant save fails before commit"); }
        w.dirtyCallback = () -> pump.readFromNBT(new NBTTagCompound());
        try { energy.receiveEnergy(10, false); throw new AssertionError("reentrant load not rejected"); }
        catch (IllegalStateException expected) { check(energy.getEnergyStored() == 200, "reentrant load fails before commit"); }
        w.dirtyCallback = () -> w.tiles.remove(ORIGIN);
        check(energy.receiveEnergy(10, false) == 0 && energy.getEnergyStored() == 200, "tile replacement during callback aborts receipt");
        w.tiles.put(ORIGIN, pump);
        w.dirtyCallback = null;
        w.failAtDirty = w.dirtyCalls + 2;
        check(energy.receiveEnergy(10, false) == 10 && energy.getEnergyStored() == 210, "post-commit dirty failure reports receipt");
        w.failAtDirty = 0;
        NBTTagCompound tag = pump.writeToNBT(new NBTTagCompound());
        FluidPumpTile reload = new FluidPumpTile(); reload.readFromNBT(tag); reload.setWorld(w); reload.setPos(ORIGIN); w.tiles.put(ORIGIN, reload);
        IEnergyStorage e2 = reload.getCapability(CapabilityEnergy.ENERGY, null);
        check(e2.getEnergyStored() == 210 && energy.receiveEnergy(1, false) == 0, "persistence and stale capability");
        tag.setInteger("Energy", Integer.MAX_VALUE); reload.readFromNBT(tag);
        check(reload.isEnergyQuarantined() && e2.receiveEnergy(1, false) == 0 && reload.writeToNBT(new NBTTagCompound()).getInteger("Energy") == Integer.MAX_VALUE, "overflow quarantined and preserved");
        tag.setInteger("Energy", -1); reload.readFromNBT(tag);
        check(reload.isEnergyQuarantined() && reload.writeToNBT(new NBTTagCompound()).getInteger("Energy") == -1, "negative preserved");
        tag.setString("Energy", "future"); reload.readFromNBT(tag);
        check(reload.isEnergyQuarantined() && !e2.canReceive() && "future".equals(reload.writeToNBT(new NBTTagCompound()).getString("Energy")), "wrong type preserved");
        tag.removeTag("Energy"); reload.readFromNBT(tag);
        check(reload.isEnergyQuarantined() && !reload.writeToNBT(new NBTTagCompound()).hasKey("Energy"), "missing energy preserved");
        tag.setInteger("Energy", 0); reload.readFromNBT(tag);
        check(!reload.isEnergyQuarantined() && e2.canReceive(), "valid data restores access");
        check(e2.extractEnergy(100, false) == 0 && !e2.canExtract(), "no energy extraction");
        w.blocked = ORIGIN;
        int tileReadsBefore = w.tileReads;
        check(e2.receiveEnergy(10, false) == 0 && w.tileReads == tileReadsBefore, "no unloaded tile lookup");
        check(PipeTopology.inspect(w, ORIGIN, pipe).status == PipeTopology.Status.UNLOADED, "unloaded start rejected");
        w.blocked = null;
        reload.invalidate();
        check(e2.receiveEnergy(10, false) == 0, "invalid tile cannot receive");
        W client = world(true); FluidPumpTile ct = new FluidPumpTile(); ct.setWorld(client); ct.setPos(ORIGIN); client.tiles.put(ORIGIN, ct);
        check(ct.getCapability(CapabilityEnergy.ENERGY, null).receiveEnergy(10, false) == 0, "no client charge");
        check(!ITickable.class.isInstance(pump), "execution remains unavailable");
        System.out.println("PASS topology bounds, load safety, facing, FE, persistence and fail-closed execution");
    }
}
