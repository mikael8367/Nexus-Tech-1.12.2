package com.mikael8367.nexustech.digitalmine;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IContainerListener;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.SlotItemHandler;

public final class DigitalMineContainer extends Container {
    public final DigitalMineTile tile;
    private final int[] last = {Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
    public DigitalMineContainer(InventoryPlayer player, DigitalMineTile tile) {
        this.tile = tile;
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) {
            addSlotToContainer(new SlotItemHandler(tile.inventory, row * 9 + col, 8 + col * 18, 32 + row * 18) {
                @Override public boolean isItemValid(ItemStack stack) { return false; }
                @Override public void onSlotChanged() { super.onSlotChanged(); if (tile.getWorld() != null && !tile.getWorld().isRemote) tile.markDirty(); }
            });
        }
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) addSlotToContainer(new Slot(player, 9 + col + row * 9, 8 + col * 18, 140 + row * 18));
        for (int col = 0; col < 9; col++) addSlotToContainer(new Slot(player, col, 8 + col * 18, 198));
    }
    @Override public boolean canInteractWith(EntityPlayer p) {
        return tile.getWorld() != null && (tile.getWorld().isRemote || tile.authorized(p)) && !tile.isInvalid() && tile.getWorld().isBlockLoaded(tile.getPos())
            && tile.getWorld().getTileEntity(tile.getPos()) == tile && p.getDistanceSq(tile.getPos().getX()+.5, tile.getPos().getY()+.5, tile.getPos().getZ()+.5) <= 64;
    }
    @Override public boolean enchantItem(EntityPlayer p, int id) {
        if (p.world.isRemote || !canInteractWith(p)) return false;
        if (id == 0) tile.enabled = !tile.enabled;
        else if (id == 1) tile.mode = tile.mode == 0 ? 1 : 0;
        else return false;
        tile.markDirty(); detectAndSendChanges(); return true;
    }
    private int field(int i) { return i == 0 ? tile.energy : i == 1 ? tile.enabled ? 1 : 0 : i == 2 ? tile.mode : tile.status; }
    private void send(IContainerListener l, int i, int v) { l.sendWindowProperty(this, i * 2, v & 65535); l.sendWindowProperty(this, i * 2 + 1, (v >>> 16) & 65535); }
    @Override public void addListener(IContainerListener l) { super.addListener(l); for (int i=0;i<4;i++) send(l,i,field(i)); }
    @Override public void detectAndSendChanges() { super.detectAndSendChanges(); for (int i=0;i<4;i++) { int v=field(i); if (last[i]!=v) { for (IContainerListener l : listeners) send(l,i,v); last[i]=v; } } }
    @Override public void updateProgressBar(int id, int value) {
        if (id < 0 || id >= 8) return;
        int i=id/2, old=field(i), v=(id%2==0) ? (old & 0xffff0000) | (value & 65535) : (old & 65535) | ((value & 65535) << 16);
        if (i==0) tile.energy=v; else if (i==1) tile.enabled=v!=0; else if (i==2) tile.mode=v; else tile.status=v;
    }
    @Override public ItemStack transferStackInSlot(EntityPlayer p, int index) {
        if (index < 0 || index >= inventorySlots.size()) return ItemStack.EMPTY;
        Slot slot=inventorySlots.get(index); if (!slot.getHasStack()) return ItemStack.EMPTY;
        ItemStack stack=slot.getStack(), original=stack.copy();
        if (index < 27) { if (!mergeItemStack(stack, 27, 63, true)) return ItemStack.EMPTY; }
        else if (index < 54) { if (!mergeItemStack(stack, 54, 63, false)) return ItemStack.EMPTY; }
        else if (!mergeItemStack(stack, 27, 54, false)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.putStack(ItemStack.EMPTY); else slot.onSlotChanged();
        if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
        slot.onTake(p, stack); return original;
    }
}
