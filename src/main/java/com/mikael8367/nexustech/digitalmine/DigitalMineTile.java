package com.mikael8367.nexustech.digitalmine;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import java.util.HashSet;
import java.util.Set;
import java.util.Random;
import javax.annotation.Nullable;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.oredict.OreDictionary;

public abstract class DigitalMineTile extends TileEntity implements ITickable {
    public final boolean advanced;
    public final int capacity, radius, baseCost, baseDelay;
    public int energy, mode, cursor, delay, status;
    public boolean enabled = true;
    private UUID owner;
    private String ownerName = "";
    private boolean transferring, mining, deliveringDrops, removalDeferred, drainingRemoval;
    private BlockPos blockedTarget;
    private IBlockState blockedState;
    private final NonNullList<ItemStack> pendingDrops = NonNullList.create();
    public final ItemStackHandler inventory = new ItemStackHandler(27) {
        @Override protected void onContentsChanged(int slot) { blockedTarget = null; blockedState = null; if (world != null && !world.isRemote) markDirty(); }
    };
    private final IItemHandler output = new IItemHandler() {
        public int getSlots() { return inventory.getSlots(); }
        public ItemStack getStackInSlot(int slot) { return inventory.getStackInSlot(slot).copy(); }
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) { return stack; }
        public ItemStack extractItem(int slot, int amount, boolean simulate) { return inventory.extractItem(slot, amount, simulate); }
        public int getSlotLimit(int slot) { return inventory.getSlotLimit(slot); }
    };
    private final IEnergyStorage storage = new IEnergyStorage() {
        public int receiveEnergy(int amount, boolean simulate) {
            if (amount <= 0 || transferring || world == null || world.isRemote || isInvalid() || !world.isBlockLoaded(pos)) return 0;
            int n = Math.min(Math.min(amount, 1000), capacity - energy);
            if (!simulate && n > 0) { energy += n; markDirty(); } return n;
        }
        public int extractEnergy(int amount, boolean simulate) { return 0; }
        public int getEnergyStored() { return energy; }
        public int getMaxEnergyStored() { return capacity; }
        public boolean canExtract() { return false; }
        public boolean canReceive() { return true; }
    };
    protected DigitalMineTile(boolean advanced) {
        this.advanced = advanced; capacity = advanced ? 120000 : 40000;
        radius = advanced ? 12 : 8; baseCost = advanced ? 1800 : 1200; baseDelay = advanced ? 20 : 40;
    }
    public static final class Basic extends DigitalMineTile { public Basic() { super(false); } }
    public static final class Advanced extends DigitalMineTile { public Advanced() { super(true); } }
    public void setOwner(EntityPlayer p) { owner = p.getUniqueID(); ownerName = p.getName(); markDirty(); }
    public boolean authorized(EntityPlayer p) { return owner != null && owner.equals(p.getUniqueID()); }
    @Override public boolean hasCapability(Capability<?> cap, @Nullable EnumFacing side) {
        return cap == CapabilityEnergy.ENERGY || cap == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY || super.hasCapability(cap, side);
    }
    @Override public <T> T getCapability(Capability<T> cap, @Nullable EnumFacing side) {
        if (cap == CapabilityEnergy.ENERGY) return CapabilityEnergy.ENERGY.cast(storage);
        if (cap == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(output);
        return super.getCapability(cap, side);
    }
    /** Recomputed reservations: no stale claim survives an invalid controller. */
    public boolean structureValid() {
        if (world == null || !world.isBlockLoaded(pos) || isInvalid() || world.getTileEntity(pos) != this) return false;
        IBlockState own = world.getBlockState(pos);
        if (!(own.getBlock() instanceof DigitalMineBlock)) return false;
        EnumFacing facing = own.getValue(DigitalMineBlock.FACING);
        if (physicalStructure(pos, facing) != 1) return false;
        // Every possible controller whose footprint could touch ours must be known.
        // Unknown chunks are NOT free, even when the current 18 cells are loaded.
        for (int dy = -1; dy <= 1; dy++) for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) {
            BlockPos candidate = pos.add(dx, dy, dz);
            if (candidate.equals(pos) || candidate.getY() < 0 || candidate.getY() >= world.getHeight()) continue;
            boolean possible = false;
            for (EnumFacing direction : EnumFacing.HORIZONTALS) {
                if (overlaps(pos, facing, candidate, direction)) { possible = true; break; }
            }
            if (!possible) continue;
            if (!world.isBlockLoaded(candidate)) return false;
            IBlockState other = world.getBlockState(candidate);
            if (!(other.getBlock() instanceof DigitalMineBlock)) continue;
            TileEntity otherTile = world.getTileEntity(candidate);
            if (!(otherTile instanceof DigitalMineTile) || otherTile.isInvalid()) continue;
            EnumFacing otherFacing = other.getValue(DigitalMineBlock.FACING);
            if (!overlaps(pos, facing, candidate, otherFacing)) continue;
            int physical = physicalStructure(candidate, otherFacing);
            if (physical < 0) return false; // incomplete knowledge, not a free reservation
            // The lexicographically smaller valid structure wins. Both sides calculate
            // the same winner without chunk-persistent mutable claims or cleanup races.
            if (physical == 1 && comparePositions(candidate, pos) < 0) return false;
        }
        return true;
    }
    private static int comparePositions(BlockPos a, BlockPos b) {
        int x = Integer.compare(a.getX(), b.getX());
        if (x != 0) return x;
        int y = Integer.compare(a.getY(), b.getY());
        return y != 0 ? y : Integer.compare(a.getZ(), b.getZ());
    }
    private static boolean overlaps(BlockPos a, EnumFacing af, BlockPos b, EnumFacing bf) {
        if (Math.abs(a.getY() - b.getY()) > 1) return false;
        EnumFacing ab = af.getOpposite(), ar = af.rotateY(), bb = bf.getOpposite(), br = bf.rotateY();
        int ax0 = a.getX() + Math.min(0, 2 * ab.getFrontOffsetX()) - Math.abs(ar.getFrontOffsetX());
        int ax1 = a.getX() + Math.max(0, 2 * ab.getFrontOffsetX()) + Math.abs(ar.getFrontOffsetX());
        int az0 = a.getZ() + Math.min(0, 2 * ab.getFrontOffsetZ()) - Math.abs(ar.getFrontOffsetZ());
        int az1 = a.getZ() + Math.max(0, 2 * ab.getFrontOffsetZ()) + Math.abs(ar.getFrontOffsetZ());
        int bx0 = b.getX() + Math.min(0, 2 * bb.getFrontOffsetX()) - Math.abs(br.getFrontOffsetX());
        int bx1 = b.getX() + Math.max(0, 2 * bb.getFrontOffsetX()) + Math.abs(br.getFrontOffsetX());
        int bz0 = b.getZ() + Math.min(0, 2 * bb.getFrontOffsetZ()) - Math.abs(br.getFrontOffsetZ());
        int bz1 = b.getZ() + Math.max(0, 2 * bb.getFrontOffsetZ()) + Math.abs(br.getFrontOffsetZ());
        return ax0 <= bx1 && bx0 <= ax1 && az0 <= bz1 && bz0 <= az1;
    }
    private static Set<BlockPos> structureCells(BlockPos controller, EnumFacing front) {
        Set<BlockPos> cells = new HashSet<>();
        for (int depth = 0; depth < 3; depth++) for (int y = 0; y < 2; y++) for (int x = -1; x <= 1; x++)
            cells.add(controller.offset(front.getOpposite(), depth).offset(front.rotateY(), x).up(y));
        return cells;
    }
    /** 1 complete, 0 known invalid, -1 unknown because a cell is unloaded. */
    private int physicalStructure(BlockPos controller, EnumFacing facing) {
        boolean missing = false, unknown = false;
        for (BlockPos p : structureCells(controller, facing)) {
            if (!world.isBlockLoaded(p)) { unknown = true; continue; }
            if (!p.equals(controller) && world.getBlockState(p).getBlock() != DigitalMineRegistry.CASING) missing = true;
        }
        return missing ? 0 : unknown ? -1 : 1;
    }
    private void pullEnergy() {
        int room = Math.min(1000, capacity - energy);
        if (room <= 0) return;
        transferring = true;
        try {
            for (EnumFacing side : EnumFacing.VALUES) {
                BlockPos p = pos.offset(side);
                if (room <= 0) break;
                if (!world.isBlockLoaded(p)) continue;
                try {
                    TileEntity te = world.getTileEntity(p);
                    if (te == null || te == this || !te.hasCapability(CapabilityEnergy.ENERGY, side.getOpposite())) continue;
                    IEnergyStorage other = te.getCapability(CapabilityEnergy.ENERGY, side.getOpposite());
                    if (other == null || !other.canExtract()) continue;
                    // Credit only a successful return. A provider that mutates then
                    // throws may lose FE: the API offers no transaction or rollback.
                    int n = Math.max(0, Math.min(room, other.extractEnergy(room, false)));
                    if (n > 0) { energy += n; room -= n; markDirty(); }
                } catch (RuntimeException badEndpoint) {
                    // Isolate this side and continue with the other neighbours.
                }
            }
        } finally { transferring = false; }
    }
    @Override public void update() {
        if (world == null || world.isRemote || isInvalid() || mining || transferring || !world.isBlockLoaded(pos)) return;
        if (!pendingDrops.isEmpty()) {
            deliverPending(pos);
            if (!pendingDrops.isEmpty()) return;
        }
        if (!enabled) { status = 0; return; }
        if (owner == null) { status = 1; return; }
        if (!structureValid()) { status = 2; return; }
        pullEnergy();
        int cost = mode == 0 ? baseCost : baseCost * 2;
        if (energy < cost) { status = 3; return; }
        if (delay > 0) { delay--; markDirty(); status = 4; return; }
        int width = radius * 2 + 1;
        int layers = Math.max(0, pos.getY() - 1); // y = controllerY - 1 down to y=1
        int total = width * width * layers;
        if (total == 0) { status = 5; return; }
        if (cursor < 0 || cursor >= total) cursor = 0;
        boolean skippedUnloaded = false;
        for (int budget = 0; budget < Math.min(128, total); budget++) {
            int n = cursor, layer = n / (width * width), square = n % (width * width);
            BlockPos target = new BlockPos(pos.getX() + square % width - radius, pos.getY() - 1 - layer, pos.getZ() + square / width - radius);
            if (!world.isBlockLoaded(target)) { skippedUnloaded = true; cursor = (cursor + 1) % total; markDirty(); continue; }
            IBlockState state = world.getBlockState(target);
            Block block = state.getBlock();
            if (isOre(state) && !block.hasTileEntity(state) && world.getTileEntity(target) == null
                    && state.getBlockHardness(world, target) >= 0 && state.getBlockHardness(world, target) <= 50
                    && world.getWorldBorder().contains(target)) {
                int result = mine(target, state, cost);
                if (result == 1) { cursor = (cursor + 1) % total; delay = mode == 0 ? baseDelay : Math.max(1, baseDelay / 2); status = 4; markDirty(); return; }
                if (result == 2) { status = 7; return; }
            }
            cursor = (cursor + 1) % total; markDirty();
        }
        status = skippedUnloaded ? 6 : 5;
    }
    private boolean isOre(IBlockState state) {
        Item item = Item.getItemFromBlock(state.getBlock());
        if (item == null || item == net.minecraft.init.Items.AIR) return false;
        ItemStack stack = new ItemStack(item, 1, state.getBlock().getMetaFromState(state));
        for (int id : OreDictionary.getOreIDs(stack)) {
            String name = OreDictionary.getOreName(id);
            if (name.startsWith("ore") && name.length() > 3) return true;
        }
        return false;
    }
    /** 1 mined, 2 no room, 0 skipped/denied. Never loads chunks. */
    private int mine(BlockPos target, IBlockState state, int cost) {
        if (mining) return 0;
        if (target.equals(blockedTarget) && state == blockedState) return 2;
        blockedTarget = null; blockedState = null;
        mining = true;
        try { return mineOnce(target, state, cost); } finally { mining = false; finishDeferredRemoval(); }
    }
    private int mineOnce(BlockPos target, IBlockState state, int cost) {
        if (!(world instanceof WorldServer) || owner == null) return 0;
        FakePlayer fake = FakePlayerFactory.get((WorldServer) world, new GameProfile(owner, ownerName));
        // ForgeHooks.onBlockBreakEvent sends packets through player.connection, which
        // is null for Forge FakePlayer. Post the same protection event directly.
        fake.setPosition(target.getX()+0.5, target.getY()+0.5, target.getZ()+0.5);
        if (!world.isBlockModifiable(fake, target)) return 0;
        net.minecraftforge.event.world.BlockEvent.BreakEvent event = new net.minecraftforge.event.world.BlockEvent.BreakEvent(world, target, state, fake);
        if (net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(event) || event.isCanceled()
                || !world.isBlockLoaded(target) || world.getBlockState(target) != state) return 0;
        // Protection listeners can remove the controller or spend its energy.
        // Do not fire harvest callbacks after that has already happened.
        if (!enabled || removalDeferred || isInvalid() || !world.isBlockLoaded(pos)
                || world.getTileEntity(pos) != this || energy < cost) return 0;
        NonNullList<ItemStack> drops = NonNullList.create();
        state.getBlock().getDrops(drops, world, target, state, 0);
        if (!enabled || removalDeferred || isInvalid() || !world.isBlockLoaded(pos)
                || world.getTileEntity(pos) != this || energy < cost
                || !world.isBlockLoaded(target) || world.getBlockState(target) != state) return 0;
        // Match Forge harvest-drop processing exactly once; plugins can edit the
        // list and chance, including removing every drop.
        drops = harvestDrops(drops, world, target, state, fake, world.rand);
        if (!canStoreDrops(target, state, drops)) return 2;
        if (!enabled || !world.isBlockLoaded(target) || world.getBlockState(target) != state || energy < cost || isInvalid() || !world.isBlockLoaded(pos) || world.getTileEntity(pos) != this || !world.isBlockModifiable(fake, target) || !structureValid()) return 0;
        if (!removeTarget(target, state, fake, cost)) return 0;
        // Preserve drops before invoking further callbacks, which may remove
        // this controller. Third-party callbacks are not a transaction.
        for (ItemStack drop : drops) queueDrop(drop);
        deliverPending(target);
        if (!isInvalid() && world.isBlockLoaded(pos) && world.getTileEntity(pos) == this) markDirty();
        state.getBlock().onBlockDestroyedByPlayer(world, target, state);
        int xp = event.getExpToDrop();
        if (xp > 0) state.getBlock().dropXpOnBlockBreak(world, target, xp);
        return 1;
    }
    boolean removeTarget(BlockPos target, IBlockState state, EntityPlayer fake, int cost) {
        // Reserve FE before removal callbacks so ordinary reentrancy cannot
        // spend it twice. Restore the reservation if removal reports failure.
        energy -= cost;
        boolean removed;
        try { removed = state.getBlock().removedByPlayer(state, world, target, fake, false); }
        catch (RuntimeException failure) {
            energy = (int) Math.min(capacity, Math.max(0L, (long) energy + cost));
            if (!isInvalid() && world.isBlockLoaded(pos) && world.getTileEntity(pos) == this) markDirty();
            throw failure;
        }
        if (!removed || (world.isBlockLoaded(target) && world.getBlockState(target) == state)) {
            energy = (int) Math.min(capacity, Math.max(0L, (long) energy + cost));
            if (!isInvalid() && world.isBlockLoaded(pos) && world.getTileEntity(pos) == this) markDirty();
            return false;
        }
        return true;
    }
    // Cache only the inventory-blocked case. A slot change wakes the target;
    // unlike re-firing Forge callbacks every tick, this has bounded side effects.
    boolean canStoreDrops(BlockPos target, IBlockState state, Iterable<ItemStack> drops) {
        ItemStackHandler simulated = new ItemStackHandler(inventory.getSlots());
        for (int i = 0; i < inventory.getSlots(); i++) simulated.setStackInSlot(i, inventory.getStackInSlot(i).copy());
        for (ItemStack drop : drops) {
            ItemStack left = drop.copy();
            for (int i = 0; i < simulated.getSlots() && !left.isEmpty(); i++) left = simulated.insertItem(i, left, false);
            if (!left.isEmpty()) { blockedTarget = target.toImmutable(); blockedState = state; return false; }
        }
        return true;
    }
    // Overflow goes to per-dimension saved data, not to a detached tile. Its
    // later explicit spawn bypasses doTileDrops but still fires Forge events.
    void deliverPending(BlockPos fallback) {
        if (deliveringDrops) return;
        deliveringDrops = true;
        try {
            for (int index = 0; index < pendingDrops.size();) {
                ItemStack left = pendingDrops.get(index);
                boolean attached = !isInvalid() && world.isBlockLoaded(pos) && world.getTileEntity(pos) == this && !removalDeferred;
                if (attached) for (int i = 0; i < inventory.getSlots() && !left.isEmpty() && !removalDeferred; i++)
                    left = inventory.insertItem(i, left, false);
                if (!left.isEmpty()) DigitalMineRecovery.get(world).enqueue(fallback, left);
                pendingDrops.remove(index);
                if (removalDeferred) break;
            }
            if (!isInvalid() && world.isBlockLoaded(pos) && world.getTileEntity(pos) == this) markDirty();
        } finally { deliveringDrops = false; finishDeferredRemoval(); }
    }
    // Defer a reentrant block removal until the current inventory operation has
    // returned its remainder; otherwise a callback can copy the same drop twice.
    void onControllerRemoved() {
        removalDeferred = true;
        finishDeferredRemoval();
    }
    private void finishDeferredRemoval() {
        if (!removalDeferred || mining || deliveringDrops || drainingRemoval) return;
        drainingRemoval = true;
        try {
            DigitalMineRecovery recovery = DigitalMineRecovery.get(world);
            for (int i = 0; i < inventory.getSlots(); i++) {
                ItemStack item = inventory.getStackInSlot(i);
                if (!item.isEmpty()) { recovery.enqueue(pos, item); inventory.setStackInSlot(i, ItemStack.EMPTY); }
            }
            for (ItemStack item : pendingDrops) recovery.enqueue(pos, item);
            pendingDrops.clear();
            removalDeferred = false;
        } finally { drainingRemoval = false; }
    }
    void queueDrop(ItemStack stack) { if (!stack.isEmpty()) pendingDrops.add(stack.copy()); }
    NonNullList<ItemStack> pendingDrops() { return pendingDrops; }
    static NonNullList<ItemStack> harvestDrops(NonNullList<ItemStack> drops, net.minecraft.world.World world,
            BlockPos target, IBlockState state, @Nullable EntityPlayer player, Random random) {
        float chance = net.minecraftforge.event.ForgeEventFactory.fireBlockHarvesting(drops, world, target, state, 0, 1.0F, false, player);
        return selectDrops(drops, chance, random);
    }
    static NonNullList<ItemStack> selectDrops(Iterable<ItemStack> drops, float chance, Random random) {
        NonNullList<ItemStack> selected = NonNullList.create();
        for (ItemStack drop : drops)
            if (drop != null && !drop.isEmpty() && random.nextFloat() <= chance) selected.add(drop.copy());
        return selected;
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt); nbt.setInteger("Energy", energy); nbt.setInteger("Mode", mode); nbt.setInteger("Cursor", cursor);
        nbt.setInteger("Delay", delay); nbt.setBoolean("Enabled", enabled); nbt.setTag("Inventory", inventory.serializeNBT());
        NBTTagList pending = new NBTTagList();
        for (ItemStack stack : pendingDrops) pending.appendTag(stack.writeToNBT(new NBTTagCompound()));
        nbt.setTag("PendingDrops", pending);
        if (owner != null) { nbt.setUniqueId("Owner", owner); nbt.setString("OwnerName", ownerName); } return nbt;
    }
    @Override public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt); energy = Math.max(0, Math.min(capacity, nbt.getInteger("Energy"))); mode = nbt.getInteger("Mode") == 1 ? 1 : 0;
        cursor = Math.max(0, nbt.getInteger("Cursor")); delay = Math.max(0, Math.min(baseDelay, nbt.getInteger("Delay")));
        enabled = !nbt.hasKey("Enabled") || nbt.getBoolean("Enabled");
        owner = nbt.hasUniqueId("Owner") ? nbt.getUniqueId("Owner") : null; ownerName = nbt.getString("OwnerName");
        if (nbt.hasKey("Inventory", 10)) inventory.deserializeNBT(nbt.getCompoundTag("Inventory"));
        pendingDrops.clear();
        NBTTagList pending = nbt.getTagList("PendingDrops", 10);
        for (int i = 0; i < pending.tagCount(); i++) { ItemStack stack = new ItemStack(pending.getCompoundTagAt(i)); if (!stack.isEmpty()) pendingDrops.add(stack); }
    }
}
