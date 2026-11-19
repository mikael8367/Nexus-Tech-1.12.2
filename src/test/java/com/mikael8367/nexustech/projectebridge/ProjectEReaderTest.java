package com.mikael8367.nexustech.projectebridge;

import com.mikael8367.nexustech.BridgeTile;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;
import sun.misc.Unsafe;
import java.util.Arrays;
import java.util.List;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

/** Standalone contract checks; no Minecraft server and no actual ProjectE implementation. */
public final class ProjectEReaderTest {
    public interface Knowledge {
        long getEmc(); List<ItemStack> getKnowledge(); boolean hasFullKnowledge(); boolean hasKnowledge(ItemStack item);
    }
    public interface EMC {
        boolean hasValue(ItemStack item); long getValue(ItemStack item); long getSellValue(ItemStack item);
    }
    public interface Wrong { double getEmc(); List<ItemStack> getKnowledge(); boolean hasFullKnowledge(); boolean hasKnowledge(ItemStack item); }
    static final class FakeWorld extends World {
        Set<BlockPos> unloaded; BlockPos origin; IBlockState bridge, table; TileEntity tile;
        private FakeWorld() { super(null, null, null, null, false); }
        protected IChunkProvider createChunkProvider() { return null; }
        protected boolean isChunkLoaded(int x, int z, boolean empty) { return true; }
        public boolean isBlockLoaded(BlockPos pos) { return !unloaded.contains(pos); }
        public IBlockState getBlockState(BlockPos pos) {
            if (unloaded.contains(pos)) throw new AssertionError("unloaded block read");
            return pos.equals(origin) ? bridge : table;
        }
        public TileEntity getTileEntity(BlockPos pos) {
            if (unloaded.contains(pos)) throw new AssertionError("unloaded tile read");
            return tile;
        }
    }
    private static void endpoints() throws Exception {
        Field f = Unsafe.class.getDeclaredField("theUnsafe"); f.setAccessible(true);
        FakeWorld w = (FakeWorld)((Unsafe) f.get(null)).allocateInstance(FakeWorld.class);
        w.origin = new BlockPos(0, 64, 0); w.unloaded = new HashSet<>(); w.tile = new BridgeTile();
        w.tile.setWorld(w); w.tile.setPos(w.origin);
        w.bridge = new Block(Material.IRON).setRegistryName("nexustech", "storage_bridge").getDefaultState();
        w.table = new Block(Material.ROCK).setRegistryName("projecte", "transmutation_table").getDefaultState();
        for (EnumFacing side : EnumFacing.values()) {
            check(ProjectEReader.endpointValid(w, w.origin, side, 64), "adjacent table " + side);
            w.unloaded.add(w.origin.offset(side));
            check(!ProjectEReader.endpointValid(w, w.origin, side, 1), "unloaded neighbor " + side);
            w.unloaded.clear();
        }
        w.unloaded.add(w.origin);
        check(!ProjectEReader.endpointValid(w, w.origin, EnumFacing.EAST, 1), "unloaded bridge");
        w.unloaded.clear();
        check(!ProjectEReader.endpointValid(w, w.origin, EnumFacing.EAST, 64.01), "too far");
        check(!ProjectEReader.endpointValid(w, w.origin, EnumFacing.EAST, Double.NaN), "NaN distance");
        check(!ProjectEReader.endpointValid(w, w.origin, null, 1), "missing side");
        w.table = new Block(Material.ROCK).setRegistryName("projecte", "condenser").getDefaultState();
        check(!ProjectEReader.endpointValid(w, w.origin, EnumFacing.EAST, 1), "wrong PE block");
        w.table = new Block(Material.ROCK).setRegistryName("projecte", "transmutation_table").getDefaultState();
        w.tile.invalidate();
        check(!ProjectEReader.endpointValid(w, w.origin, EnumFacing.EAST, 1), "invalid bridge tile");
        w.tile = null;
        check(!ProjectEReader.endpointValid(w, w.origin, EnumFacing.EAST, 1), "missing bridge tile");
        w.tile = new BridgeTile(); w.tile.setWorld(w); w.tile.setPos(w.origin);
        w.bridge = new Block(Material.IRON).setRegistryName("other", "storage_bridge").getDefaultState();
        check(!ProjectEReader.endpointValid(w, w.origin, EnumFacing.EAST, 1), "wrong origin block");
    }
    private static int calls;
    private static boolean full, has, known, broken, negative, nullList;
    private static Object proxy(Class<?> type, boolean isKnowledge) {
        return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, (p, method, args) -> {
            calls++;
            if (broken) throw new IllegalStateException("simulated failure");
            switch (method.getName()) {
                case "getEmc": return negative ? -1L : Long.MAX_VALUE;
                case "getKnowledge": return nullList ? null : Arrays.asList(ItemStack.EMPTY, ItemStack.EMPTY);
                case "hasFullKnowledge": return full;
                case "hasKnowledge":
                    ((ItemStack) args[0]).setCount(1); return known;
                case "hasValue":
                    ((ItemStack) args[0]).setCount(1); return has;
                case "getValue": return 900L;
                case "getSellValue": return 450L;
                default: throw new AssertionError("Unexpected call: " + method);
            }
        });
    }
    private static void check(boolean value, String text) { if (!value) throw new AssertionError(text); }
    private static void contract(Class<?> knowledge, Class<?> emc) {
        Object p = proxy(knowledge, true), e = proxy(emc, false);
        ItemStack item = new ItemStack(Items.DIAMOND, 4);
        has = true; known = true; full = false; broken = false; negative = false; nullList = false; calls = 0;
        ProjectEReader.Result r = ProjectEReader.inspect(p, knowledge, e, emc, item);
        check(r.status == ProjectEReader.Status.OK && r.balance == Long.MAX_VALUE && r.knowledgeCount == 2
                && !r.fullKnowledge && r.itemKnown && r.itemHasValue && r.itemValue == 900 && r.itemSellValue == 450,
                "complete values and separate sale price");
        check(item.getCount() == 4 && calls == 7, "copies and exact read calls");
        full = true; known = false; has = false; calls = 0;
        r = ProjectEReader.inspect(p, knowledge, e, emc, item);
        check(r.status == ProjectEReader.Status.OK && r.fullKnowledge && !r.itemKnown && !r.itemHasValue
                && r.itemValue == 0 && r.itemSellValue == 0 && calls == 5, "full flag and no-value path");
        broken = true;
        check(ProjectEReader.inspect(p, knowledge, e, emc, item).status == ProjectEReader.Status.API_ERROR, "exception");
        broken = false; negative = true;
        check(ProjectEReader.inspect(p, knowledge, e, emc, item).status == ProjectEReader.Status.API_ERROR, "negative balance");
        negative = false; nullList = true;
        check(ProjectEReader.inspect(p, knowledge, e, emc, item).status == ProjectEReader.Status.API_ERROR, "null list");
        nullList = false;
        check(ProjectEReader.inspect(null, knowledge, e, emc, item).status == ProjectEReader.Status.API_ERROR, "null provider");
        check(ProjectEReader.inspect(p, knowledge, null, emc, item).status == ProjectEReader.Status.API_ERROR, "null proxy");
        check(ProjectEReader.inspect(p, knowledge, e, emc, ItemStack.EMPTY).status == ProjectEReader.Status.INVALID_ITEM, "empty stack");
        check(item.getCount() == 4, "caller stack not modified");
    }
    public static void main(String[] args) throws Exception {
        Bootstrap.register();
        endpoints();
        check(ProjectEReader.read(null, BlockPos.ORIGIN, EnumFacing.UP, ItemStack.EMPTY).status == ProjectEReader.Status.UNAVAILABLE, "null player fails closed");
        contract(Knowledge.class, EMC.class);
        ItemStack item = new ItemStack(Items.DIAMOND);
        check(ProjectEReader.inspect(proxy(Wrong.class, true), Wrong.class, proxy(EMC.class, false), EMC.class, item).status == ProjectEReader.Status.API_ERROR, "wrong return type");
        try {
            Class<?> k = Class.forName("moze_intel.projecte.api.capabilities.IKnowledgeProvider");
            Class<?> e = Class.forName("moze_intel.projecte.api.proxy.IEMCProxy");
            contract(k, e);
            System.out.println("PASS official ProjectE interface signatures with simulated providers");
        } catch (ClassNotFoundException absent) {
            System.out.println("SKIP official interfaces not on test classpath");
        }
        System.out.println("PASS optional PE read-only contract and fail-closed checks");
    }
}
