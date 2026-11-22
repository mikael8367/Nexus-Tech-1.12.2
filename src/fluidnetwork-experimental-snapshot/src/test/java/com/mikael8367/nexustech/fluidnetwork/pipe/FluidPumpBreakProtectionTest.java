package com.mikael8367.nexustech.fluidnetwork.pipe;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.concurrent.Callable;
import net.minecraft.block.state.IBlockState;
import net.minecraft.block.material.EnumPushReaction;
import net.minecraft.util.EnumFacing;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;
import sun.misc.Unsafe;

/** Real Forge hooks in an isolated fake world; not an actual Minecraft server/client. */
public final class FluidPumpBreakProtectionTest {
    static final BlockPos POS = new BlockPos(0, 64, 0);
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    static final class W extends World {
        IBlockState state;
        TileEntity tile;
        boolean loaded, failLookup;
        int tileReads, removals, updates;
        Runnable callback, dirtyCallback;
        W() { super(null, null, null, null, false); }
        @Override protected IChunkProvider createChunkProvider() { return null; }
        @Override protected boolean isChunkLoaded(int x, int z, boolean empty) { return loaded; }
        @Override public boolean isBlockLoaded(BlockPos p) { return loaded; }
        @Override public IBlockState getBlockState(BlockPos p) {
            if (!loaded) throw new AssertionError("unloaded block read");
            return state;
        }
        @Override public TileEntity getTileEntity(BlockPos p) {
            if (!loaded) throw new AssertionError("unloaded tile read");
            tileReads++;
            if (failLookup) throw new IllegalStateException("lookup");
            return tile;
        }
        @Override public boolean setBlockState(BlockPos p, IBlockState s, int flags) {
            if (callback != null) callback.run();
            removals++; state = s; tile = null; return true;
        }
        @Override public boolean setBlockToAir(BlockPos p) { return setBlockState(p, Blocks.AIR.getDefaultState(), 3); }
        @Override public void notifyBlockUpdate(BlockPos p, IBlockState old, IBlockState now, int flags) { updates++; }
        @Override public void markChunkDirty(BlockPos p, TileEntity t) { if (dirtyCallback != null) dirtyCallback.run(); }
        @Override public void updateComparatorOutputLevel(BlockPos p, net.minecraft.block.Block b) { }
    }
    static W world() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true);
        W w = (W) ((Unsafe) field.get(null)).allocateInstance(W.class);
        w.loaded = true; return w;
    }
    static FluidPumpTile place(W w, FluidPumpBlock block) {
        w.state = block.getDefaultState(); FluidPumpTile tile = new FluidPumpTile();
        tile.setWorld(w); tile.setPos(POS); w.tile = tile;
        block.onBlockPlacedBy(w, POS, w.state, null, net.minecraft.item.ItemStack.EMPTY);
        return tile;
    }
    static void rejected(FluidPumpBlock block, W w, String name) {
        int before = w.removals, reads = w.tileReads;
        IBlockState state = w.state; TileEntity original = w.tile;
        check(!block.removedByPlayer(block.getDefaultState(), w, POS, null, true), name + " survival");
        check(!block.removedByPlayer(block.getDefaultState(), w, POS, null, false), name + " creative hook");
        block.onBlockExploded(w, POS, null);
        check(w.removals == before && w.state == state && w.tile == original, name + " retain block and tile");
        check(w.tileReads == reads, name + " no tile lookup");
    }
    @SuppressWarnings("unchecked") public static void main(String[] args) throws Exception {
        Bootstrap.register();
        Constructor<?> c = Capability.class.getDeclaredConstructors()[0]; c.setAccessible(true);
        CapabilityEnergy.ENERGY = (Capability<IEnergyStorage>) c.newInstance("test:energy", null, (Callable<IEnergyStorage>) () -> null);
        TileEntity.register("test:pump_break", FluidPumpTile.class);
        FluidPumpBlock block = new FluidPumpBlock(); W w = world();
        check(!block.canDropFromExplosion(null), "explosion must not pre-drop");
        FluidPumpTile tile = place(w, block);
        IEnergyStorage energy = tile.getCapability(CapabilityEnergy.ENERGY, null);
        check(energy.receiveEnergy(200, false) == 200, "charge");
        int updates = w.updates; rejected(block, w, "charged");
        check(energy.getEnergyStored() == 200 && w.updates == updates + 2, "no loss; player resync");
        NBTTagCompound tag = tile.writeToNBT(new NBTTagCompound());
        tag.setInteger("Energy", FluidPumpTile.CAPACITY); tile.readFromNBT(tag);
        rejected(block, w, "full capacity");
        check(energy.getEnergyStored() == FluidPumpTile.CAPACITY, "full energy retained");
        tag.setInteger("Energy", 0); tile.readFromNBT(tag);
        check(!tile.isEnergyQuarantined() && energy.getEnergyStored() == 0, "valid persisted zero");
        Object[] bad = {"wrong", Integer.valueOf(-1), Integer.valueOf(10001), null};
        for (Object value : bad) {
            tag.removeTag("Energy");
            if (value instanceof String) tag.setString("Energy", (String)value);
            if (value instanceof Integer) tag.setInteger("Energy", (Integer)value);
            tile.readFromNBT(tag); rejected(block, w, "invalid " + value);
            NBTTagCompound saved = tile.writeToNBT(new NBTTagCompound());
            check(value == null ? !saved.hasKey("Energy") : value.equals(value instanceof String ? saved.getString("Energy") : saved.getInteger("Energy")), "raw NBT retained");
        }
        w.tile = null; rejected(block, w, "missing tile");
        FluidPumpTile recreated = new FluidPumpTile(); recreated.setWorld(w); recreated.setPos(POS); w.tile = recreated;
        rejected(block, w, "Forge auto-created missing tile");
        check(!recreated.writeToNBT(new NBTTagCompound()).hasKey("Energy"), "unknown energy not replaced by zero");
        FluidPumpTile reloadedMissing = new FluidPumpTile(); reloadedMissing.readFromNBT(recreated.writeToNBT(new NBTTagCompound()));
        reloadedMissing.setWorld(w); reloadedMissing.setPos(POS); w.tile = reloadedMissing;
        rejected(block, w, "auto-created tile after reload");
        tile = place(w, block); tile.invalidate(); rejected(block, w, "invalidated tile");
        tile = place(w, block); w.failLookup = true; rejected(block, w, "lookup fails"); w.failLookup = false;
        w.loaded = false; int reads = w.tileReads; rejected(block, w, "unloaded");
        check(w.tileReads == reads, "unloaded tile lookup"); w.loaded = true;
        final FluidPumpTile receivingTile = place(w, block);
        final IEnergyStorage receivingEnergy = receivingTile.getCapability(CapabilityEnergy.ENERGY, null);
        w.dirtyCallback = () -> rejected(block, w, "receiving reentrancy");
        check(receivingEnergy.receiveEnergy(1, false) == 1, "receipt completes"); w.dirtyCallback = null;
        check(receivingEnergy.getEnergyStored() == 1, "receipt retained");
        FluidPumpTile empty = place(w, block);
        IEnergyStorage emptyEnergy = empty.getCapability(CapabilityEnergy.ENERGY, null);
        rejected(block, w, "empty");
        check(emptyEnergy.getEnergyStored() == 0, "empty retains energy");
        W client = world(); client.state = block.getDefaultState(); client.tile = empty;
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe"); unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);
        unsafe.putBoolean(client, unsafe.objectFieldOffset(World.class.getField("isRemote")), true);
        rejected(block, client, "speculative client");
        check(client.updates == 0, "no client resync attempt");
        check(!block.removedByPlayer(null, null, null, null, true), "null world survival");
        check(!block.removedByPlayer(null, null, null, null, false), "null world creative");
        block.onBlockExploded(null, null, null);
        // Real Forge state delegates mobility to the block. Test all four facings
        // and both directions of every facing transition, including identical states.
        FluidPumpTile oriented = place(w, block);
        IEnergyStorage orientedEnergy = oriented.getCapability(CapabilityEnergy.ENERGY, null);
        check(orientedEnergy.receiveEnergy(200, false) == 200, "orientation charge");
        for (EnumFacing from : EnumFacing.HORIZONTALS) {
            IBlockState oldState = block.getDefaultState().withProperty(FluidPumpBlock.FACING, from);
            check(oldState.getMobilityFlag() == EnumPushReaction.BLOCK, "piston blocked " + from);
            for (EnumFacing to : EnumFacing.HORIZONTALS) {
                IBlockState newState = block.getDefaultState().withProperty(FluidPumpBlock.FACING, to);
                check(!oriented.shouldRefresh(w, POS, oldState, newState), "same block refresh " + from + " -> " + to);
                check(orientedEnergy.getEnergyStored() == 200 && w.tile == oriented, "orientation retains FE and tile");
            }
            check(oriented.shouldRefresh(w, POS, oldState, Blocks.AIR.getDefaultState()), "replacement must refresh");
            check(oriented.shouldRefresh(w, POS, oldState, new FluidPumpBlock().getDefaultState()), "different block must refresh");
        }
        System.out.println("PASS pump player/explosion guard, NBT, unloaded, reentrancy, orientation and pistons");
    }
}
