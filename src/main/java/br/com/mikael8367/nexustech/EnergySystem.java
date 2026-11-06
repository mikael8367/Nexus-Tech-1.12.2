package br.com.mikael8367.nexustech;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.server.management.PlayerChunkMapEntry;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;

final class EnergyBuffer implements IEnergyStorage {
    private final int capacity, maxReceive, maxExtract;
    private int energy;
    EnergyBuffer(int capacity, int receive, int extract) {
        if (capacity <= 0 || receive < 0 || extract < 0) throw new IllegalArgumentException("Invalid FE limits");
        this.capacity = capacity; maxReceive = receive; maxExtract = extract;
    }
    @Override public int receiveEnergy(int amount, boolean simulate) {
        if (maxReceive == 0 || amount <= 0) return 0;
        int accepted = Math.min(capacity - energy, Math.min(maxReceive, amount));
        if (!simulate) energy += accepted;
        return accepted;
    }
    @Override public int extractEnergy(int amount, boolean simulate) {
        if (maxExtract == 0 || amount <= 0) return 0;
        int extracted = Math.min(energy, Math.min(maxExtract, amount));
        if (!simulate) energy -= extracted;
        return extracted;
    }
    /** Internal generation: ignores the external receive rate, never exceeds capacity. */
    int addInternal(int amount) {
        if (amount <= 0) return 0;
        int accepted = Math.min(capacity - energy, amount);
        energy += accepted;
        return accepted;
    }
    /**
     * Internal consumption by the machine that owns this buffer. It ignores the external
     * extract rate (consumers expose 0 FE/t extraction to other blocks) and is all-or-nothing.
     */
    boolean consumeInternal(int amount) {
        if (amount <= 0 || amount > energy) return false;
        energy -= amount;
        return true;
    }
    void setEnergyStored(int amount) { energy = Math.max(0, Math.min(capacity, amount)); }
    int getCapacity() { return capacity; }
    @Override public int getEnergyStored() { return energy; }
    @Override public int getMaxEnergyStored() { return capacity; }
    @Override public boolean canExtract() { return maxExtract > 0 && energy > 0; }
    @Override public boolean canReceive() { return maxReceive > 0 && energy < capacity; }
}

abstract class AbstractEnergyTile extends TileEntity {
    private static final int SYNC_INTERVAL = 10;
    protected final EnergyBuffer energy;
    /** World tick at which energy last arrived through each face; used to avoid sending it straight back. */
    private final long[] lastReceivedTick = {Long.MIN_VALUE, Long.MIN_VALUE, Long.MIN_VALUE,
            Long.MIN_VALUE, Long.MIN_VALUE, Long.MIN_VALUE};
    private final SidedEnergy[] sided = new SidedEnergy[6];
    private long lastSyncedState = Long.MIN_VALUE;

    AbstractEnergyTile(int capacity, int receive, int extract) {
        energy = new EnergyBuffer(capacity, receive, extract);
        for (EnumFacing side : EnumFacing.values()) sided[side.getIndex()] = new SidedEnergy(this, side);
    }
    @Override public boolean hasCapability(net.minecraftforge.common.capabilities.Capability<?> cap, EnumFacing side) {
        return cap == CapabilityEnergy.ENERGY || super.hasCapability(cap, side);
    }
    @Override @SuppressWarnings("unchecked")
    public <T> T getCapability(net.minecraftforge.common.capabilities.Capability<T> cap, EnumFacing side) {
        if (cap == CapabilityEnergy.ENERGY) return (T) (side == null ? energy : sided[side.getIndex()]);
        return super.getCapability(cap, side);
    }
    int getStoredEnergy() { return energy.getEnergyStored(); }
    int getEnergyCapacity() { return energy.getCapacity(); }

    void noteReceivedFrom(EnumFacing side) {
        if (side != null && world != null) lastReceivedTick[side.getIndex()] = world.getTotalWorldTime();
    }
    boolean receivedRecentlyFrom(EnumFacing side) {
        if (world == null) return false;
        long last = lastReceivedTick[side.getIndex()];
        return last != Long.MIN_VALUE && world.getTotalWorldTime() - last <= 1L;
    }

    /** Compact fingerprint of everything the client screen displays. */
    protected long syncState() { return energy.getEnergyStored(); }

    /**
     * Saves and sends the tile to watching clients only when the displayed state changed.
     * Sends a tile packet instead of notifyBlockUpdate so clients do not rebuild the chunk mesh.
     */
    protected void syncIfChanged() {
        if (world == null || world.isRemote || world.getTotalWorldTime() % SYNC_INTERVAL != 0L) return;
        long state = syncState();
        if (state == lastSyncedState) return;
        lastSyncedState = state;
        markDirty();
        if (world instanceof WorldServer) {
            PlayerChunkMapEntry entry = ((WorldServer) world).getPlayerChunkMap()
                    .getEntry(pos.getX() >> 4, pos.getZ() >> 4);
            if (entry != null) {
                SPacketUpdateTileEntity packet = getUpdatePacket();
                if (packet != null) entry.sendPacket(packet);
            }
        }
    }
    @Override public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(pos, 1, getUpdateTag());
    }
    @Override public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }
    @Override public void handleUpdateTag(NBTTagCompound tag) { readFromNBT(tag); }
    @Override public void onDataPacket(NetworkManager network, SPacketUpdateTileEntity packet) {
        readFromNBT(packet.getNbtCompound());
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        tag.setInteger("NexusEnergy", energy.getEnergyStored());
        return super.writeToNBT(tag);
    }
    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        energy.setEnergyStored(tag.getInteger("NexusEnergy"));
    }
}

