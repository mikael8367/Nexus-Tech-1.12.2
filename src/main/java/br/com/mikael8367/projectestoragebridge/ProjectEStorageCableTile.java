package br.com.mikael8367.projectestoragebridge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import mrriegel.storagenetwork.block.master.TileMaster;
import mrriegel.storagenetwork.api.capability.IConnectableLink;
import mrriegel.storagenetwork.api.data.DimPos;
import mrriegel.storagenetwork.api.data.EnumStorageDirection;
import mrriegel.storagenetwork.api.data.IItemStackMatcher;
import mrriegel.storagenetwork.block.cable.TileCable;
import mrriegel.storagenetwork.capabilities.StorageNetworkCapabilities;
import moze_intel.projecte.api.ProjectEAPI;
import moze_intel.projecte.api.capabilities.IKnowledgeProvider;
import moze_intel.projecte.api.proxy.IEMCProxy;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.block.state.IBlockState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fml.common.FMLCommonHandler;

public final class ProjectEStorageCableTile extends TileCable implements IConnectableLink {

    public IBlockState getFacadeState() {
        return null;
    }

    public void setFacadeState(IBlockState facadeState) {
        // Fixed Bridge texture: do not accept SSN facade skins.
    }

    public void setFacadeState(String block, int meta) {
        // Ignore old facade data and facade packets.
    }


    @Override
    public boolean hasCapability(Capability<?> capability, EnumFacing facing) {
        return capability == StorageNetworkCapabilities.CONNECTABLE_ITEM_STORAGE_CAPABILITY
                || super.hasCapability(capability, facing);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getCapability(Capability<T> capability, EnumFacing facing) {
        if (capability == StorageNetworkCapabilities.CONNECTABLE_ITEM_STORAGE_CAPABILITY) {
            return (T) this;
        }
        return super.getCapability(capability, facing);
    }

    @Override
    public List<ItemStack> getStoredStacks() {
        if (!isBridgeActive()) {
            return Collections.emptyList();
        }
        EntityPlayerMP player = getUnambiguousPlayer();
        // SSN can query this cable from a different dimension than the sole online
        // player. Do not expose a remote player's EMC as if it belonged to this network.
        if (player == null || player.world != world) {
            return Collections.emptyList();
        }
        IKnowledgeProvider knowledge = getKnowledge(player);
        IEMCProxy emc = ProjectEAPI.getEMCProxy();
        if (knowledge == null || emc == null) {
            return Collections.emptyList();
        }

        long balance = knowledge.getEmc();
        if (balance <= 0L) {
            return Collections.emptyList();
        }

        List<ItemStack> visible = new ArrayList<ItemStack>();
        int maxTypes = Math.max(1, BridgeConfig.maxListedItemTypes);
        for (ItemStack learned : knowledge.getKnowledge()) {
            if (visible.size() >= maxTypes) {
                break;
            }
            if (learned == null || learned.isEmpty()) {
                continue;
            }
            ItemStack one = learned.copy();
            one.setCount(1);
            long value = emc.getValue(one);
            if (value <= 0L) {
                continue;
            }
            int available = EmcWithdrawalCalculator.maxAffordable(
                    balance, value, one.getMaxStackSize(), one.getMaxStackSize());
            if (available > 0) {
                one.setCount(available);
                visible.add(one);
            }
        }
        return visible;
    }

    @Override
    public synchronized ItemStack insertStack(ItemStack stack, boolean simulate) {
        if (stack == null || stack.isEmpty() || !isBridgeActive()) {
            return stack == null ? ItemStack.EMPTY : stack;
        }

        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null || !server.isCallingFromMinecraftThread()) {
            return stack;
        }

        EntityPlayerMP player = getUnambiguousPlayer();
        IKnowledgeProvider knowledge = getKnowledge(player);
        IEMCProxy emc = ProjectEAPI.getEMCProxy();
        if (player == null || player.world != world || knowledge == null || emc == null) {
            return stack;
        }

        ItemStack one = stack.copy();
        one.setCount(1);
        long emcPerItem = emc.getSellValue(one);
        if (emcPerItem <= 0L) {
            return stack;
        }

        long balance = knowledge.getEmc();
        if (balance < 0L) {
            return stack;
        }
        long room = Long.MAX_VALUE - balance;
        int accepted = (int) Math.min((long) stack.getCount(), room / emcPerItem);
        if (accepted <= 0) {
            return stack;
        }

        long credit = emcPerItem * (long) accepted;
        boolean needsKnowledge = !knowledge.hasFullKnowledge() && !knowledge.hasKnowledge(one);

        if (simulate) {
            return remainder(stack, accepted);
        }

        // Learn before crediting, but roll the knowledge back if the EMC update fails.
        boolean learnedNow = false;
        if (needsKnowledge) {
            learnedNow = knowledge.addKnowledge(one);
            if (!learnedNow && !knowledge.hasKnowledge(one)) {
                return stack;
            }
        }

        // Recheck the balance immediately before committing the deposit.
        long currentBalance = knowledge.getEmc();
        if (currentBalance != balance || currentBalance > Long.MAX_VALUE - credit) {
            if (learnedNow) {
                knowledge.removeKnowledge(one);
                knowledge.sync(player);
            }
            return stack;
        }

        knowledge.setEmc(currentBalance + credit);
        if (knowledge.getEmc() != currentBalance + credit) {
            knowledge.setEmc(currentBalance);
            if (learnedNow) {
                knowledge.removeKnowledge(one);
            }
            knowledge.sync(player);
            return stack;
        }

        knowledge.sync(player);
        markDirty();
        return remainder(stack, accepted);
    }

