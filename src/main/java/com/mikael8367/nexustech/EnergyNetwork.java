package com.mikael8367.nexustech;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;

/** Iterative traversal: cables have no buffer. Never query an unloaded position. */
final class EnergyNetwork {
    private static final ThreadLocal<Boolean> ACTIVE = new ThreadLocal<Boolean>();
    private EnergyNetwork() { }
    static int send(World world, BlockPos origin, BlockPos excludedSource, int amount, boolean simulate, boolean batterySource) {
        if (world == null || world.isRemote || amount <= 0 || Boolean.TRUE.equals(ACTIVE.get()) || !world.isBlockLoaded(origin)) return 0;
        ACTIVE.set(true);
        try {
            Queue<BlockPos> queue = new ArrayDeque<>();
            Set<BlockPos> cables = new HashSet<>();
            Set<BlockPos> endpoints = new HashSet<>();
            queue.add(origin); cables.add(origin);
            int moved = 0;
            while (!queue.isEmpty() && moved < amount) {
                BlockPos current = queue.remove();
                for (EnumFacing direction : EnumFacing.VALUES) {
                    BlockPos next = current.offset(direction);
                    if (next.equals(excludedSource) || !world.isBlockLoaded(next)) continue;
                    TileEntity tile = world.getTileEntity(next);
                    if (tile instanceof MachineTile.Cable) {
                        if (cables.add(next)) queue.add(next);
                        continue;
                    }
                    if (tile == null || (batterySource && tile instanceof MachineTile.Battery)) continue;
                    // A block may expose different capabilities on different faces: only mark
                    // it visited after a face actually accepts energy.
                    if (endpoints.contains(next)) continue;
                    IEnergyStorage receiver = tile.getCapability(CapabilityEnergy.ENERGY, direction.getOpposite());
                    if (receiver == null || !receiver.canReceive()) continue;
                    int accepted = receiver.receiveEnergy(amount - moved, simulate);
                    accepted = Math.max(0, Math.min(amount - moved, accepted));
                    if (accepted > 0) { moved += accepted; endpoints.add(next); }
                    if (moved >= amount) break;
                }
            }
            return moved;
        } finally { ACTIVE.remove(); }
    }
}
