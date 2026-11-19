package com.mikael8367.nexustech;

import java.util.Arrays;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IContainerListener;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.fml.common.Loader;

/** Each viewer has independent server-authoritative diagnostics. No slots or actions. */
public final class BridgeContainer extends Container {
    private final BridgeTile tile;
    private final EntityPlayer viewer;
    private final int[] values = new int[7];
    private final int[] last = new int[7];
    private int peState = ProjectEReadOnly.WAITING;
    private final int[] parts = new int[6];
    private int polls = 20;
    private int lastPeState = -1, lastCount = -1;
    private long lastEmc;
    private boolean peSent;
    public BridgeContainer(EntityPlayer viewer, BridgeTile tile) {
        this.viewer = viewer; this.tile = tile;
        Arrays.fill(values, BridgeDiagnostics.WAITING);
        Arrays.fill(last, -1);
    }
    @Override public boolean canInteractWith(EntityPlayer player) {
        return player != null && player == viewer && tile.getWorld() != null && player.world == tile.getWorld()
            && !tile.isInvalid() && tile.getWorld().isBlockLoaded(tile.getPos())
            && tile.getWorld().getTileEntity(tile.getPos()) == tile
            && tile.getWorld().getBlockState(tile.getPos()).getBlock() == ModRegistry.BRIDGE
            && player.getDistanceSq(tile.getPos().getX() + .5, tile.getPos().getY() + .5, tile.getPos().getZ() + .5) <= 64.0;
    }
    private int read(int i) {
        if (i == 6) return (Loader.isModLoaded("projecte") ? 1 : 0) | (Loader.isModLoaded("storagenetwork") ? 2 : 0);
        return BridgeDiagnostics.inspect(tile.getWorld(), tile.getPos(), EnumFacing.values()[i]);
    }
    @Override public void addListener(IContainerListener listener) {
        // Container.addListener itself calls detectAndSendChanges: force one initial broadcast there.
        Arrays.fill(last, -1);
        polls = 20;
        peSent = false;
        super.addListener(listener);
    }
    @Override public void detectAndSendChanges() {
        super.detectAndSendChanges();
        if (tile.getWorld() == null || tile.getWorld().isRemote || !canInteractWith(viewer)) return;
        for (int i = 0; i < values.length; i++) {
            int current = read(i);
            if (last[i] != current) {
                for (IContainerListener listener : listeners) listener.sendWindowProperty(this, i, current);
                last[i] = current;
            }
        }
        if (++polls < 20) return;
        polls = 0;
        ProjectEReadOnly.Snapshot snapshot = ProjectEReadOnly.read(viewer);
        if (!peSent || snapshot.state != lastPeState || snapshot.emc != lastEmc || snapshot.count != lastCount) {
            for (IContainerListener listener : listeners) {
                // Invalidate before the parts; publish status only after the entire snapshot.
                listener.sendWindowProperty(this, 7, ProjectEReadOnly.WAITING);
                if (snapshot.state == ProjectEReadOnly.OK || snapshot.state == ProjectEReadOnly.FULL) {
                    for (int i = 0; i < 4; i++) listener.sendWindowProperty(this, 8 + i, (int)(snapshot.emc >>> (i * 16)) & 0xffff);
                    for (int i = 0; i < 2; i++) listener.sendWindowProperty(this, 12 + i, (snapshot.count >>> (i * 16)) & 0xffff);
                }
                listener.sendWindowProperty(this, 7, snapshot.state);
            }
            peSent = true; lastPeState = snapshot.state; lastEmc = snapshot.emc; lastCount = snapshot.count;
        }
    }
    @Override public void updateProgressBar(int id, int value) {
        if (id >= 0 && id < values.length && value >= 0 && value <= (id == 6 ? 3 : BridgeDiagnostics.UNLOADED)) values[id] = value;
        else if (id == 7 && value >= ProjectEReadOnly.WAITING && value <= ProjectEReadOnly.FULL) peState = value;
        else if (id >= 8 && id <= 13 && value >= Short.MIN_VALUE && value <= 0xffff) parts[id - 8] = value & 0xffff;
    }
    public int getValue(int i) { return values[i]; }
    public int getPeState() { return peState; }
    public long getEmc() {
        long value = 0;
        for (int i = 0; i < 4; i++) value |= (long)parts[i] << (16 * i);
        return value;
    }
    public int getLearnedCount() { return parts[4] | (parts[5] << 16); }
}
