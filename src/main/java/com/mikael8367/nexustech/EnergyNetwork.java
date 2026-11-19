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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Iterative traversal: cables have no buffer. Never query an unloaded position. */
final class EnergyNetwork {
    private static final ThreadLocal<Boolean> ACTIVE = new ThreadLocal<Boolean>();
    private static final Logger LOGGER = LogManager.getLogger("NexusTech");
    private static final long LOG_INTERVAL_NANOS = 60_000_000_000L;
    private static long lastFailureLog;
    private static boolean hasLoggedFailure;
    private static long suppressedFailures;
    private EnergyNetwork() { }

    // A broken endpoint can be visited every tick. Limit log volume globally without
    // retaining world/tile references, and report the number suppressed next time.
    private static synchronized void logEndpointFailure(BlockPos pos, RuntimeException error) {
        long now = System.nanoTime();
        if (!hasLoggedFailure || now - lastFailureLog >= LOG_INTERVAL_NANOS) {
            LOGGER.warn("Forge Energy endpoint at {} failed ({} similar failures suppressed); skipping it for this traversal",
                    pos, suppressedFailures, error);
            lastFailureLog = now;
            hasLoggedFailure = true;
            suppressedFailures = 0;
        } else if (suppressedFailures < Long.MAX_VALUE) {
            suppressedFailures++;
        }
    }
    /**
     * Pull from loaded endpoints adjacent to the origin or its cable component.
     * A machine origin must be the excluded consumer; a cable origin can be
     * used by a sided external consumer. The shared guard also covers direct
     * neighbours and callbacks while resolving capabilities.
     */
    static int pull(World world, BlockPos origin, BlockPos excludedConsumer, int amount,
                    boolean simulate, boolean allowNexusBatteries) {
        if (world == null || origin == null || world.isRemote || amount <= 0
                || Boolean.TRUE.equals(ACTIVE.get()) || !world.isBlockLoaded(origin)) return 0;
        ACTIVE.set(true);
        try {
            TileEntity start = world.getTileEntity(origin);
            if (!(start instanceof MachineTile.Cable)
                    && (!(start instanceof MachineTile) || !origin.equals(excludedConsumer))) return 0;
            Queue<BlockPos> queue = new ArrayDeque<>();
            Set<BlockPos> cables = new HashSet<>();
            Set<BlockPos> endpoints = new HashSet<>();
            queue.add(origin); cables.add(origin);
            int moved = 0;
            while (!queue.isEmpty() && moved < amount) {
                BlockPos current = queue.remove();
                for (EnumFacing direction : EnumFacing.VALUES) {
                    BlockPos next = current.offset(direction);
                    if (next.equals(excludedConsumer) || !world.isBlockLoaded(next)) continue;
                    TileEntity tile = world.getTileEntity(next);
                    if (tile instanceof MachineTile.Cable) {
                        if (cables.add(next)) queue.add(next);
                        continue;
                    }
                    if (tile == null || (!allowNexusBatteries && tile instanceof MachineTile.Battery)
                            || endpoints.contains(next)) continue;
                    // Try other faces only until this endpoint supplies energy.
                    try {
                        IEnergyStorage source = tile.getCapability(CapabilityEnergy.ENERGY, direction.getOpposite());
                        if (source == null || !source.canExtract()) continue;
                        int extracted = source.extractEnergy(amount - moved, simulate);
                        extracted = Math.max(0, Math.min(amount - moved, extracted));
                        if (extracted > 0) { moved += extracted; endpoints.add(next); }
                    } catch (RuntimeException error) {
                        // No return value is available if the endpoint mutated before throwing.
                        // Do not retry another face in this traversal; preserve earlier transfers.
                        endpoints.add(next);
                        logEndpointFailure(next, error);
                    }
                    if (moved >= amount) break;
                }
            }
            return moved;
        } finally { ACTIVE.remove(); }
    }
    private static boolean exposesExtraction(TileEntity tile) {
        for (EnumFacing face : EnumFacing.VALUES) {
            IEnergyStorage storage = tile.getCapability(CapabilityEnergy.ENERGY, face);
            if (storage != null && storage.canExtract()) return true;
        }
        return false;
    }
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
                    try {
                        IEnergyStorage receiver = tile.getCapability(CapabilityEnergy.ENERGY, direction.getOpposite());
                        if (receiver == null || !receiver.canReceive()) continue;
                        // A storage endpoint can be empty after a pull; excluding only
                        // this tick's donors makes it bounce back on the next tick.
                        // Inspect every face because input and output may be sided.
                        // Conservatively let such endpoints pull, rather than push to them.
                        if (batterySource && exposesExtraction(tile)) continue;
                        int accepted = receiver.receiveEnergy(amount - moved, simulate);
                        accepted = Math.max(0, Math.min(amount - moved, accepted));
                        if (accepted > 0) { moved += accepted; endpoints.add(next); }
                    } catch (RuntimeException error) {
                        // A throwing endpoint may have mutated without reporting how much.
                        // Skip its other faces; completed transfers remain accounted for.
                        endpoints.add(next);
                        logEndpointFailure(next, error);
                    }
                    if (moved >= amount) break;
                }
            }
            return moved;
        } finally { ACTIVE.remove(); }
    }
}
