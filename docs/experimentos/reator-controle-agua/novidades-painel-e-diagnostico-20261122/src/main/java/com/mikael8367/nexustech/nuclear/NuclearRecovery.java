package com.mikael8367.nexustech.nuclear;

import com.mikael8367.nexustech.NexusTech;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** Per-dimension recovery survives removal of the controller and rejected entity spawns. */
@Mod.EventBusSubscriber(modid=NexusTech.MODID)
public final class NuclearRecovery extends WorldSavedData {
 private static final String KEY="nexustech_nuclear_recovery";
 private final List<Entry> entries=new ArrayList<>();
 // Exact mB, bound to the original dimension and block position.
 private final List<WaterEntry> water=new ArrayList<>();
 private static final class WaterEntry {
  final BlockPos pos; int amount;
  WaterEntry(BlockPos pos,int amount){this.pos=pos.toImmutable();this.amount=amount;}
 }
 private boolean delivering;
 private int next;
 private static final class Entry {
  final BlockPos pos; final ItemStack stack; boolean quarantined;
  Entry(BlockPos p,ItemStack s){pos=p.toImmutable();stack=s.copy();}
 }
 public NuclearRecovery(){this(KEY);}
 public NuclearRecovery(String name){super(name);}
 static NuclearRecovery get(World w){
  MapStorage storage=w.getPerWorldStorage();
  NuclearRecovery data=(NuclearRecovery)storage.getOrLoadData(NuclearRecovery.class,KEY);
  if(data==null){data=new NuclearRecovery(KEY);storage.setData(KEY,data);}
  return data;
 }
 void enqueue(BlockPos pos,ItemStack stack){if(!stack.isEmpty()){entries.add(new Entry(pos,stack));markDirty();}}
 void enqueueWater(BlockPos pos,int amount){if(amount>0){water.add(new WaterEntry(pos,amount));markDirty();}}
 int waterAt(BlockPos pos){int total=0;for(WaterEntry e:water)if(e.pos.equals(pos))total=(int)Math.min(Integer.MAX_VALUE,(long)total+e.amount);return total;}
 /** Read-only, point-in-time diagnostic. Call only on the owning server thread. */
 WaterSnapshot waterSnapshot(BlockPos pos){
  if(pos==null)throw new IllegalArgumentException("position required");
  long total=0;int count=0;
  for(WaterEntry e:water)if(e.pos.equals(pos)){total+=e.amount;count++;}
  return new WaterSnapshot(total,count);
 }
 /** No position, player or world references escape into the diagnostic. */
 static final class WaterSnapshot {
  final long pendingMb; final int entryCount;
  private WaterSnapshot(long pendingMb,int entryCount){this.pendingMb=pendingMb;this.entryCount=entryCount;}
  /** Estimate only: does not authorize or perform a transfer and may become stale. */
  WaterPlan plan(int tankMb){
   if(tankMb<0 || tankMb>NuclearController.WATER_CAPACITY)throw new IllegalArgumentException("invalid tank amount");
   int room=NuclearController.WATER_CAPACITY-tankMb;
   int transferable=(int)Math.min(pendingMb,(long)room);
   return new WaterPlan(room,transferable,pendingMb-transferable);
  }
 }
 static final class WaterPlan {
  final int roomMb,transferableMb; final long remainingMb;
  private WaterPlan(int roomMb,int transferableMb,long remainingMb){this.roomMb=roomMb;this.transferableMb=transferableMb;this.remainingMb=remainingMb;}
 }
 // No world callbacks: caller installs the returned water immediately.
 int takeWater(BlockPos pos,int limit){
  int taken=0;
  for(int i=0;i<water.size() && taken<limit;){
   WaterEntry e=water.get(i);if(!e.pos.equals(pos)){i++;continue;}
   int n=Math.min(e.amount,limit-taken);taken+=n;e.amount-=n;
   if(e.amount==0)water.remove(i);else i++;
  }
  if(taken>0)markDirty();return taken;
 }
 int size(){return entries.size();}
 void retry(World w){
  if(delivering || entries.isEmpty())return;
  delivering=true;
  try{
   int budget=Math.min(64,entries.size());
   while(budget-->0 && !entries.isEmpty()){
    if(next>=entries.size())next=0;
    Entry e=entries.get(next);boolean spawned=false;
    if(!e.quarantined && w.isBlockLoaded(e.pos)){
     // Reentrant serialization must see uncertainty before invoking external code.
     // markDirty schedules a save; it is not a synchronous disk commit.
     e.quarantined=true;markDirty();
     try{
      spawned=w.spawnEntity(new EntityItem(w,e.pos.getX()+.5,e.pos.getY()+.5,e.pos.getZ()+.5,e.stack.copy()));
      if(!spawned){e.quarantined=false;markDirty();} // normal false is a definite rejection
     }
     catch(RuntimeException ex){
      // A callback may have spawned the entity before throwing. Do not retry
      // uncertain delivery automatically; preserve the entry for inspection.
      org.apache.logging.log4j.LogManager.getLogger("NexusTech").error("Nuclear recovery delivery indeterminate at {}; entry quarantined",e.pos,ex);
     }
    }
    if(spawned){entries.remove(next);markDirty();}else next++;
   }
  }finally{delivering=false;}
 }
 @SubscribeEvent public static void worldTick(TickEvent.WorldTickEvent e){
  if(!e.world.isRemote && e.phase==TickEvent.Phase.END && e.world.getTotalWorldTime()%20==0)get(e.world).retry(e.world);
 }
 @Override public void readFromNBT(NBTTagCompound n){
  entries.clear();water.clear();next=0;
  NBTTagList list=n.getTagList("Entries",10);
  for(int i=0;i<list.tagCount();i++){
   NBTTagCompound t=list.getCompoundTagAt(i);ItemStack s=new ItemStack(t.getCompoundTag("Stack"));
   if(!s.isEmpty()){Entry e=new Entry(new BlockPos(t.getInteger("X"),t.getInteger("Y"),t.getInteger("Z")),s);e.quarantined=t.getBoolean("Quarantined");entries.add(e);}
  }
  NBTTagList fluids=n.getTagList("Water",10);
  for(int i=0;i<fluids.tagCount();i++){NBTTagCompound t=fluids.getCompoundTagAt(i);int amount=t.getInteger("Amount");if(amount>0)water.add(new WaterEntry(new BlockPos(t.getInteger("X"),t.getInteger("Y"),t.getInteger("Z")),amount));}
 }
 @Override public NBTTagCompound writeToNBT(NBTTagCompound n){
  NBTTagList list=new NBTTagList();
  for(Entry e:entries){NBTTagCompound t=new NBTTagCompound();t.setInteger("X",e.pos.getX());t.setInteger("Y",e.pos.getY());t.setInteger("Z",e.pos.getZ());t.setBoolean("Quarantined",e.quarantined);t.setTag("Stack",e.stack.writeToNBT(new NBTTagCompound()));list.appendTag(t);}
  n.setTag("Entries",list);
  NBTTagList fluids=new NBTTagList();for(WaterEntry e:water){NBTTagCompound t=new NBTTagCompound();t.setInteger("X",e.pos.getX());t.setInteger("Y",e.pos.getY());t.setInteger("Z",e.pos.getZ());t.setInteger("Amount",e.amount);fluids.appendTag(t);}n.setTag("Water",fluids);return n;
 }
}