/** Per-face view of a tile buffer that remembers which faces supplied energy. */
final class SidedEnergy implements IEnergyStorage {
    private final AbstractEnergyTile owner;
    private final EnumFacing side;
    SidedEnergy(AbstractEnergyTile owner, EnumFacing side) { this.owner = owner; this.side = side; }
    @Override public int receiveEnergy(int amount, boolean simulate) {
        int accepted = owner.energy.receiveEnergy(amount, simulate);
        if (!simulate && accepted > 0) owner.noteReceivedFrom(side);
        return accepted;
    }
    @Override public int extractEnergy(int amount, boolean simulate) { return owner.energy.extractEnergy(amount, simulate); }
    @Override public int getEnergyStored() { return owner.energy.getEnergyStored(); }
    @Override public int getMaxEnergyStored() { return owner.energy.getMaxEnergyStored(); }
    @Override public boolean canExtract() { return owner.energy.canExtract(); }
    @Override public boolean canReceive() { return owner.energy.canReceive(); }
}

final class EnergyTransfer {
    private EnergyTransfer() { }

    /**
     * Amount a cable may hand to a neighbouring Nexus cable: only downhill, and only half
     * the difference so two cables converge instead of bouncing energy back and forth.
     */
    static int cableShare(int mine, int theirs, int budget) {
        if (budget <= 0 || mine <= theirs) return 0;
        return Math.min(budget, (mine - theirs) / 2);
    }

    static boolean push(AbstractEnergyTile source, int limit) {
        World world = source.getWorld();
        EnergyBuffer buffer = source.energy;
        if (world == null || world.isRemote || limit <= 0) return false;
        int initial = buffer.getEnergyStored();
        int budget = Math.min(limit, initial);
        BlockPos origin = source.getPos();
        boolean sourceIsCable = source instanceof TileEnergyCable;
        for (EnumFacing side : EnumFacing.values()) {
            if (budget <= 0) break;
            // Never return energy through the face it just arrived from (external batteries/cables).
            if (source.receivedRecentlyFrom(side)) continue;
            BlockPos targetPos = origin.offset(side);
            if (!world.isBlockLoaded(targetPos)) continue;
            TileEntity target = world.getTileEntity(targetPos);
            if (target == null || target == source || !target.hasCapability(CapabilityEnergy.ENERGY, side.getOpposite())) continue;
            IEnergyStorage receiver = target.getCapability(CapabilityEnergy.ENERGY, side.getOpposite());
            if (receiver == null || !receiver.canReceive()) continue;
            int offer = budget;
            if (sourceIsCable && target instanceof TileEnergyCable) {
                offer = cableShare(buffer.getEnergyStored(), receiver.getEnergyStored(), budget);
                if (offer <= 0) continue;
            }
            int simulated = receiver.receiveEnergy(buffer.extractEnergy(offer, true), true);
            if (simulated <= 0) continue;
            int extracted = buffer.extractEnergy(simulated, false);
            int inserted = Math.max(0, Math.min(extracted, receiver.receiveEnergy(extracted, false)));
            if (inserted < extracted) buffer.addInternal(extracted - inserted);
            budget -= inserted;
        }
        return initial != buffer.getEnergyStored();
    }

    static boolean pull(AbstractEnergyTile destination, int limit) {
        World world = destination.getWorld();
        EnergyBuffer buffer = destination.energy;
        if (world == null || world.isRemote || limit <= 0 || !buffer.canReceive()) return false;
        int initial = buffer.getEnergyStored();
        int budget = Math.min(limit, buffer.getMaxEnergyStored() - initial);
        BlockPos origin = destination.getPos();
        for (EnumFacing side : EnumFacing.values()) {
            if (budget <= 0) break;
            BlockPos sourcePos = origin.offset(side);
            if (!world.isBlockLoaded(sourcePos)) continue;
            TileEntity source = world.getTileEntity(sourcePos);
            // Nexus producers and cables push on their own tick; pulling from them too would double-move FE.
            if (source == null || source == destination || source instanceof AbstractEnergyTile
                    || !source.hasCapability(CapabilityEnergy.ENERGY, side.getOpposite())) continue;
            IEnergyStorage provider = source.getCapability(CapabilityEnergy.ENERGY, side.getOpposite());
            if (provider == null || !provider.canExtract()) continue;
            int accepted = buffer.receiveEnergy(provider.extractEnergy(budget, true), true);
            if (accepted <= 0) continue;
            int extracted = Math.max(0, Math.min(accepted, provider.extractEnergy(accepted, false)));
            if (extracted <= 0) continue;
            // Keep FE conserved if a third-party provider changes its response between simulation and commit.
            int inserted = buffer.receiveEnergy(extracted, false);
            if (inserted < extracted) buffer.addInternal(extracted - inserted);
            destination.noteReceivedFrom(side);
            budget -= extracted;
        }
        return initial != buffer.getEnergyStored();
    }
}
