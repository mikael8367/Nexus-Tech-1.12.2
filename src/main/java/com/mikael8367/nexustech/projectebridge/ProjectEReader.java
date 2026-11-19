package com.mikael8367.nexustech.projectebridge;

import com.mikael8367.nexustech.BridgeTile;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.relauncher.Side;

/**
 * Isolated, optional PE 1.12.2-PE1.4.1 diagnostic, not wired to a GUI or network.
 * Call only for the authenticated connection's player, on the server thread.
 * A neighboring table is a physical prerequisite, not an inventory or authorization.
 * No ItemStack or ProjectE provider escapes this class. Results are point-in-time
 * observations, not reservations and not proof that a transmutation would succeed.
 */
public final class ProjectEReader {
    private static final String VERSION = "1.12.2-PE1.4.1";
    private static final ResourceLocation BRIDGE = new ResourceLocation("nexustech", "storage_bridge");
    private static final ResourceLocation TABLE = new ResourceLocation("projecte", "transmutation_table");

    public enum Status { OK, UNAVAILABLE, INVALID_ENDPOINT, INVALID_ITEM, API_ERROR }

    public static final class Result {
        public final Status status;
        /** Provider balance only; excludes EMC stored in input items. */
        public final long balance;
        /** Size of the current PE view, not the total number of possible items. */
        public final int knowledgeCount;
        public final boolean fullKnowledge, itemKnown, itemHasValue;
        public final long itemValue, itemSellValue;
        private Result(Status status, long balance, int knowledgeCount, boolean fullKnowledge,
                       boolean itemKnown, boolean itemHasValue, long itemValue, long itemSellValue) {
            this.status = status; this.balance = balance; this.knowledgeCount = knowledgeCount;
            this.fullKnowledge = fullKnowledge; this.itemKnown = itemKnown;
            this.itemHasValue = itemHasValue; this.itemValue = itemValue; this.itemSellValue = itemSellValue;
        }
    }
    private static Result failure(Status status) {
        return new Result(status, 0, 0, false, false, false, 0, 0);
    }

    /** Read one item. Never trust a player identity or arbitrary item claimed by a client. */
    public static Result read(EntityPlayerMP player, BlockPos bridgePos, EnumFacing tableSide, ItemStack item) {
        try {
            if (player == null || player instanceof FakePlayer || player.world == null || player.world.isRemote
                    || FMLCommonHandler.instance().getEffectiveSide() != Side.SERVER) return failure(Status.UNAVAILABLE);
            MinecraftServer server = player.getServer();
            if (server == null || !server.isCallingFromMinecraftThread()
                    || server.getPlayerList().getPlayerByUUID(player.getUniqueID()) != player || player.isDead)
                return failure(Status.UNAVAILABLE);
            if (!Loader.isModLoaded("projecte")) return failure(Status.UNAVAILABLE);
            ModContainer mod = Loader.instance().getIndexedModList().get("projecte");
            if (mod == null || !VERSION.equals(mod.getVersion())) return failure(Status.UNAVAILABLE);
            if (bridgePos == null || !endpointValid(player.world, bridgePos, tableSide,
                    player.getDistanceSq(bridgePos.getX() + 0.5, bridgePos.getY() + 0.5, bridgePos.getZ() + 0.5)))
                return failure(Status.INVALID_ENDPOINT);
            if (item == null || item.isEmpty()) return failure(Status.INVALID_ITEM);

            ClassLoader loader = ProjectEReader.class.getClassLoader();
            Class<?> api = Class.forName("moze_intel.projecte.api.ProjectEAPI", true, loader);
            Class<?> knowledgeType = Class.forName("moze_intel.projecte.api.capabilities.IKnowledgeProvider", false, loader);
            Class<?> emcType = Class.forName("moze_intel.projecte.api.proxy.IEMCProxy", false, loader);
            Field field = api.getField("KNOWLEDGE_CAPABILITY");
            if (!Modifier.isStatic(field.getModifiers()) || field.getType() != Capability.class)
                return failure(Status.API_ERROR);
            Object cap = field.get(null);
            if (!(cap instanceof Capability)) return failure(Status.API_ERROR);
            Object provider = player.getCapability((Capability<?>) cap, null);
            Method getProxy = api.getMethod("getEMCProxy");
            if (!Modifier.isStatic(getProxy.getModifiers()) || getProxy.getReturnType() != emcType)
                return failure(Status.API_ERROR);
            Object proxy = getProxy.invoke(null);
            return inspect(provider, knowledgeType, proxy, emcType, item.copy());
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            return failure(Status.API_ERROR);
        }
    }

    static boolean endpointValid(World world, BlockPos pos, EnumFacing side, double distanceSquared) {
        if (world == null || pos == null || side == null || !(distanceSquared >= 0 && distanceSquared <= 64.0)) return false;
        BlockPos table = pos.offset(side);
        if (!world.isBlockLoaded(pos) || !world.isBlockLoaded(table)) return false;
        if (!BRIDGE.equals(world.getBlockState(pos).getBlock().getRegistryName())) return false;
        TileEntity tile = world.getTileEntity(pos);
        if (!(tile instanceof BridgeTile) || tile.isInvalid() || tile.getWorld() != world || !pos.equals(tile.getPos())) return false;
        return TABLE.equals(world.getBlockState(table).getBlock().getRegistryName());
    }

    // Package-private seam for contract tests. Public interfaces only; never their implementation class.
    static Result inspect(Object provider, Class<?> knowledgeType, Object proxy, Class<?> emcType, ItemStack item) {
        try {
            if (item == null || item.isEmpty()) return failure(Status.INVALID_ITEM);
            if (knowledgeType == null || !knowledgeType.isInterface() || !knowledgeType.isInstance(provider)
                    || emcType == null || !emcType.isInterface() || !emcType.isInstance(proxy))
                return failure(Status.API_ERROR);
            Method balance = method(knowledgeType, "getEmc", long.class);
            Method list = method(knowledgeType, "getKnowledge", List.class);
            Method full = method(knowledgeType, "hasFullKnowledge", boolean.class);
            Method known = method(knowledgeType, "hasKnowledge", boolean.class, ItemStack.class);
            Method hasValue = method(emcType, "hasValue", boolean.class, ItemStack.class);
            Method value = method(emcType, "getValue", long.class, ItemStack.class);
            Method sell = method(emcType, "getSellValue", long.class, ItemStack.class);
            long b = (Long) balance.invoke(provider);
            Object entries = list.invoke(provider);
            if (!(entries instanceof List) || b < 0) return failure(Status.API_ERROR);
            int count = ((List<?>) entries).size();
            boolean f = (Boolean) full.invoke(provider);
            boolean k = (Boolean) known.invoke(provider, item.copy());
            boolean has = (Boolean) hasValue.invoke(proxy, item.copy());
            long v = has ? (Long) value.invoke(proxy, item.copy()) : 0;
            long s = has ? (Long) sell.invoke(proxy, item.copy()) : 0;
            if (count < 0 || v < 0 || s < 0) return failure(Status.API_ERROR);
            return new Result(Status.OK, b, count, f, k, has, v, s);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            return failure(Status.API_ERROR);
        }
    }
    private static Method method(Class<?> type, String name, Class<?> result, Class<?>... args)
            throws NoSuchMethodException {
        Method m = type.getMethod(name, args);
        if (m.getReturnType() != result || Modifier.isStatic(m.getModifiers()))
            throw new NoSuchMethodException(name);
        return m;
    }
    private ProjectEReader() { }
}