    private static ItemStack remainder(ItemStack original, int accepted) {
        int remaining = original.getCount() - accepted;
        if (remaining <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack result = original.copy();
        result.setCount(remaining);
        return result;
    }

    @Override
    public synchronized ItemStack extractStack(IItemStackMatcher matcher, int size, boolean simulate) {
        if (!isBridgeActive() || matcher == null || size <= 0) {
            return ItemStack.EMPTY;
        }
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        // EMC changes and item delivery must run in the same serialized server tick.
        if (server == null || !server.isCallingFromMinecraftThread()) {
            return ItemStack.EMPTY;
        }
        EntityPlayerMP player = getUnambiguousPlayer();
        IKnowledgeProvider knowledge = getKnowledge(player);
        IEMCProxy emc = ProjectEAPI.getEMCProxy();
        if (player == null || player.world != world || knowledge == null || emc == null) {
            return ItemStack.EMPTY;
        }

        for (ItemStack learned : knowledge.getKnowledge()) {
            if (learned == null || learned.isEmpty()) {
                continue;
            }
            ItemStack one = learned.copy();
            one.setCount(1);
            if (!matcher.match(one) || !knowledge.hasKnowledge(one)) {
                continue;
            }
            long emcPerItem = emc.getValue(one);
            long balance = knowledge.getEmc();
            int count = EmcWithdrawalCalculator.maxAffordable(
                    balance, emcPerItem, size, one.getMaxStackSize());
            if (count <= 0) {
                continue;
            }
            long cost = EmcWithdrawalCalculator.costFor(emcPerItem, count);
            if (cost <= 0L || cost > balance) {
                continue;
            }

            // Re-read immediately before committing. The server-thread guard plus this
            // synchronized method prevents concurrent bridge callbacks from spending
            // the same EMC snapshot more than once.
            long currentBalance = knowledge.getEmc();
            if (currentBalance < cost || currentBalance != balance) {
                continue;
            }

            ItemStack result = one.copy();
            result.setCount(count);
            if (simulate) {
                return result;
            }

            // Commit the EMC debit before returning the stack to SSN. Never return
            // deliverable items if the balance could not be debited.
            knowledge.setEmc(currentBalance - cost);
            if (knowledge.getEmc() != currentBalance - cost) {
                knowledge.setEmc(currentBalance);
                knowledge.sync(player);
                return ItemStack.EMPTY;
            }
            knowledge.sync(player);
            return result;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public int getPriority() {
        return 0;
    }

    @Override
    public EnumStorageDirection getSupportedTransferDirection() {
        return EnumStorageDirection.BOTH;
    }

    @Override
    public int getEmptySlots() {
        return 0;
    }

    // Optional SSN API method in newer 1.12 builds; no @Override so 1.8.3 compiles.
    public DimPos getTargetPos() {
        return null;
    }

    /**
     * The ProjectE 1.12.2 table is not a storage tile or capability provider.
     * The bridge therefore requires physical adjacency to the real table, then
     * uses the single online player's ProjectE knowledge/EMC capability.
     */
    private boolean isBridgeActive() {
        return BridgeConfig.enableVirtualStorage
                && world != null
                && !world.isRemote
                && isPrimaryBridgeForNetwork()
                && isAdjacentToTransmutationTable();
    }

    boolean isAdjacentToTransmutationTable() {
        for (EnumFacing facing : EnumFacing.values()) {
            BlockPos adjacent = pos.offset(facing);
            if (!world.isBlockLoaded(adjacent)) {
                continue;
            }
            if (ProjectEStorageCableBlock.isTransmutationTable(world.getBlockState(adjacent))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Every bridge cable sees the same player's ProjectE knowledge and EMC pool.
     * Expose that virtual inventory from exactly one cable per SSN network, otherwise
     * TileMaster.getStacks() sums the same virtual stock once for every bridge cable.
     */
    private boolean isPrimaryBridgeForNetwork() {
        DimPos masterPos = getMaster();
        if (masterPos == null || !masterPos.isLoaded()) {
            return false;
        }

        TileMaster master = masterPos.getTileEntity(TileMaster.class);
        if (master == null || master.getWorld() != world) {
            return false;
        }

        int primaryDimension = Integer.MAX_VALUE;
        BlockPos primaryPos = null;
        for (DimPos connectedPos : master.getConnectablePositions()) {
            if (connectedPos == null || !connectedPos.isLoaded()) {
                continue;
            }
            TileEntity connectedTile = connectedPos.getTileEntity(TileEntity.class);
            if (!(connectedTile instanceof ProjectEStorageCableTile)) {
                continue;
            }
            // Elect only among bridge cables physically linked to a ProjectE table.
            // Otherwise a lower-position cable elsewhere on the SSN network could
            // become primary and suppress the one cable that actually touches the table.
            ProjectEStorageCableTile bridgeTile = (ProjectEStorageCableTile) connectedTile;
            if (!bridgeTile.isAdjacentToTransmutationTable()) {
                continue;
            }

            int candidateDimension = connectedPos.getWorld().provider.getDimension();
            BlockPos candidatePos = connectedPos.getBlockPos();
            if (primaryPos == null || candidateDimension < primaryDimension
                    || (candidateDimension == primaryDimension && candidatePos.compareTo(primaryPos) < 0)) {
                primaryDimension = candidateDimension;
                primaryPos = candidatePos;
            }
        }

        return primaryPos != null
                && world.provider.getDimension() == primaryDimension
                && primaryPos.equals(pos);
    }

    private IKnowledgeProvider getKnowledge(EntityPlayerMP player) {
        if (player == null || !player.isEntityAlive() || ProjectEAPI.KNOWLEDGE_CAPABILITY == null) {
            return null;
        }
        return player.getCapability(ProjectEAPI.KNOWLEDGE_CAPABILITY, null);
    }

    private EntityPlayerMP getUnambiguousPlayer() {
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) {
            return null;
        }
        List<EntityPlayerMP> players = server.getPlayerList().getPlayers();
        return players.size() == 1 ? players.get(0) : null;
    }
}
