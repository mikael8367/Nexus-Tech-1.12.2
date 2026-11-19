package com.mikael8367.nexustech;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;

/** Optional PE 1.4.1 capability access. No ProjectE class is linked at startup. */
final class ProjectEReadOnly {
    static final int WAITING = 0, UNAVAILABLE = 1, ERROR = 2, OK = 3, FULL = 4;
    // Fail closed until the complete Forge capability injection and authenticated-player
    // path has been validated against the real ProjectE implementation.
    private static final boolean INTEGRATION_VERIFIED = false;
    private static final String VERSION = "1.12.2-PE1.4.1";
    static final class Snapshot {
        final int state, count;
        final long emc;
        Snapshot(int state, long emc, int count) { this.state = state; this.emc = emc; this.count = count; }
    }
    private static Snapshot failure(int state) { return new Snapshot(state, 0, 0); }

    static Snapshot read(EntityPlayer viewer) {
        if (!INTEGRATION_VERIFIED) return failure(UNAVAILABLE);
        if (viewer == null || viewer.world == null || viewer.world.isRemote || !(viewer instanceof EntityPlayerMP)
                || FMLCommonHandler.instance().getEffectiveSide() != Side.SERVER) return failure(UNAVAILABLE);
        MinecraftServer server = viewer.getServer();
        if (server == null || server.getPlayerList().getPlayerByUUID(viewer.getUniqueID()) != viewer)
            return failure(UNAVAILABLE);
        if (!Loader.isModLoaded("projecte")) return failure(UNAVAILABLE);
        ModContainer mod = Loader.instance().getIndexedModList().get("projecte");
        if (mod == null || !VERSION.equals(mod.getVersion())) return failure(UNAVAILABLE);
        try {
            ClassLoader loader = ProjectEReadOnly.class.getClassLoader();
            Class<?> api = Class.forName("moze_intel.projecte.api.ProjectEAPI", true, loader);
            Class<?> knowledge = Class.forName("moze_intel.projecte.api.capabilities.IKnowledgeProvider", false, loader);
            Field field = api.getField("KNOWLEDGE_CAPABILITY");
            if (!Modifier.isStatic(field.getModifiers())) return failure(ERROR);
            Object cap = field.get(null);
            if (!(cap instanceof Capability)) return failure(ERROR);
            Object provider = viewer.getCapability((Capability<?>)cap, null);
            if (provider == null) return failure(ERROR);
            return inspectProvider(provider, knowledge);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            return failure(ERROR);
        }
    }

    // Uses methods on the public API interface, not on potentially non-public implementations.
    static Snapshot inspectProvider(Object provider, Class<?> type) {
        try {
            if (provider == null || type == null || !type.isInterface() || !type.isInstance(provider)) return failure(ERROR);
            Method emc = type.getMethod("getEmc"), knowledge = type.getMethod("getKnowledge"), full = type.getMethod("hasFullKnowledge");
            if (emc.getReturnType() != long.class || !List.class.isAssignableFrom(knowledge.getReturnType()) || full.getReturnType() != boolean.class)
                return failure(ERROR);
            Object value = emc.invoke(provider), entries = knowledge.invoke(provider), flag = full.invoke(provider);
            if (!(value instanceof Long) || !(entries instanceof List) || !(flag instanceof Boolean)) return failure(ERROR);
            int count = ((List<?>)entries).size();
            if (count < 0) return failure(ERROR);
            return new Snapshot((Boolean)flag ? FULL : OK, (Long)value, count);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            return failure(ERROR);
        }
    }
    private ProjectEReadOnly() { }
}
