package com.mikael8367.nexustech.digitalmine;

import java.lang.reflect.Field;
import java.util.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;
import sun.misc.Unsafe;

public final class DigitalMineTest {
    static void check(boolean b, String s) { if (!b) throw new AssertionError(s); }
    static class TestWorld extends World {
        Map<BlockPos,IBlockState> states; Map<BlockPos,TileEntity> tiles; Set<BlockPos> unloaded; int illegalReads;
        net.minecraft.world.GameRules rules; boolean allowSpawn; List<ItemStack> spawned;
        net.minecraft.world.storage.MapStorage storage; Runnable onDirty; Runnable onSpawn; long time;
        private TestWorld() { super(null,null,null,null,false); }
        protected IChunkProvider createChunkProvider() { return null; }
        protected boolean isChunkLoaded(int x,int z,boolean a) { return true; }
        public boolean isBlockLoaded(BlockPos p) { return !unloaded.contains(p); }
        public IBlockState getBlockState(BlockPos p) { if (unloaded.contains(p)) { illegalReads++; throw new AssertionError("unloaded state"); } IBlockState s=states.get(p); return s == null ? Blocks.AIR.getDefaultState() : s; }
        public TileEntity getTileEntity(BlockPos p) { if (unloaded.contains(p)) { illegalReads++; throw new AssertionError("unloaded tile"); } return tiles.get(p); }
        public int getHeight() { return 256; }
        public net.minecraft.world.GameRules getGameRules() { return rules; }
        public boolean spawnEntity(net.minecraft.entity.Entity entity) {
            if (onSpawn != null) { Runnable callback=onSpawn; onSpawn=null; callback.run(); }
            if (!allowSpawn || net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.entity.EntityJoinWorldEvent(entity, this))) return false;
            spawned.add(((net.minecraft.entity.item.EntityItem) entity).getItem().copy()); return true;
        }
        public void markChunkDirty(BlockPos p, TileEntity t) { if (onDirty != null) { Runnable callback=onDirty; onDirty=null; callback.run(); } }
        public net.minecraft.world.storage.MapStorage getPerWorldStorage() { return storage; }
        public long getTotalWorldTime() { return time; }
        public void removeTileEntity(BlockPos p) { TileEntity tile=tiles.remove(p); if (tile!=null) tile.invalidate(); }
    }
    static TestWorld world() throws Exception {
        Field f=Unsafe.class.getDeclaredField("theUnsafe"); f.setAccessible(true);
        Unsafe u=(Unsafe)f.get(null); TestWorld w=(TestWorld)u.allocateInstance(TestWorld.class);
        w.states=new HashMap<>(); w.tiles=new HashMap<>(); w.unloaded=new HashSet<>();
        w.rules=new net.minecraft.world.GameRules(); w.allowSpawn=true; w.spawned=new ArrayList<>(); w.storage=new net.minecraft.world.storage.MapStorage(null);
        Field rand=World.class.getDeclaredField("rand"); rand.setAccessible(true); rand.set(w,new Random(1));
        Field provider=World.class.getDeclaredField("provider"); provider.setAccessible(true); provider.set(w,new net.minecraft.world.WorldProviderSurface());
        return w;
    }
    static DigitalMineTile.Basic assemble(TestWorld w, BlockPos origin, EnumFacing front) {
        DigitalMineTile.Basic t = new DigitalMineTile.Basic(); t.setWorld(w); t.setPos(origin); w.tiles.put(origin,t);
        w.states.put(origin, DigitalMineRegistry.BASIC.getDefaultState().withProperty(DigitalMineBlock.FACING,front));
        for (int d=0;d<3;d++) for (int y=0;y<2;y++) for (int x=-1;x<=1;x++) {
            BlockPos p=origin.offset(front.getOpposite(),d).offset(front.rotateY(),x).up(y);
            if (!p.equals(origin)) w.states.put(p, DigitalMineRegistry.CASING.getDefaultState());
        }
        return t;
    }
    static class Source extends TileEntity implements net.minecraftforge.energy.IEnergyStorage {
        int stored=10000, extracted=0;
        public boolean hasCapability(net.minecraftforge.common.capabilities.Capability<?> c, EnumFacing side) { return c == net.minecraftforge.energy.CapabilityEnergy.ENERGY; }
        public <T> T getCapability(net.minecraftforge.common.capabilities.Capability<T> c, EnumFacing side) { return c == net.minecraftforge.energy.CapabilityEnergy.ENERGY ? net.minecraftforge.energy.CapabilityEnergy.ENERGY.cast(this) : null; }
        public int receiveEnergy(int n, boolean s) { return 0; }
        public int extractEnergy(int n, boolean s) { int v=Math.min(stored,n); if(!s){stored-=v; extracted+=v;} return v; }
        public int getEnergyStored(){return stored;} public int getMaxEnergyStored(){return 10000;}
        public boolean canExtract(){return true;} public boolean canReceive(){return false;}
    }
    static class FaultySource extends Source {
        final String phase;
        FaultySource(String p) { phase=p; }
        public boolean hasCapability(net.minecraftforge.common.capabilities.Capability<?> c, EnumFacing side) {
            if (phase.equals("has")) throw new IllegalStateException("has"); return super.hasCapability(c,side);
        }
        public <T> T getCapability(net.minecraftforge.common.capabilities.Capability<T> c, EnumFacing side) {
            if (phase.equals("get")) throw new IllegalStateException("get"); return super.getCapability(c,side);
        }
        public boolean canExtract() { if (phase.equals("can")) throw new IllegalStateException("can"); return true; }
        public int extractEnergy(int n, boolean simulated) {
            if (phase.equals("extract")) throw new IllegalStateException("extract");
            if (phase.equals("mutate")) { super.extractEnergy(250,false); throw new IllegalStateException("after mutation"); }
            return super.extractEnergy(n,simulated);
        }
    }
    static void testFaultsAndDrops() throws Exception {
        java.lang.reflect.Method pull=DigitalMineTile.class.getDeclaredMethod("pullEnergy"); pull.setAccessible(true);
        for (String phase : new String[]{"has","get","can","extract","mutate"}) {
            TestWorld w=world(); BlockPos p=new BlockPos(20,64,20); DigitalMineTile.Basic machine=assemble(w,p,EnumFacing.NORTH);
            FaultySource bad=new FaultySource(phase); Source good=new Source();
            w.tiles.put(p.down(),bad); w.tiles.put(p.up(),good);
            pull.invoke(machine);
            check(machine.energy==1000 && good.extracted==1000,"failed "+phase+" isolated, healthy endpoint credited once");
            check(bad.extracted==(phase.equals("mutate")?250:0),"uncredited failed extraction "+phase);
            machine.energy=0; w.tiles.remove(p.down()); pull.invoke(machine);
            check(machine.energy==1000 && good.extracted==2000,"transfer guard reset after "+phase);
        }
        TestWorld w=world(); BlockPos p=new BlockPos(20,64,20); DigitalMineTile.Basic machine=assemble(w,p,EnumFacing.NORTH);
        w.rules.setOrCreateGameRule("doTileDrops","false");
        net.minecraft.block.Block.spawnAsEntity(w,p,new ItemStack(Blocks.DIAMOND_BLOCK));
        check(w.spawned.isEmpty(),"vanilla spawnAsEntity suppressed by game rule");
        for(int i=0;i<machine.inventory.getSlots();i++) machine.inventory.setStackInSlot(i,new ItemStack(Blocks.STONE,64));
        DigitalMineRecovery recovery=DigitalMineRecovery.get(w);
        machine.queueDrop(new ItemStack(Blocks.DIAMOND_BLOCK,3)); machine.deliverPending(p);
        check(w.spawned.isEmpty() && recovery.size()==1 && machine.pendingDrops().isEmpty(),"overflow handed to recovery without immediate callback");
        recovery.retry(w);
        check(w.spawned.size()==1 && w.spawned.get(0).getCount()==3 && recovery.size()==0,"explicit overflow spawn with doTileDrops false");
        w.allowSpawn=false; machine.queueDrop(new ItemStack(Blocks.DIAMOND_BLOCK,5)); machine.deliverPending(p); recovery.retry(w);
        check(recovery.size()==1,"rejected spawn retained in world data");
        NBTTagCompound nbt=recovery.writeToNBT(new NBTTagCompound()); DigitalMineRecovery restored=new DigitalMineRecovery(); restored.readFromNBT(nbt);
        check(restored.size()==1,"world recovery NBT restored");
        w.unloaded.add(p); w.allowSpawn=true; restored.retry(w);
        check(restored.size()==1 && w.spawned.size()==1,"unloaded recovery deferred"); w.unloaded.clear();
        restored.retry(w); restored.retry(w);
        check(restored.size()==0 && w.spawned.size()==2 && w.spawned.get(1).getCount()==5,"recovered once after restart");
        machine.queueDrop(new ItemStack(Blocks.DIAMOND_BLOCK,2)); machine.invalidate(); machine.deliverPending(p);
        check(recovery.size()==2 && machine.pendingDrops().isEmpty(),"detached tile hands drops to world recovery");
        // Recovery had its original entry too: use an isolated world for removal.
        TestWorld rw=world(); BlockPos rp=new BlockPos(40,64,40); DigitalMineTile.Basic rm=assemble(rw,rp,EnumFacing.NORTH);
        rm.inventory.setStackInSlot(0,new ItemStack(Blocks.STONE,7)); rm.queueDrop(new ItemStack(Blocks.DIAMOND_BLOCK,4));
        rw.allowSpawn=false; DigitalMineRegistry.BASIC.breakBlock(rw,rp,rw.states.get(rp));
        DigitalMineRecovery rr=DigitalMineRecovery.get(rw); rr.retry(rw);
        check(rr.size()==2 && rw.spawned.isEmpty(),"breaking controller retains inventory and pending on refusal");
        NBTTagCompound saved=rr.writeToNBT(new NBTTagCompound()); DigitalMineRecovery reloaded=new DigitalMineRecovery(); reloaded.readFromNBT(saved);
        rw.allowSpawn=true; rw.onSpawn=()->reloaded.retry(rw); reloaded.retry(rw); reloaded.retry(rw);
        check(reloaded.size()==0 && rw.spawned.size()==2 && rw.spawned.get(0).getCount()==7 && rw.spawned.get(1).getCount()==4,"break recovery persisted and reentrant retry cannot duplicate");
        // A callback removes the controller inside ItemStackHandler.insertItem.
        TestWorld cw=world(); BlockPos cp=new BlockPos(50,64,50); DigitalMineTile.Basic cm=assemble(cw,cp,EnumFacing.NORTH);
        cm.queueDrop(new ItemStack(Blocks.DIAMOND_BLOCK,9));
        cw.onDirty=()->{ cm.onControllerRemoved(); cw.tiles.remove(cp); cm.invalidate(); };
        cm.deliverPending(cp); DigitalMineRecovery cr=DigitalMineRecovery.get(cw);
        check(cr.size()==1 && cm.pendingDrops().isEmpty() && cm.inventory.getStackInSlot(0).isEmpty(),"insertion callback removal defers transfer without duplication");
        cr.retry(cw); check(cw.spawned.size()==1 && cw.spawned.get(0).getCount()==9,"deferred callback drop recovered exactly once");
        // Full inventory backpressure cache must reset when inventory changes.
        TestWorld bw=world(); BlockPos bp=new BlockPos(60,64,60); DigitalMineTile.Basic bm=assemble(bw,bp,EnumFacing.NORTH);
        for(int i=0;i<bm.inventory.getSlots();i++) bm.inventory.setStackInSlot(i,new ItemStack(Blocks.STONE,64));
        net.minecraft.util.NonNullList<ItemStack> noRoom=net.minecraft.util.NonNullList.create(); noRoom.add(new ItemStack(Blocks.DIAMOND_BLOCK));
        check(!bm.canStoreDrops(bp.down(),Blocks.STONE.getDefaultState(),noRoom),"real capacity check caches blocked target");
        Field blocked=DigitalMineTile.class.getDeclaredField("blockedTarget"); blocked.setAccessible(true);
        java.lang.reflect.Method mine=DigitalMineTile.class.getDeclaredMethod("mine",BlockPos.class,IBlockState.class,int.class); mine.setAccessible(true);
        for(int i=0;i<20;i++) check(((Integer)mine.invoke(bm,bp.down(),Blocks.STONE.getDefaultState(),1200))==2,"blocked target returns before firing events");
        bm.inventory.setStackInSlot(0,ItemStack.EMPTY);
        check(blocked.get(bm)==null && bm.canStoreDrops(bp.down(),Blocks.STONE.getDefaultState(),noRoom),"inventory change wakes blocked target");
        // Removal from within removedByPlayer/harvest callback: mine's finally
        // must flush the removed tile even if entity spawns are denied.
        TestWorld mw=world(); BlockPos mp=new BlockPos(70,64,70); DigitalMineTile.Basic mm=assemble(mw,mp,EnumFacing.NORTH);
        mm.inventory.setStackInSlot(0,new ItemStack(Blocks.STONE,11));
        Field mining=DigitalMineTile.class.getDeclaredField("mining"); mining.setAccessible(true); mining.setBoolean(mm,true);
        mm.onControllerRemoved(); mw.tiles.remove(mp); mm.invalidate(); mm.queueDrop(new ItemStack(Blocks.DIAMOND_BLOCK,6)); mm.deliverPending(mp);
        check(DigitalMineRecovery.get(mw).size()==1 && mm.inventory.getStackInSlot(0).getCount()==11,"in-flight removal waits for mining operation");
        mining.setBoolean(mm,false); java.lang.reflect.Method finish=DigitalMineTile.class.getDeclaredMethod("finishDeferredRemoval"); finish.setAccessible(true); finish.invoke(mm);
        DigitalMineRecovery mr=DigitalMineRecovery.get(mw); mw.allowSpawn=false; mr.retry(mw);
        check(mr.size()==2 && mm.inventory.getStackInSlot(0).isEmpty(),"detached mine contents survive refused spawn");
        DigitalMineRecovery afterRestart=new DigitalMineRecovery(); afterRestart.readFromNBT(mr.writeToNBT(new NBTTagCompound()));
        mw.allowSpawn=true; afterRestart.retry(mw);
        check(afterRestart.size()==0 && mw.spawned.size()==2 && mw.spawned.get(0).getCount()==6 && mw.spawned.get(1).getCount()==11,"in-flight removal recovery roundtrip");


    }
    static void testTickAndCancellation() throws Exception {
        TestWorld w=world(); BlockPos p=new BlockPos(100,64,100); DigitalMineRecovery data=DigitalMineRecovery.get(w);
        data.enqueue(p,new ItemStack(Blocks.DIAMOND_BLOCK,5));
        w.allowSpawn=false;
        DigitalMineRecovery.worldTick(new net.minecraftforge.fml.common.gameevent.TickEvent.WorldTickEvent(net.minecraftforge.fml.relauncher.Side.SERVER,net.minecraftforge.fml.common.gameevent.TickEvent.Phase.START,w));
        w.time=1; DigitalMineRecovery.worldTick(new net.minecraftforge.fml.common.gameevent.TickEvent.WorldTickEvent(net.minecraftforge.fml.relauncher.Side.SERVER,net.minecraftforge.fml.common.gameevent.TickEvent.Phase.END,w));
        check(w.spawned.isEmpty() && data.size()==1,"tick waits for end phase and 20 tick interval");
        w.time=20; DigitalMineRecovery.worldTick(new net.minecraftforge.fml.common.gameevent.TickEvent.WorldTickEvent(net.minecraftforge.fml.relauncher.Side.SERVER,net.minecraftforge.fml.common.gameevent.TickEvent.Phase.END,w));
        check(data.size()==1 && w.spawned.isEmpty(),"refused entity spawn retains item");
        w.allowSpawn=true; w.time=40;
        DigitalMineRecovery.worldTick(new net.minecraftforge.fml.common.gameevent.TickEvent.WorldTickEvent(net.minecraftforge.fml.relauncher.Side.SERVER,net.minecraftforge.fml.common.gameevent.TickEvent.Phase.END,w));
        check(data.size()==0 && w.spawned.size()==1 && w.spawned.get(0).getCount()==5,"later tick retries accepted spawn once");
    }
    static class RemoveBlock extends net.minecraft.block.Block {
        final DigitalMineTile tile; String result; int seen;
        RemoveBlock(DigitalMineTile tile, String result) { super(net.minecraft.block.material.Material.ROCK); this.tile=tile; this.result=result; }
        @Override public boolean removedByPlayer(IBlockState state, World w, BlockPos p, net.minecraft.entity.player.EntityPlayer player, boolean harvest) {
            seen=tile.energy;
            if (result.equals("throw")) throw new IllegalStateException("test callback");
            if (result.equals("success")) ((TestWorld)w).states.put(p,Blocks.AIR.getDefaultState());
            return !result.equals("false");
        }
    }
    static void testEnergyReservation() throws Exception {
        TestWorld w=world(); BlockPos p=new BlockPos(90,64,90), target=p.down(); DigitalMineTile.Basic m=assemble(w,p,EnumFacing.NORTH);
        for (String result : new String[]{"false","unchanged","throw","success"}) {
            m.energy=3000; RemoveBlock block=new RemoveBlock(m,result); IBlockState state=block.getDefaultState(); w.states.put(target,state);
            boolean ok=false;
            try { ok=m.removeTarget(target,state,null,1200); } catch (IllegalStateException ex) { check(result.equals("throw"),"only expected callback exception"); }
            check(block.seen==1800,"energy reserved before "+result+" callback");
            check(ok==result.equals("success") && m.energy==(ok?1800:3000),"energy committed/refunded on "+result);
        }
    }
    static class HarvestListener {
        int calls;
        @net.minecraftforge.fml.common.eventhandler.SubscribeEvent
        public void onHarvest(net.minecraftforge.event.world.BlockEvent.HarvestDropsEvent e) {
            calls++; e.getDrops().clear(); e.getDrops().add(new ItemStack(Blocks.DIAMOND_BLOCK,3)); e.setDropChance(1);
        }
    }
    public static void main(String[] args) throws Exception {
        Bootstrap.register();
        DigitalMineRegistry.registerTiles();
        java.lang.reflect.Constructor<?> ctor=net.minecraftforge.common.capabilities.Capability.class.getDeclaredConstructors()[0]; ctor.setAccessible(true);
        net.minecraftforge.energy.CapabilityEnergy.ENERGY=(net.minecraftforge.common.capabilities.Capability<net.minecraftforge.energy.IEnergyStorage>)ctor.newInstance("test:energy",null,(java.util.concurrent.Callable<net.minecraftforge.energy.IEnergyStorage>)() -> null);
        BlockPos origin=new BlockPos(17,64,20);
        for (boolean advanced : new boolean[]{false,true}) for (EnumFacing front : EnumFacing.HORIZONTALS) {
            TestWorld w=world(); DigitalMineTile tile=advanced ? new DigitalMineTile.Advanced() : new DigitalMineTile.Basic();
            tile.setWorld(w); tile.setPos(origin); w.tiles.put(origin,tile);
            DigitalMineBlock block=advanced ? DigitalMineRegistry.ADVANCED : DigitalMineRegistry.BASIC;
            IBlockState control=block.getDefaultState().withProperty(DigitalMineBlock.FACING,front);
            check(block.getStateFromMeta(block.getMetaFromState(control)).equals(control),"metadata "+front);
            w.states.put(origin,control);
            Set<BlockPos> cells=new HashSet<>();
            for(int d=0;d<3;d++) for(int y=0;y<2;y++) for(int x=-1;x<=1;x++) {
                BlockPos p=origin.offset(front.getOpposite(),d).offset(front.rotateY(),x).up(y); cells.add(p);
                if (!p.equals(origin)) w.states.put(p,DigitalMineRegistry.CASING.getDefaultState());
            }
            check(cells.size()==18,"18 unique positions"); check(tile.structureValid(),"assembled "+front);
            for(BlockPos p: cells) {
                if (p.equals(origin)) continue;
                IBlockState saved=w.states.remove(p); check(!tile.structureValid(),"missing "+p); w.states.put(p,saved);
                w.unloaded.add(p); check(!tile.structureValid(),"unloaded "+p); w.unloaded.remove(p);
            }
            w.unloaded.add(origin); check(!tile.structureValid(),"unloaded controller"); w.unloaded.clear();
            check(w.illegalReads==0,"never read unloaded");
        }
        DigitalMineTile a=new DigitalMineTile.Advanced(); a.energy=54321; a.mode=1; a.cursor=384; a.delay=7; a.enabled=false;
        a.inventory.setStackInSlot(3,new ItemStack(Blocks.STONE,17));
        NBTTagCompound nbt=a.writeToNBT(new NBTTagCompound()); DigitalMineTile b=new DigitalMineTile.Advanced(); b.readFromNBT(nbt);
        check(b.energy==54321 && b.mode==1 && b.cursor==384 && b.delay==7 && !b.enabled,"persistent fields");
        check(b.inventory.getStackInSlot(3).getCount()==17 && b.inventory.getStackInSlot(3).getItem()==net.minecraft.item.Item.getItemFromBlock(Blocks.STONE),"persistent inventory");
        nbt.setInteger("Energy",Integer.MAX_VALUE); nbt.setInteger("Mode",99); nbt.setInteger("Delay",Integer.MAX_VALUE); b.readFromNBT(nbt);
        check(b.energy==b.capacity && b.mode==0 && b.delay==b.baseDelay,"corrupt nbt clamped");
        TestWorld w=world(); BlockPos p=new BlockPos(0,64,0);
        DigitalMineTile.Basic first=assemble(w,p,EnumFacing.NORTH);
        DigitalMineTile.Basic second=assemble(w,p.add(2,0,0),EnumFacing.NORTH);
        check(first.structureValid() && !second.structureValid(),"overlapping casing has one deterministic winner");
        w.states.remove(p); check(second.structureValid(),"reservation released when first is removed");
        w.states.put(p,DigitalMineRegistry.BASIC.getDefaultState().withProperty(DigitalMineBlock.FACING,EnumFacing.NORTH));
        BlockPos firstOnly=p.add(-1,0,0); w.states.remove(firstOnly);
        check(second.structureValid(),"reservation released when first is physically invalid");
        w.states.put(firstOnly,DigitalMineRegistry.CASING.getDefaultState());
        w.unloaded.add(p); check(!second.structureValid(),"unloaded competing controller never assumed free");
        check(w.illegalReads==0,"no unloaded conflict reads");

        TestWorld eworld=world(); BlockPos ep=new BlockPos(20,64,20);
        DigitalMineTile.Basic machine=assemble(eworld,ep,EnumFacing.NORTH);
        Field owner=DigitalMineTile.class.getDeclaredField("owner"); owner.setAccessible(true); owner.set(machine,UUID.randomUUID());
        Source src=new Source(); eworld.tiles.put(ep.down(),src);
        machine.enabled=false; machine.update(); check(src.extracted==0,"disabled does not pull FE");
        machine.enabled=true; eworld.states.remove(ep.up()); machine.update(); check(src.extracted==0,"invalid structure does not pull FE");
        eworld.states.put(ep.up(),DigitalMineRegistry.CASING.getDefaultState());
        machine.energy=2000;
        BlockPos skipped=ep.add(-8,-1,-8); eworld.unloaded.add(skipped);
        machine.update(); check(src.extracted==1000 && machine.cursor==128,"advance beyond unloaded target in one tick");
        machine.update(); check(machine.cursor==256,"does not revisit skipped target next tick");
        check(eworld.illegalReads==0,"never reads skipped target");

        net.minecraft.util.NonNullList<ItemStack> list=net.minecraft.util.NonNullList.create(); list.add(new ItemStack(Blocks.STONE));
        HarvestListener listener=new HarvestListener();
        Field id=net.minecraftforge.fml.common.eventhandler.EventBus.class.getDeclaredField("busID"); id.setAccessible(true);
        int busId=id.getInt(net.minecraftforge.common.MinecraftForge.EVENT_BUS);
        net.minecraftforge.fml.common.eventhandler.ListenerList listeners = new net.minecraftforge.event.world.BlockEvent.HarvestDropsEvent(eworld,ep,Blocks.STONE.getDefaultState(),0,1,list,null,false).getListenerList();
        net.minecraftforge.fml.common.eventhandler.IEventListener hook = event -> { if (event instanceof net.minecraftforge.event.world.BlockEvent.HarvestDropsEvent) listener.onHarvest((net.minecraftforge.event.world.BlockEvent.HarvestDropsEvent)event); };
        listeners.register(busId,net.minecraftforge.fml.common.eventhandler.EventPriority.NORMAL,hook);
        net.minecraft.util.NonNullList<ItemStack> selected=DigitalMineTile.harvestDrops(list,eworld,ep,Blocks.STONE.getDefaultState(),null,new Random(1));
        listeners.unregister(busId,hook);
        check(listener.calls==1 && selected.size()==1 && selected.get(0).getItem()==net.minecraft.item.Item.getItemFromBlock(Blocks.DIAMOND_BLOCK) && selected.get(0).getCount()==3,"one harvest event honors modified list");
        check(DigitalMineTile.selectDrops(selected,-1,new Random(1)).isEmpty(),"drop chance suppresses drops");
        check(DigitalMineTile.selectDrops(selected,1,new Random(1)).size()==1,"drop chance retains drops");
        testFaultsAndDrops(); testEnergyReservation(); testTickAndCancellation();
        Bootstrap.printToSYSOUT("PASS: structures, exclusivity/release, unloaded chunks, disabled/invalid FE, scan, HarvestDropsEvent, chance, metadata, NBT, faulty FE endpoints, persistent recovery, reentrancy, backpressure and FE reservation");
    }
}
