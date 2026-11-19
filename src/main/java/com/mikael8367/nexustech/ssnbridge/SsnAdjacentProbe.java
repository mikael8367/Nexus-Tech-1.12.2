package com.mikael8367.nexustech.ssnbridge;

import com.mikael8367.nexustech.BridgeTile;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;

/**
 * Opt-in, server-thread-only observation of an adjacent SSN 1.12.2-2.0.1
 * plain cable. This does NOT attach the Nexus cable to the SSN topology.
 * An assigned master is a potentially stale SSN pointer, not proof of a path.
 * No SSN classes are linked until the exact installed version is checked.
 */
public final class SsnAdjacentProbe {
    public enum State {
        INVALID_CONTEXT, SSN_ABSENT, UNSUPPORTED_VERSION, API_UNAVAILABLE,
        UNLOADED, NOT_NEXUS_CABLE, NOT_PLAIN_SSN_CABLE, NO_CAPABILITY,
        INCONSISTENT_SELF, NO_MASTER, OTHER_DIMENSION, MASTER_UNAVAILABLE,
        ASSIGNED_MASTER, ERROR
    }
    public static final class Result {
        public final State state;
        /** Only present with ASSIGNED_MASTER; a snapshot, not a live handle. */
        public final BlockPos masterPos;
        private Result(State state, BlockPos masterPos) { this.state = state; this.masterPos = masterPos; }
    }
    private static Result result(State state) { return new Result(state, null); }
    private SsnAdjacentProbe() { }
    static boolean supportedVersion(String version) { return "2.0.1".equals(version); }

    /** Call on the authoritative server thread, only when a snapshot is needed. */
    public static Result inspect(World world, BlockPos nexusPos, EnumFacing face) {
        if (world == null || nexusPos == null || face == null || world.isRemote) return result(State.INVALID_CONTEXT);
        try {
            MinecraftServer server = world.getMinecraftServer();
            if (server == null || !server.isCallingFromMinecraftThread()) return result(State.INVALID_CONTEXT);
            if (!Loader.isModLoaded("storagenetwork")) return result(State.SSN_ABSENT);
            ModContainer mod = Loader.instance().getIndexedModList().get("storagenetwork");
            // The release filename/mcmod.info says 1.12.2-2.0.1, but its
            // @Mod annotation (and hence Forge ModContainer) says 2.0.1.
            if (mod == null || !supportedVersion(mod.getVersion())) return result(State.UNSUPPORTED_VERSION);
            ClassLoader cl = SsnAdjacentProbe.class.getClassLoader();
            Class<?> holder = Class.forName("mrriegel.storagenetwork.capabilities.StorageNetworkCapabilities", false, cl);
            Class<?> connectable = Class.forName("mrriegel.storagenetwork.api.capability.IConnectable", false, cl);
            Class<?> dimPos = Class.forName("mrriegel.storagenetwork.api.data.DimPos", false, cl);
            Class<?> cable = Class.forName("mrriegel.storagenetwork.block.cable.TileCable", false, cl);
            Class<?> master = Class.forName("mrriegel.storagenetwork.block.master.TileMaster", false, cl);
            Class<?> masterApi = Class.forName("mrriegel.storagenetwork.api.network.INetworkMaster", false, cl);
            Field field = holder.getField("CONNECTABLE_CAPABILITY");
            if (!Modifier.isStatic(field.getModifiers()) || field.getType() != Capability.class) return result(State.API_UNAVAILABLE);
            Object cap = field.get(null);
            if (!(cap instanceof Capability)) return result(State.API_UNAVAILABLE);
            return inspectPrepared(world, nexusPos, face,
                new Api((Capability<?>) cap, connectable, dimPos, cable, master, masterApi));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            return result(State.API_UNAVAILABLE);
        }
    }

    /* Separate binding allows contract tests without packaging SSN or counterfeit classes. */
    static final class Api {
        final Capability<?> cap;
        final Class<?> connectable, dimPos, cable, master, masterApi;
        final Method self, masterPos, blockPos;
        final Field dimension;
        Api(Capability<?> cap, Class<?> connectable, Class<?> dimPos, Class<?> cable, Class<?> master, Class<?> masterApi)
                throws ReflectiveOperationException {
            this.cap = cap; this.connectable = connectable; this.dimPos = dimPos;
            this.cable = cable; this.master = master; this.masterApi = masterApi;
            if (cap == null || !connectable.isInterface() || !masterApi.isInterface()) throw new NoSuchMethodException("API types");
            self = connectable.getMethod("getPos"); masterPos = connectable.getMethod("getMasterPos");
            blockPos = dimPos.getMethod("getBlockPos"); dimension = dimPos.getField("dimension");
            if (self.getReturnType() != dimPos || masterPos.getReturnType() != dimPos
                    || blockPos.getReturnType() != BlockPos.class || dimension.getType() != int.class
                    || Modifier.isStatic(dimension.getModifiers())) throw new NoSuchMethodException("API signatures");
        }
    }
    static Result inspectPrepared(World world, BlockPos nexusPos, EnumFacing face, Api api) {
        if (world == null || world.isRemote || nexusPos == null || face == null || api == null || world.provider == null)
            return result(State.INVALID_CONTEXT);
        try {
            if (!world.isBlockLoaded(nexusPos)) return result(State.UNLOADED);
            TileEntity nexus = world.getTileEntity(nexusPos);
            if (!(nexus instanceof BridgeTile) || nexus.isInvalid() || nexus.getWorld() != world || !nexusPos.equals(nexus.getPos())) return result(State.NOT_NEXUS_CABLE);
            BlockPos adjacent = nexusPos.offset(face);
            if (!world.isBlockLoaded(adjacent)) return result(State.UNLOADED);
            TileEntity tile = world.getTileEntity(adjacent);
            // Exact class prevents invoking an arbitrary mod's overridden provider.
            if (tile == null || tile.isInvalid() || tile.getClass() != api.cable) return result(State.NOT_PLAIN_SSN_CABLE);
            EnumFacing tileFace = face.getOpposite();
            if (!tile.hasCapability(api.cap, tileFace)) return result(State.NO_CAPABILITY);
            Object con = tile.getCapability(api.cap, tileFace);
            if (!api.connectable.isInstance(con)) return result(State.NO_CAPABILITY);
            Object self = api.self.invoke(con);
            int dim = world.provider.getDimension();
            if (!api.dimPos.isInstance(self) || api.dimension.getInt(self) != dim
                    || !adjacent.equals(api.blockPos.invoke(self))) return result(State.INCONSISTENT_SELF);
            Object ptr = api.masterPos.invoke(con);
            if (ptr == null) return result(State.NO_MASTER);
            if (!api.dimPos.isInstance(ptr)) return result(State.ERROR);
            if (api.dimension.getInt(ptr) != dim) return result(State.OTHER_DIMENSION);
            Object rawPos = api.blockPos.invoke(ptr);
            if (!(rawPos instanceof BlockPos)) return result(State.ERROR);
            BlockPos pos = ((BlockPos)rawPos).toImmutable();
            if (!world.isBlockLoaded(pos)) return result(State.UNLOADED);
            TileEntity master = world.getTileEntity(pos);
            if (master == null || master.isInvalid() || master.getClass() != api.master || !api.masterApi.isInstance(master))
                return result(State.MASTER_UNAVAILABLE);
            return new Result(State.ASSIGNED_MASTER, pos);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            return result(State.ERROR);
        }
    }
}
