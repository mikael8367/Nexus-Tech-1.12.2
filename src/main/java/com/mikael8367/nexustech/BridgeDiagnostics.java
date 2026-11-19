package com.mikael8367.nexustech;

import net.minecraft.util.ResourceLocation;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Classification is read-only, not proof of network connectivity or access. */
public final class BridgeDiagnostics {
    public static final int NONE = 0, TABLE = 1, OTHER_PE = 2, MASTER = 3,
        REQUEST = 4, INVENTORY = 5, SSN_CABLE = 6, OTHER_SSN = 7, UNLOADED = 8, WAITING = 9;
    private BridgeDiagnostics() { }
    public static int classify(ResourceLocation id) {
        if (id == null) return NONE;
        String mod = id.getResourceDomain(), path = id.getResourcePath();
        if (mod.equals("projecte")) return path.equals("transmutation_table") ? TABLE : OTHER_PE;
        if (!mod.equals("storagenetwork")) return NONE;
        switch (path) {
            case "master": return MASTER;
            case "request": return REQUEST;
            case "inventory": return INVENTORY;
            case "simple_kabel": case "kabel": case "storage_kabel":
            case "ex_kabel": case "im_kabel": case "process_kabel": return SSN_CABLE;
            default: return OTHER_SSN;
        }
    }
    public static int inspect(World world, BlockPos pos, EnumFacing side) {
        BlockPos neighbor = pos.offset(side);
        if (!world.isBlockLoaded(neighbor)) return UNLOADED;
        return classify(world.getBlockState(neighbor).getBlock().getRegistryName());
    }
}
