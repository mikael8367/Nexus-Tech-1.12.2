package br.com.mikael8367.projectestoragebridge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fml.common.FMLCommonHandler;

public final class ProjectEStorageCableTile extends TileCable implements IConnectableLink {

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
        if (!BridgeConfig.enableVirtualStorage) {
            return Collections.emptyList();
        }
        EntityPlayerMP player = getUnambiguousPlayer();
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
    public ItemStack insertStack(ItemStack stack, boolean simulate) {
        return stack;
    }

    @Override
    public ItemStack extractStack(IItemStackMatcher matcher, int size, boolean simulate) {
        if (!BridgeConfig.enableVirtualStorage || world == null || world.isRemote
                || matcher == null || size <= 0) {
            return ItemStack.EMPTY;
        }
        EntityPlayerMP player = getUnambiguousPlayer();
        IKnowledgeProvider knowledge = getKnowledge(player);
        IEMCProxy emc = ProjectEAPI.getEMCProxy();
        if (player == null || knowledge == null || emc == null) {
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
            ItemStack result = one.copy();
            result.setCount(count);
            if (!simulate) {
                knowledge.setEmc(balance - cost);
                knowledge.sync(player);
            }
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
        return EnumStorageDirection.OUT;
    }

    @Override
    public int getEmptySlots() {
        return 0;
    }

    // Optional SSN API method in newer 1.12 builds; no @Override so 1.8.3 compiles.
    public DimPos getTargetPos() {
        return null;
    }

    private IKnowledgeProvider getKnowledge(EntityPlayerMP player) {
        if (player == null || ProjectEAPI.KNOWLEDGE_CAPABILITY == null) {
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
