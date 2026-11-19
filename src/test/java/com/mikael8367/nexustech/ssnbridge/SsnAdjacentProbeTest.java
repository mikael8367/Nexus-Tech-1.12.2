package com.mikael8367.nexustech.ssnbridge;

import com.mikael8367.nexustech.BridgeTile;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import net.minecraft.init.Bootstrap;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldProviderSurface;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraftforge.common.capabilities.Capability;
import sun.misc.Unsafe;

/** No SSN classes on the classpath: tests the reflective contract with stand-ins. */
public final class SsnAdjacentProbeTest {
    public interface Connectable { Position getPos(); Position getMasterPos(); }
    public interface MasterApi { }
    public static final class Position {
        public int dimension;
        private final BlockPos pos;
        Position(int d, BlockPos p) { dimension = d; pos = p; }
        public BlockPos getBlockPos() { return pos; }
    }
    public static final class Connection implements Connectable {
        Position self, master; boolean throwsOnRead;
        public Position getPos() { if (throwsOnRead) throw new IllegalStateException("test"); return self; }
        public Position getMasterPos() { return master; }
    }
    public static class Cable extends TileEntity {
        Capability<?> cap; Connection con; EnumFacing seen; boolean exposes = true;
        @Override public boolean hasCapability(Capability<?> c, EnumFacing side) { seen = side; return exposes && c == cap; }
        @Override @SuppressWarnings("unchecked") public <T> T getCapability(Capability<T> c, EnumFacing side) { seen = side; return c == cap ? (T) con : null; }
    }
    public static final class OtherCable extends Cable { }
    public static final class Master extends TileEntity implements MasterApi { }
    public static final class FakeWorld extends World {
        Map<BlockPos, TileEntity> tiles; Set<BlockPos> unloaded; int forbidden;
        private FakeWorld() { super(null, null, null, null, false); }
        @Override protected IChunkProvider createChunkProvider() { return null; }
        @Override protected boolean isChunkLoaded(int x, int z, boolean allowEmpty) { return true; }
        @Override public boolean isBlockLoaded(BlockPos p) { return !unloaded.contains(p); }
        @Override public TileEntity getTileEntity(BlockPos p) {
            if (unloaded.contains(p)) { forbidden++; throw new AssertionError("read unloaded position"); }
            return tiles.get(p);
        }
        void put(BlockPos p, TileEntity t) { tiles.put(p, t); t.setPos(p); t.setWorld(this); }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void expect(SsnAdjacentProbe.Result result, SsnAdjacentProbe.State state) {
        check(result.state == state, "expected " + state + ", got " + result.state);
        if (state != SsnAdjacentProbe.State.ASSIGNED_MASTER) check(result.masterPos == null, "failure exposes master");
    }
    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        Bootstrap.register();
        Field f = Unsafe.class.getDeclaredField("theUnsafe"); f.setAccessible(true); Unsafe unsafe = (Unsafe)f.get(null);
        FakeWorld w = (FakeWorld)unsafe.allocateInstance(FakeWorld.class);
        w.tiles = new HashMap<>(); w.unloaded = new HashSet<>();
        Field provider = World.class.getField("provider");
        unsafe.putObject(w, unsafe.objectFieldOffset(provider), new WorldProviderSurface());
        Constructor<?> ctor = Capability.class.getDeclaredConstructors()[0]; ctor.setAccessible(true);
        Capability<?> cap = (Capability<?>)ctor.newInstance("test:connectable", null, (Callable<Object>)() -> null);
        check(SsnAdjacentProbe.supportedVersion("2.0.1"), "actual @Mod version");
        check(!SsnAdjacentProbe.supportedVersion("1.12.2-2.0.1") && !SsnAdjacentProbe.supportedVersion(null), "other versions rejected");
        // If the official JAR is deliberately placed on the test classpath, verify
        // our reflection descriptors against its real classes without starting Forge.
        try {
            ClassLoader cl = SsnAdjacentProbeTest.class.getClassLoader();
            Class<?> actualCon = Class.forName("mrriegel.storagenetwork.api.capability.IConnectable", false, cl);
            new SsnAdjacentProbe.Api(cap, actualCon,
                Class.forName("mrriegel.storagenetwork.api.data.DimPos", false, cl),
                Class.forName("mrriegel.storagenetwork.block.cable.TileCable", false, cl),
                Class.forName("mrriegel.storagenetwork.block.master.TileMaster", false, cl),
                Class.forName("mrriegel.storagenetwork.api.network.INetworkMaster", false, cl));
            Class<?> holder = Class.forName("mrriegel.storagenetwork.capabilities.StorageNetworkCapabilities", false, cl);
            check(holder.getField("CONNECTABLE_CAPABILITY").getType() == Capability.class, "real capability field");
            System.out.println("PASS official SSN API signature binding (no Minecraft runtime)");
        } catch (ClassNotFoundException absent) {
            System.out.println("SKIP official SSN API signature binding (JAR absent)");
        }
        SsnAdjacentProbe.Api api = new SsnAdjacentProbe.Api(cap, Connectable.class, Position.class, Cable.class, Master.class, MasterApi.class);
        BlockPos origin = new BlockPos(0, 64, 0), adjacent = origin.east(), masterPos = origin.east(3);
        w.put(origin, new BridgeTile());
        Cable cable = new Cable(); cable.cap = cap; cable.con = new Connection();
        cable.con.self = new Position(0, adjacent); cable.con.master = new Position(0, masterPos);
        w.put(adjacent, cable); w.put(masterPos, new Master());
        SsnAdjacentProbe.Result ok = SsnAdjacentProbe.inspectPrepared(w, origin, EnumFacing.EAST, api);
        expect(ok, SsnAdjacentProbe.State.ASSIGNED_MASTER); check(masterPos.equals(ok.masterPos), "master snapshot");
        check(cable.seen == EnumFacing.WEST, "opposite face");
        cable.con.master = null; expect(SsnAdjacentProbe.inspectPrepared(w, origin, EnumFacing.EAST, api), SsnAdjacentProbe.State.NO_MASTER);
        cable.con.master = new Position(1, masterPos); expect(SsnAdjacentProbe.inspectPrepared(w, origin, EnumFacing.EAST, api), SsnAdjacentProbe.State.OTHER_DIMENSION);
        cable.con.master = new Position(0, masterPos);
        cable.con.self = new Position(0, origin); expect(SsnAdjacentProbe.inspectPrepared(w, origin, EnumFacing.EAST, api), SsnAdjacentProbe.State.INCONSISTENT_SELF);
        cable.con.self = new Position(0, adjacent); cable.exposes = false;
        expect(SsnAdjacentProbe.inspectPrepared(w, origin, EnumFacing.EAST, api), SsnAdjacentProbe.State.NO_CAPABILITY); cable.exposes = true;
        w.unloaded.add(masterPos); expect(SsnAdjacentProbe.inspectPrepared(w, origin, EnumFacing.EAST, api), SsnAdjacentProbe.State.UNLOADED); w.unloaded.clear();
        w.unloaded.add(adjacent); expect(SsnAdjacentProbe.inspectPrepared(w, origin, EnumFacing.EAST, api), SsnAdjacentProbe.State.UNLOADED); w.unloaded.clear();
        w.unloaded.add(origin); expect(SsnAdjacentProbe.inspectPrepared(w, origin, EnumFacing.EAST, api), SsnAdjacentProbe.State.UNLOADED); w.unloaded.clear();
        check(w.forbidden == 0, "never read an unloaded tile");
        w.put(masterPos, new TileEntity() { }); expect(SsnAdjacentProbe.inspectPrepared(w, origin, EnumFacing.EAST, api), SsnAdjacentProbe.State.MASTER_UNAVAILABLE); w.put(masterPos, new Master());
        cable.con.throwsOnRead = true; expect(SsnAdjacentProbe.inspectPrepared(w, origin, EnumFacing.EAST, api), SsnAdjacentProbe.State.ERROR); cable.con.throwsOnRead = false;
        w.put(adjacent, new OtherCable()); expect(SsnAdjacentProbe.inspectPrepared(w, origin, EnumFacing.EAST, api), SsnAdjacentProbe.State.NOT_PLAIN_SSN_CABLE); w.put(adjacent, cable);
        w.put(origin, new TileEntity() { }); expect(SsnAdjacentProbe.inspectPrepared(w, origin, EnumFacing.EAST, api), SsnAdjacentProbe.State.NOT_NEXUS_CABLE);
        expect(SsnAdjacentProbe.inspect(null, origin, EnumFacing.EAST), SsnAdjacentProbe.State.INVALID_CONTEXT);
        expect(SsnAdjacentProbe.inspectPrepared(w, origin, null, api), SsnAdjacentProbe.State.INVALID_CONTEXT);
        System.out.println("SsnAdjacentProbeTest passed");
    }
}
