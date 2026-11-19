package com.mikael8367.nexustech;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IContainerListener;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.SlotItemHandler;

public final class MachineContainer extends Container {
    public final MachineTile tile;
    private final int machineSlots;
    private final int[] last = { Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE };
    public MachineContainer(InventoryPlayer player, MachineTile tile) {
        this.tile = tile;
        if (tile.kind == MachineTile.Kind.GENERATOR) addSlotToContainer(new MachineSlot(tile, 0, 80, 35));
        if (tile.kind == MachineTile.Kind.FURNACE) {
            addSlotToContainer(new MachineSlot(tile, 0, 56, 17));
            addSlotToContainer(new MachineSlot(tile, 1, 116, 35) {
                @Override public boolean isItemValid(ItemStack stack) { return false; }
            });
        }
        machineSlots = tile.inventory.getSlots();
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) addSlotToContainer(new Slot(player, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
        for (int col = 0; col < 9; col++) addSlotToContainer(new Slot(player, col, 8 + col * 18, 142));
    }
    // Container.mergeItemStack mutates existing stacks directly, bypassing the
    // handler callback. Persist these changes even when the machine is idle.
    private static class MachineSlot extends SlotItemHandler {
        private final MachineTile machine;
        MachineSlot(MachineTile machine, int index, int x, int y) {
            super(machine.inventory, index, x, y);
            this.machine = machine;
        }
        @Override public void onSlotChanged() {
            super.onSlotChanged();
            if (machine.getWorld() != null && !machine.getWorld().isRemote) machine.markDirty();
        }
    }
    @Override public boolean canInteractWith(EntityPlayer player) {
        return tile.getWorld() != null && !tile.isInvalid() && tile.getWorld().isBlockLoaded(tile.getPos())
                && tile.getWorld().getTileEntity(tile.getPos()) == tile
                && player.getDistanceSq(tile.getPos().getX() + .5, tile.getPos().getY() + .5, tile.getPos().getZ() + .5) <= 64.0;
    }
    private void sendField(IContainerListener listener, int field, int value) {
        listener.sendWindowProperty(this, field * 2, value & 0xffff);
        listener.sendWindowProperty(this, field * 2 + 1, (value >>> 16) & 0xffff);
    }
    @Override public void addListener(IContainerListener listener) {
        super.addListener(listener);
        for (int i = 0; i < 4; i++) sendField(listener, i, tile.field(i));
    }
    @Override public void detectAndSendChanges() {
        super.detectAndSendChanges();
        for (int i = 0; i < 4; i++) {
            int value = tile.field(i);
            if (last[i] != value) { for (IContainerListener listener : listeners) sendField(listener, i, value); last[i] = value; }
        }
    }
    @Override public void updateProgressBar(int id, int value) {
        if (id < 0 || id >= 8) return;
        int index = id / 2, old = tile.field(index);
        tile.setField(index, (id & 1) == 0 ? (old & 0xffff0000) | (value & 0xffff) : (old & 0xffff) | ((value & 0xffff) << 16));
    }
    @Override public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        if (index < 0 || index >= inventorySlots.size()) return ItemStack.EMPTY;
        Slot slot = inventorySlots.get(index);
        if (!slot.getHasStack()) return ItemStack.EMPTY;
        ItemStack stack = slot.getStack(), original = stack.copy();
        if (index < machineSlots) {
            if (!mergeItemStack(stack, machineSlots, inventorySlots.size(), true)) return ItemStack.EMPTY;
        } else {
            boolean moved = machineSlots > 0 && tile.inventory.isItemValid(0, stack)
                    && mergeItemStack(stack, 0, 1, false);
            if (!moved) {
                if (index < machineSlots + 27) {
                    if (!mergeItemStack(stack, machineSlots + 27, machineSlots + 36, false)) return ItemStack.EMPTY;
                } else if (!mergeItemStack(stack, machineSlots, machineSlots + 27, false)) return ItemStack.EMPTY;
            }
        }
        if (stack.isEmpty()) slot.putStack(ItemStack.EMPTY); else slot.onSlotChanged();
        if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return original;
    }
}
