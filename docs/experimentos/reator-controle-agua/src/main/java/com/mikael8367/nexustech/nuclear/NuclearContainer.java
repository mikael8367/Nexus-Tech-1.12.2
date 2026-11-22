package com.mikael8367.nexustech.nuclear;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.IContainerListener;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.items.SlotItemHandler;

public final class NuclearContainer extends Container {
 public final NuclearController tile; private final int[] last={-1,-1,-1,-1,-1,-1};
 private final BlockPos clicked; private final TileEntity clickedTile;
 // Forge's stock SlotItemHandler probes by temporarily clearing the slot;
 // a reentrant block removal at that point can otherwise miss the stack.
 private static final class SafeSlot extends SlotItemHandler {
   private final NuclearController owner; private final int index;
   SafeSlot(NuclearController owner,int index,int x,int y){super(owner.inventory,index,x,y);this.owner=owner;this.index=index;}
   @Override public boolean isItemValid(ItemStack s){return !s.isEmpty() && owner.inventory.isItemValid(index,s);}
   @Override public int getItemStackLimit(ItemStack s){return Math.min(owner.inventory.getSlotLimit(index),s.getMaxStackSize());}
 }
 public NuclearContainer(InventoryPlayer player,NuclearController tile){this(player,tile,tile.getPos(),tile);}
 NuclearContainer(InventoryPlayer player,NuclearController tile,BlockPos clicked,TileEntity clickedTile){
   this.tile=tile;this.clicked=clicked.toImmutable();this.clickedTile=clickedTile;
   addSlotToContainer(new SafeSlot(tile,0,8,NuclearGuiLayout.MACHINE_SLOT_Y));
   addSlotToContainer(new SafeSlot(tile,1,30,NuclearGuiLayout.MACHINE_SLOT_Y));
   addSlotToContainer(new SafeSlot(tile,2,52,NuclearGuiLayout.MACHINE_SLOT_Y));
   for(int r=0;r<3;r++)for(int c=0;c<9;c++)addSlotToContainer(new Slot(player,c+r*9+9,8+c*18,NuclearGuiLayout.PLAYER_SLOT_Y+r*18));
   for(int c=0;c<9;c++)addSlotToContainer(new Slot(player,c,8+c*18,NuclearGuiLayout.HOTBAR_Y));
 }
 @Override public boolean canInteractWith(EntityPlayer p){
   if(tile.getWorld()==null || tile.isInvalid() || !NuclearGuiHooks.accessible(p,tile.getWorld(),clicked)
       || !tile.getWorld().isBlockLoaded(tile.getPos()) || tile.getWorld().getTileEntity(tile.getPos())!=tile
       || clickedTile.isInvalid() || tile.getWorld().getTileEntity(clicked)!=clickedTile)return false;
   if(tile.getWorld().isRemote)return true; // client display only; authoritative checks run on server
   if(tile.validateStructure()!=1)return false;
   return clickedTile==tile ? clicked.equals(tile.getPos())
       : clickedTile instanceof NuclearPart && tile.ownsLivePart(clicked,(NuclearPart)clickedTile);
 }
 /** Vanilla enchant-button packet is bound to the open server container/window. */
 @Override public boolean enchantItem(EntityPlayer p,int id){if(!canInteractWith(p)||tile.getWorld().isRemote || (id!=0 && id!=1 && id!=2))return false;if(id==2)tile.setCoolantGuard(!tile.coolantGuard());else tile.setEnabled(id==1);return true;}
 private void send(IContainerListener l,int i,int v){l.sendWindowProperty(this,i*2,v&65535);l.sendWindowProperty(this,i*2+1,(v>>>16)&65535);}
 @Override public void addListener(IContainerListener l){super.addListener(l);for(int i=0;i<6;i++)send(l,i,tile.field(i));}
 @Override public void detectAndSendChanges(){super.detectAndSendChanges();for(int i=0;i<6;i++){int v=tile.field(i);if(v!=last[i]){for(IContainerListener l:listeners)send(l,i,v);last[i]=v;}}}
 @Override public void updateProgressBar(int id,int value){if(id<0||id>=12)return;int i=id/2,old=tile.displayField(i);tile.setClientField(i,(id&1)==0?(old&0xffff0000)|(value&65535):(old&65535)|((value&65535)<<16));}
 @Override public ItemStack slotClick(int slot,int button,ClickType type,EntityPlayer p){
   if(tile.getWorld()!=null && tile.getWorld().isRemote)return super.slotClick(slot,button,type,p);
   if(!canInteractWith(p) || !tile.beginOperation())return ItemStack.EMPTY;
   try{return super.slotClick(slot,button,type,p);}finally{tile.endOperation();}
 }
 @Override public ItemStack transferStackInSlot(EntityPlayer p,int index){
   if(tile.getWorld()!=null && tile.getWorld().isRemote)return transferImpl(p,index);
   if(!canInteractWith(p) || !tile.beginOperation())return ItemStack.EMPTY;
   try{return transferImpl(p,index);}finally{tile.endOperation();}
 }
 private ItemStack transferImpl(EntityPlayer p,int index){
   if(index<0||index>=inventorySlots.size())return ItemStack.EMPTY;Slot slot=inventorySlots.get(index);if(!slot.getHasStack())return ItemStack.EMPTY;
   ItemStack s=slot.getStack(),before=s.copy();
   if(index<3){if(!mergeItemStack(s,3,39,true))return ItemStack.EMPTY;}
   else {boolean moved=false;if(tile.inventory.isItemValid(0,s))moved=mergeItemStack(s,0,1,false);else if(tile.inventory.isItemValid(1,s))moved=mergeItemStack(s,1,2,false);
     if(!moved){if(index<30){if(!mergeItemStack(s,30,39,false))return ItemStack.EMPTY;}else if(!mergeItemStack(s,3,30,false))return ItemStack.EMPTY;}}
   if(s.isEmpty())slot.putStack(ItemStack.EMPTY);else slot.onSlotChanged();tile.markDirty();if(s.getCount()==before.getCount())return ItemStack.EMPTY;slot.onTake(p,s);return before;
 }
}
