package com.mikael8367.nexustech.nuclear;

import javax.annotation.Nullable;
import net.minecraft.block.BlockHorizontal;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.IItemHandler;

/** One owner, one store. No remote loading, no processing by passive parts. */
public final class NuclearController extends TileEntity implements ITickable {
 public static final int CAPACITY=1000000, FE_PER_TICK=200, FUEL_TICKS=2400, WATER_CAPACITY=8000, SCRAM=1000, RESET=400, OUTPUT=400;
 public int energy,temperature,fuelTicks,production; public boolean enabled, scrammed, formed;
 private boolean reserved; private int outputThisTick; private long outputTick=Long.MIN_VALUE;
 public final ItemStackHandler inventory=new ItemStackHandler(3) {
   @Override public boolean isItemValid(int slot,ItemStack s){ return slot==0?s.getItem()==NuclearRegistry.FUEL:slot==1?s.getItem()==Items.WATER_BUCKET:false; }
   @Override public ItemStack insertItem(int slot,ItemStack s,boolean simulate){
     if(!s.isEmpty() && !isItemValid(slot,s))return s;
     return super.insertItem(slot,s,simulate);
   }
   @Override protected void onContentsChanged(int slot){markDirty();}
 };
 // Automation can insert only inputs and extract only the output. Internal operations use inventory.
 private final IItemHandler automation=new IItemHandler(){
   public int getSlots(){return inventory.getSlots();}
   public ItemStack getStackInSlot(int slot){return inventory.getStackInSlot(slot).copy();}
   public ItemStack insertItem(int slot,ItemStack stack,boolean simulate){return inventory.insertItem(slot,stack,simulate);}
   public ItemStack extractItem(int slot,int amount,boolean simulate){
     if(slot!=2){inventory.getStackInSlot(slot);return ItemStack.EMPTY;}
     return inventory.extractItem(slot,amount,simulate);
   }
   public int getSlotLimit(int slot){return inventory.getSlotLimit(slot);}
 };
 private final FluidTank tank=new FluidTank(WATER_CAPACITY) {
   @Override public boolean canFillFluidType(FluidStack f){return f!=null && f.getFluid()==FluidRegistry.WATER;}
   @Override public boolean canDrain(){return false;}
   @Override public boolean canDrainFluidType(FluidStack f){return false;}
   @Override protected void onContentsChanged(){markDirty();}
 };
 private final IEnergyStorage storage=new IEnergyStorage(){
   public int receiveEnergy(int n,boolean sim){return 0;}
   public int extractEnergy(int n,boolean sim){
     if(!usable() || reserved || n<=0 || validateStructure()!=1)return 0;
     resetOutputForWorldTick();
     int out=Math.min(Math.min(n,energy),Math.max(0,OUTPUT-outputThisTick));
     if(!sim && out>0){bindStructure();energy-=out;outputThisTick+=out;markDirty();}return out;
   }
   public int getEnergyStored(){return energy;} public int getMaxEnergyStored(){return CAPACITY;}
   public boolean canExtract(){return true;} public boolean canReceive(){return false;}
 };
 private boolean usable(){return world!=null&&!world.isRemote&&!isInvalid()&&world.isBlockLoaded(pos)&&world.getTileEntity(pos)==this;}
 private static int clamp(int v,int max){return Math.max(0,Math.min(max,v));}
 public int water(){return tank.getFluidAmount();}
 /** Geometry uses controller as middle of front bottom row, front is its facing. */
 public static BlockPos at(BlockPos origin,EnumFacing front,int dx,int y,int depth){return origin.offset(front.rotateY(),dx).offset(front.getOpposite(),depth).up(y);}
 /** Synchronize the shared output budget to world time, not tile update order. */
 private void resetOutputForWorldTick(){
   long tick=world.getTotalWorldTime();
   if(outputTick!=tick){outputTick=tick;outputThisTick=0;}
 }
 /** Geometry only; deliberately ignores ownership so old owners can be checked without recursion. */
 private int validateGeometry(){
   if(!usable())return -1;
   IBlockState controller=world.getBlockState(pos);
   if(controller.getBlock()!=NuclearRegistry.CONTROLLER)return 0;
   EnumFacing front=controller.getValue(BlockHorizontal.FACING);
   for(int x=-1;x<=1;x++)for(int y=0;y<5;y++)for(int d=0;d<3;d++)
     if(!world.isBlockLoaded(at(pos,front,x,y,d)))return -1;
   for(int x=-1;x<=1;x++)for(int y=0;y<5;y++)for(int d=0;d<3;d++){
     BlockPos q=at(pos,front,x,y,d);
     if(x==0&&y==0&&d==0)continue;
     if(world.getBlockState(q).getBlock()!=(x==0&&y==2&&d==1?NuclearRegistry.CORE:NuclearRegistry.CASING)
         || !(world.getTileEntity(q) instanceof NuclearPart))return 0;
   }
   return 1;
 }
 private boolean containsPart(BlockPos part){
   IBlockState state=world.getBlockState(pos);
   if(state.getBlock()!=NuclearRegistry.CONTROLLER)return false;
   EnumFacing front=state.getValue(BlockHorizontal.FACING);
   for(int x=-1;x<=1;x++)for(int y=0;y<5;y++)for(int d=0;d<3;d++)
     if(!(x==0&&y==0&&d==0) && at(pos,front,x,y,d).equals(part))return true;
   return false;
 }
 /** Pure check: -1 unloaded, 0 invalid or owned, 1 claimable. No bind or dirtying. */
 public int validateStructure(){
   int geometry=validateGeometry(); if(geometry!=1)return geometry;
   EnumFacing front=world.getBlockState(pos).getValue(BlockHorizontal.FACING);
   for(int x=-1;x<=1;x++)for(int y=0;y<5;y++)for(int d=0;d<3;d++){
     if(x==0&&y==0&&d==0)continue;
     BlockPos q=at(pos,front,x,y,d);
     BlockPos owner=((NuclearPart)world.getTileEntity(q)).owner;
     if(owner!=null&&!owner.equals(pos)){
       if(!world.isBlockLoaded(owner))return -1;
       TileEntity old=world.getTileEntity(owner);
       if(old instanceof NuclearController){
         NuclearController c=(NuclearController)old;
         // Damaged but hot/armed owners keep their bindings until safe.
         if(c.enabled || c.temperature>RESET)return 0;
         int oldGeometry=c.validateGeometry();
         if(oldGeometry<0)return -1;
         if(oldGeometry==1 && c.containsPart(q))return 0;
       }
     }
   }
   return 1;
 }
 /** Called only after a successful pure validation, on an actual server operation. */
 private void bindStructure(){
   EnumFacing front=world.getBlockState(pos).getValue(BlockHorizontal.FACING);
   for(int x=-1;x<=1;x++)for(int y=0;y<5;y++)for(int d=0;d<3;d++)
     if(!(x==0&&y==0&&d==0))((NuclearPart)world.getTileEntity(at(pos,front,x,y,d))).bind(pos);
 }
 /** Release only loaded parts still owned by this controller; never touches inventories. */
 void releaseStructure(){
   if(world==null || world.isRemote || !world.isBlockLoaded(pos))return;
   if(enabled || temperature>RESET)return;
   IBlockState state=world.getBlockState(pos); if(state.getBlock()!=NuclearRegistry.CONTROLLER)return;
   EnumFacing front=state.getValue(BlockHorizontal.FACING);
   for(int x=-1;x<=1;x++)for(int y=0;y<5;y++)for(int d=0;d<3;d++){
     if(x==0&&y==0&&d==0)continue;
     BlockPos q=at(pos,front,x,y,d); if(!world.isBlockLoaded(q))continue;
     TileEntity t=world.getTileEntity(q);
     if(t instanceof NuclearPart)((NuclearPart)t).unbind(pos);
   }
 }
 private void acceptBucket(){
   ItemStack in=inventory.getStackInSlot(1),out=inventory.getStackInSlot(2);
   if(in.getItem()!=Items.WATER_BUCKET || tank.getCapacity()-water()<1000 || (!out.isEmpty() && (out.getItem()!=Items.BUCKET || out.getCount()>=out.getMaxStackSize())))return;
   if(tank.fill(new FluidStack(FluidRegistry.WATER,1000),true)!=1000)return;
   inventory.extractItem(1,1,false);
   if(out.isEmpty())inventory.setStackInSlot(2,new ItemStack(Items.BUCKET));else {out.grow(1);inventory.setStackInSlot(2,out);}
 }
 public void setEnabled(boolean value){
   if(!usable())return;
   if(value){
     int structure=validateStructure();
     if(structure!=1){stopForStructureLoss();return;}
     formed=true;
     if(temperature>RESET && scrammed){markDirty();return;}
     bindStructure(); enabled=true; scrammed=false;
   } else enabled=false;
   markDirty();
 }
 /** A structural interruption is latched; restoring chunks/blocks alone cannot restart. */
 private void stopForStructureLoss(){
   if(enabled)scrammed=true;
   enabled=false; formed=false; production=0; setIndicator(false); markDirty();
 }
 @Override public void update(){
   if(!usable() || reserved)return;
   resetOutputForWorldTick();
   int structure=validateStructure(); formed=structure==1;
   if(structure==1)bindStructure();
   else {stopForStructureLoss();if(structure==0)releaseStructure();}
   // A missing chunk pauses everything, including coolant, heat and output.
   if(structure<0){production=0;setIndicator(false);return;}
   acceptBucket(); production=0;
   if(enabled && formed && energy<=CAPACITY-FE_PER_TICK){
     if(fuelTicks==0 && inventory.getStackInSlot(0).getItem()==NuclearRegistry.FUEL) {inventory.extractItem(0,1,false);fuelTicks=FUEL_TICKS;}
     if(fuelTicks>0){
       fuelTicks--; energy+=FE_PER_TICK; production=FE_PER_TICK; temperature+=3;
       if(water()>0){tank.drainInternal(1,true);temperature=Math.max(0,temperature-4);}
     }
   }
   temperature=Math.max(0,temperature-1);
   if(temperature>=SCRAM){enabled=false;scrammed=true;}
   setIndicator(enabled && production>0);
   if(formed)pushOutput(); markDirty();
 }
 private void setIndicator(boolean active){
   IBlockState s=world.getBlockState(pos);
   if(s.getBlock()==NuclearRegistry.CONTROLLER && s.getValue(NuclearBlock.ACTIVE)!=active)world.setBlockState(pos,s.withProperty(NuclearBlock.ACTIVE,active),3);
 }
 private void pushOutput(){
   for(EnumFacing side:EnumFacing.VALUES){
     int offer=Math.min(energy,OUTPUT-outputThisTick); if(offer<=0)break;
     BlockPos target=pos.offset(side); if(!world.isBlockLoaded(target))continue;
     TileEntity other=world.getTileEntity(target);if(other==null)continue;
     // Reserve first: a malicious/reentrant receiver cannot also pull these FE.
     try {
       IEnergyStorage receiver=other.getCapability(CapabilityEnergy.ENERGY,side.getOpposite());
       if(receiver==null || !receiver.canReceive())continue;
       offer=Math.min(energy,OUTPUT-outputThisTick); if(offer<=0)break;
       energy-=offer; outputThisTick+=offer; reserved=true;
       int got=receiver.receiveEnergy(offer,false); got=clamp(got,offer);
       energy+=offer-got;outputThisTick-=offer-got;
     } catch(RuntimeException ex) {
       // If receiver changed state before throwing, keeping the reservation avoids duplication.
     } finally {reserved=false;}
   }
 }
 @Override public boolean hasCapability(Capability<?> cap,@Nullable EnumFacing face){return cap==CapabilityEnergy.ENERGY || cap==CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY || cap==CapabilityItemHandler.ITEM_HANDLER_CAPABILITY || super.hasCapability(cap,face);}
 @Override public <T>T getCapability(Capability<T> cap,@Nullable EnumFacing face){
   if(cap==CapabilityEnergy.ENERGY)return CapabilityEnergy.ENERGY.cast(storage);
   if(cap==CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY)return CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY.cast(tank);
   if(cap==CapabilityItemHandler.ITEM_HANDLER_CAPABILITY)return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(automation);
   return super.getCapability(cap,face);
 }
 @Override public NBTTagCompound writeToNBT(NBTTagCompound n){super.writeToNBT(n);n.setLong("OutputTick",outputTick);n.setInteger("OutputThisTick",outputThisTick);n.setInteger("Energy",energy);n.setInteger("Temperature",temperature);n.setInteger("FuelTicks",fuelTicks);n.setBoolean("Enabled",enabled);n.setBoolean("Scrammed",scrammed);n.setTag("Inventory",inventory.serializeNBT());n.setTag("Tank",tank.writeToNBT(new NBTTagCompound()));return n;}
 @Override public void readFromNBT(NBTTagCompound n){super.readFromNBT(n);energy=clamp(n.getInteger("Energy"),CAPACITY);temperature=clamp(n.getInteger("Temperature"),SCRAM);fuelTicks=clamp(n.getInteger("FuelTicks"),FUEL_TICKS);enabled=false;scrammed=n.getBoolean("Scrammed") || n.getBoolean("Enabled") || temperature>=SCRAM;inventory.deserializeNBT(n.getCompoundTag("Inventory"));tank.readFromNBT(n.getCompoundTag("Tank"));if(tank.getFluid()!=null && tank.getFluid().getFluid()!=FluidRegistry.WATER)tank.setFluid(null);if(tank.getFluidAmount()>WATER_CAPACITY)tank.setFluid(new FluidStack(FluidRegistry.WATER,WATER_CAPACITY));formed=false;production=0;outputTick=n.hasKey("OutputTick")?n.getLong("OutputTick"):Long.MIN_VALUE;outputThisTick=clamp(n.getInteger("OutputThisTick"),OUTPUT);}
 @Override public boolean shouldRefresh(World w,BlockPos p,IBlockState a,IBlockState b){return a.getBlock()!=b.getBlock();}
 public int field(int i){switch(i){case 0:return energy;case 1:return production;case 2:return temperature;case 3:return fuelTicks;case 4:return water();case 5:return (enabled?1:0)|(scrammed?2:0)|(formed?4:0);default:return 0;}}
 // Client display only; server state never accepts these values from a packet.
 void setClientField(int i,int v){switch(i){case 0:energy=v;break;case 1:production=v;break;case 2:temperature=v;break;case 3:fuelTicks=v;break;case 4:clientWater=v;break;case 5:enabled=(v&1)!=0;scrammed=(v&2)!=0;formed=(v&4)!=0;break;}}
 private int clientWater;
 int displayField(int i){return i==4 && world!=null && world.isRemote?clientWater:field(i);}
}
