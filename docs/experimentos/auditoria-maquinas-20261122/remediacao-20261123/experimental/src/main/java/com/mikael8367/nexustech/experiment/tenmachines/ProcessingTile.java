package com.mikael8367.nexustech.experiment.tenmachines;

import javax.annotation.Nullable;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

/** Server owned, two dedicated inputs and one output. Push FE only; never calls neighbours. */
public abstract class ProcessingTile extends TileEntity implements ITickable {
 public static final int CAPACITY=20000, RECEIVE=1000, PER_TICK=20, TICKS=100;
 private final MachineKind kind;
 private final ItemStack[] slots={ItemStack.EMPTY,ItemStack.EMPTY,ItemStack.EMPTY};
 private int energy,progress;
 // Ambiguous saves are kept verbatim and cannot operate without offline recovery.
 private NBTTagCompound quarantine;
 private boolean busy,pendingDrop,dropped;
 private World dropWorld; private BlockPos dropPos;
 protected ProcessingTile(MachineKind kind) { this.kind=kind; }
 public final MachineKind kind(){ return kind; }
 public final int progress(){ return progress; }
 private boolean live(){ return quarantine==null && !pendingDrop && !dropped && world!=null && !world.isRemote && !isInvalid() && world.isBlockLoaded(pos) && world.getTileEntity(pos)==this; }
 private void finish(boolean dirty){
  try { if(dirty && !pendingDrop && !dropped) markDirty(); }
  finally { busy=false; if(pendingDrop) flushDrops(); }
 }
 private final IItemHandler itemAccess=new IItemHandler(){
  public int getSlots(){return 3;}
  public int getSlotLimit(int slot){return slot>=0 && slot<3?64:0;}
  public ItemStack getStackInSlot(int slot){ return slot>=0 && slot<3 && !dropped && quarantine==null ? slots[slot].copy() : ItemStack.EMPTY; }
  public boolean isItemValid(int slot,ItemStack s){return slot>=0 && slot<2 && kind.accepts(slot,s);}
  public ItemStack insertItem(int slot,ItemStack stack,boolean simulate){
   if(stack==null || stack.isEmpty()) return ItemStack.EMPTY;
   if(busy || !live() || slot<0 || slot>1 || !kind.accepts(slot,stack)) return stack;
   busy=true; boolean changed=false;
   try {
    ItemStack old=slots[slot];
    if(!old.isEmpty() && (!ItemStack.areItemsEqual(old,stack) || !ItemStack.areItemStackTagsEqual(old,stack))) return stack;
    int limit=Math.min(64,stack.getMaxStackSize()); int n=Math.min(stack.getCount(),Math.max(0,limit-(old.isEmpty()?0:old.getCount())));
    if(n<=0) return stack;
    ItemStack rem=stack.copy(); rem.shrink(n);
    if(!simulate){ItemStack replacement=old.isEmpty()?stack.copy():old.copy(); replacement.setCount((old.isEmpty()?0:old.getCount())+n); slots[slot]=replacement; if(old.isEmpty())progress=0; changed=true;}
    return rem;
   } finally {finish(changed);}
  }
  public ItemStack extractItem(int slot,int amount,boolean simulate){
   if(busy || !live() || slot<0 || slot>2 || amount<=0 || slots[slot].isEmpty()) return ItemStack.EMPTY;
   busy=true; boolean changed=false;
   try {
    ItemStack old=slots[slot], result=old.copy(); int n=Math.min(Math.min(amount,old.getCount()),old.getMaxStackSize()); result.setCount(n);
    if(!simulate){ItemStack remaining=old.copy(); remaining.shrink(n); slots[slot]=remaining.isEmpty()?ItemStack.EMPTY:remaining; if(slot<2)progress=0; changed=true;}
    return result;
   } finally {finish(changed);}
  }
 };
 // All faces and null return the same public handler. Consumers must avoid
 // transferring between aliases and must filter input extraction themselves.
 private final IEnergyStorage energyAccess=new IEnergyStorage(){
  public int receiveEnergy(int amount,boolean simulate){
   if(busy || !live() || amount<=0)return 0;
   busy=true; boolean changed=false;
   try {int n=Math.min(Math.min(amount,RECEIVE),CAPACITY-energy); if(!simulate && n>0){energy+=n;changed=true;} return n;}
   finally{finish(changed);}
  }
  public int extractEnergy(int amount,boolean simulate){return 0;}
  public int getEnergyStored(){return energy;}
  public int getMaxEnergyStored(){return CAPACITY;}
  public boolean canExtract(){return false;}
  public boolean canReceive(){return true;}
 };
 private boolean fits(ItemStack product){
  if(product.isEmpty())return false;
  ItemStack out=slots[2]; int limit=Math.min(64,product.getMaxStackSize());
  return out.isEmpty()?product.getCount()<=limit:ItemStack.areItemsEqual(out,product) && ItemStack.areItemStackTagsEqual(out,product) && out.getCount()<=limit-product.getCount();
 }
 @Override public void update(){
  if(busy || !live())return;
  busy=true; boolean changed=false;
  try {
   ItemStack product=kind.result(slots[0],slots[1]);
   if(product.isEmpty()){ if(progress!=0){progress=0;changed=true;} return; }
   if(!fits(product))return; // Congestion pauses, rather than discarding paid work.
   if(energy<PER_TICK)return;
   if(progress+1<TICKS){energy-=PER_TICK;progress++;changed=true;return;}
   // Construct all replacements first; there are no world calls between assignments.
   ItemStack a=slots[0].copy(); a.shrink(kind.required(0));
   ItemStack b=slots[1].copy(); if(kind.required(1)>0)b.shrink(kind.required(1));
   ItemStack out=slots[2].isEmpty()?product.copy():slots[2].copy();
   if(!slots[2].isEmpty())out.grow(product.getCount());
   slots[0]=a.isEmpty()?ItemStack.EMPTY:a; slots[1]=b.isEmpty()?ItemStack.EMPTY:b; slots[2]=out;
   energy-=PER_TICK; progress=0; changed=true;
  } finally { finish(changed); }
 }
 void requestDrops(World w,BlockPos p){
  if(dropped || pendingDrop || w==null || w.isRemote)return;
  pendingDrop=true; dropWorld=w; dropPos=p.toImmutable();
  if(!busy)flushDrops();
 }
 private void flushDrops(){
  if(!pendingDrop || dropped)return;
  World w=dropWorld; BlockPos p=dropPos;
  ItemStack[] saved={slots[0],slots[1],slots[2]};
  slots[0]=slots[1]=slots[2]=ItemStack.EMPTY; energy=0;progress=0;
  pendingDrop=false; dropped=true;dropWorld=null;dropPos=null;
  for(ItemStack s:saved)if(!s.isEmpty())w.spawnEntity(new EntityItem(w,p.getX()+0.5,p.getY()+0.5,p.getZ()+0.5,s));
 }
 @Override public boolean hasCapability(Capability<?> cap,@Nullable EnumFacing side){return cap==CapabilityEnergy.ENERGY || cap==CapabilityItemHandler.ITEM_HANDLER_CAPABILITY || super.hasCapability(cap,side);}
 @Override public <T>T getCapability(Capability<T> cap,@Nullable EnumFacing side){
  if(cap==CapabilityEnergy.ENERGY)return CapabilityEnergy.ENERGY.cast(energyAccess);
  if(cap==CapabilityItemHandler.ITEM_HANDLER_CAPABILITY)return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(itemAccess);
  return super.getCapability(cap,side);
 }
 @Override public NBTTagCompound writeToNBT(NBTTagCompound tag){
  if(quarantine!=null){tag.merge(quarantine.copy());super.writeToNBT(tag);return tag;}
  super.writeToNBT(tag); tag.setInteger("Energy",energy);tag.setInteger("Progress",progress);
  // Bind paid progress to machine and ingredients; this is validation, not authentication.
  tag.setString("ProgressKind",kind.name());
  for(int i=0;i<2;i++) if(kind.required(i)>0 && !slots[i].isEmpty()){
   ItemStack identity=slots[i].copy();identity.setCount(1);NBTTagCompound n=new NBTTagCompound();identity.writeToNBT(n);tag.setTag("ProgressInput"+i,n);
  }
  NBTTagList list=new NBTTagList(); for(int i=0;i<3;i++)if(!slots[i].isEmpty()){NBTTagCompound n=new NBTTagCompound();slots[i].writeToNBT(n);n.setByte("Slot",(byte)i);list.appendTag(n);} tag.setTag("Inventory",list);return tag;
 }
 private boolean progressMatches(NBTTagCompound tag){
  if(!kind.name().equals(tag.getString("ProgressKind")) || kind.result(slots[0],slots[1]).isEmpty())return false;
  for(int i=0;i<2;i++) if(kind.required(i)>0){
   if(!tag.hasKey("ProgressInput"+i,10))return false;
   ItemStack identity=new ItemStack(tag.getCompoundTag("ProgressInput"+i));
   if(identity.isEmpty() || !ItemStack.areItemsEqual(identity,slots[i]) || !ItemStack.areItemStackTagsEqual(identity,slots[i]))return false;
  }
  return true;
 }
 @Override public void readFromNBT(NBTTagCompound tag){
  if(busy || dropped || pendingDrop)return;
  busy=true;
  try {
   super.readFromNBT(tag); quarantine=null; energy=Math.max(0,Math.min(CAPACITY,tag.getInteger("Energy")));
   progress=Math.max(0,Math.min(TICKS-1,tag.getInteger("Progress")));
   slots[0]=slots[1]=slots[2]=ItemStack.EMPTY;
   NBTTagList list=tag.getTagList("Inventory",10);
   boolean[] seen=new boolean[3]; boolean ambiguous=tag.hasKey("Inventory") && !tag.hasKey("Inventory",9);
   if(tag.hasKey("Inventory",9)){NBTTagList raw=(NBTTagList)tag.getTag("Inventory"); if(raw.tagCount()>0 && raw.getTagType()!=10)ambiguous=true;}
   if(tag.getInteger("Progress")<0 || tag.getInteger("Progress")>=TICKS || tag.getInteger("Energy")<0 || tag.getInteger("Energy")>CAPACITY)ambiguous=true;
   for(int i=0;i<list.tagCount();i++) {
    NBTTagCompound n=list.getCompoundTagAt(i);int slot=n.getByte("Slot") & 255;
    if(!n.hasKey("Slot",1) || slot>=3 || seen[slot]){ambiguous=true;continue;}
    seen[slot]=true;ItemStack s=new ItemStack(n);if(s.isEmpty())ambiguous=true;else slots[slot]=s;
   }
   if(progress>0 && !progressMatches(tag))ambiguous=true;
   if(ambiguous){quarantine=tag.copy();progress=0;}
  } finally {busy=false;if(pendingDrop)flushDrops();}
 }
 /** Quarantine is not proof of save authenticity and has no user interface. */
 public final boolean isQuarantined(){return quarantine!=null;}
 /** Call only after an operator verifies the provenance of a backed-up legacy save.
  * There is deliberately no automatic caller. Never accepts partial/new identities. */
 public final boolean acceptTrustedLegacy(){
  if(quarantine==null || busy || dropped || pendingDrop || world==null || world.isRemote || isInvalid() || !world.isBlockLoaded(pos) || world.getTileEntity(pos)!=this)return false;
  NBTTagCompound q=quarantine;
  if(q.hasKey("ProgressKind") || q.hasKey("ProgressInput0") || q.hasKey("ProgressInput1"))return false;
  int p=q.getInteger("Progress"), e=q.getInteger("Energy");
  if(p<=0 || p>=TICKS || e<0 || e>CAPACITY || kind.result(slots[0],slots[1]).isEmpty())return false;
  if(!q.hasKey("Inventory",9))return false;
  NBTTagList list=(NBTTagList)q.getTag("Inventory"); if(list.getTagType()!=10)return false;
  boolean[] seen=new boolean[3];
  for(int i=0;i<list.tagCount();i++){
   NBTTagCompound n=list.getCompoundTagAt(i); int s=n.getByte("Slot") & 255;
   if(!n.hasKey("Slot",1) || s>=3 || seen[s] || new ItemStack(n).isEmpty())return false; seen[s]=true;
  }
  busy=true; try{progress=p;energy=e;quarantine=null;markDirty();return true;}finally{busy=false;if(pendingDrop)flushDrops();}
 }
 public static final class StoneCutter extends ProcessingTile {public StoneCutter(){super(MachineKind.STONE_CUTTER);}}
 public static final class MossAssembler extends ProcessingTile {public MossAssembler(){super(MachineKind.MOSS_ASSEMBLER);}}
 public static final class GlassShaper extends ProcessingTile {public GlassShaper(){super(MachineKind.GLASS_SHAPER);}}
 public static final class FiberSpinner extends ProcessingTile {public FiberSpinner(){super(MachineKind.FIBER_SPINNER);}}
 public static final class PaperPress extends ProcessingTile {public PaperPress(){super(MachineKind.PAPER_PRESS);}}
 public static final class SugarMill extends ProcessingTile {public SugarMill(){super(MachineKind.SUGAR_MILL);}}
 public static final class BreadBaker extends ProcessingTile {public BreadBaker(){super(MachineKind.BREAD_BAKER);}}
 public static final class SeedExtractor extends ProcessingTile {public SeedExtractor(){super(MachineKind.SEED_EXTRACTOR);}}
 public static final class DyeMixer extends ProcessingTile {public DyeMixer(){super(MachineKind.DYE_MIXER);}}
 public static final class BoneMill extends ProcessingTile {public BoneMill(){super(MachineKind.BONE_MILL);}}
}
