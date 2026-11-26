package com.mikael8367.nexustech.worldgen;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import net.minecraft.nbt.NBTTagCompound;

public final class NovaPlacementTest {
    static int checks;
    static void ok(boolean b) { checks++; if(!b) throw new AssertionError("check "+checks); }
    static void invalid(Runnable r) { try { r.run(); throw new AssertionError("accepted invalid plan"); } catch(IllegalArgumentException good) { checks++; } }
    static String key(NovaPlacement.Entry e) { return e.x+","+e.y+","+e.z; }
    static final class Stored {
        int id,meta; NBTTagCompound tile;
        Stored(NovaPlacement.Entry e) { id=e.id; meta=e.metadata; tile=e.tileCopy(); }
        boolean equal(NovaPlacement.Entry e) { return id==e.id && meta==e.metadata && Objects.equals(tile,e.tileCopy()); }
    }
    static final class Sim implements NovaPlacement.SimulationBackend {
        final ReentrantLock lock=new ReentrantLock();
        final Map<String,Stored> cells=new HashMap<>();
        boolean available=true, compatible=true, replace=true, failRead=false, failAfter=false;
        boolean pretendWrite=false, corruptEarlier=false, failPostRead=false, failLock=false, failUnlock=false;
        int failWrite=-1,calls=0, cx,cz;
        Sim(int cx,int cz) { this.cx=cx;this.cz=cz; }
        public void lock() { if(failLock) throw new IllegalStateException(); lock.lock(); }
        public void unlock() { lock.unlock(); if(failUnlock) throw new IllegalStateException(); }
        void confined(NovaPlacement.Entry e) { if((e.x>>4)!=cx || (e.z>>4)!=cz) throw new AssertionError("neighbor access"); }
        public boolean authorisedChunkAvailable(int x,int z) { if(x!=cx||z!=cz) throw new AssertionError("wrong chunk"); return available; }
        public boolean compatible(NovaPlacement.Entry e) { confined(e); if(failRead) throw new IllegalStateException(); return compatible; }
        public boolean equal(NovaPlacement.Entry e) { confined(e); if(failPostRead && calls==210) throw new IllegalStateException(); Stored s=cells.get(key(e)); return s!=null&&s.equal(e); }
        public boolean mayReplace(NovaPlacement.Entry e) { confined(e); return replace && !cells.containsKey(key(e)); }
        public void write(NovaPlacement.Entry e) { confined(e); int i=calls++; if(i==failWrite&&!failAfter) throw new IllegalStateException(); if(!pretendWrite) cells.put(key(e),new Stored(e)); if(corruptEarlier && i==209) cells.clear(); if(i==failWrite) throw new IllegalStateException(); }
    }
    public static void main(String[] args) throws Exception {
        NovaSchematic schematic=NovaSchematic.loadResource();
        NovaPlacement.Plan p=NovaPlacement.plan(schematic,-16,12,-16,-1,-1,256);
        ok(p.entries().size()==210); ok(!NovaPlacement.FORGE_GENERATION_ENABLED);
        List<NovaSchematic.Cell> src=schematic.snapshot(); int tiles=0,air=0;
        for(int i=0;i<210;i++) {
            NovaSchematic.Cell c=src.get(i); NovaPlacement.Entry e=p.entries().get(i);
            ok(e.x==-16+c.x && e.y==12+c.y && e.z==-16+c.z && e.id==c.id && e.metadata==c.metadata);
            if(e.id==0) air++; ok(e.id!=20&&e.id!=95&&e.id!=102&&e.id!=160);
            NBTTagCompound a=c.tileCopy(), b=e.tileCopy();
            if(a!=null) { tiles++; a.setInteger("x",e.x);a.setInteger("y",e.y);a.setInteger("z",e.z);ok(a.equals(b)); b.setInteger("x",123);ok(e.tileCopy().equals(a)); }
            else ok(b==null);
        }
        ok(tiles==4&&air==119); try { p.entries().clear(); throw new AssertionError(); } catch(UnsupportedOperationException good) { checks++; }
        invalid(()->NovaPlacement.plan(schematic,-5,0,0,-1,0,256));
        invalid(()->NovaPlacement.plan(schematic,0,252,0,0,0,256));
        invalid(()->NovaPlacement.plan(schematic,0,-1,0,0,0,256));
        invalid(()->NovaPlacement.plan(schematic,Integer.MAX_VALUE,0,0,Integer.MAX_VALUE>>4,0,256));
        invalid(()->NovaPlacement.plan(schematic,0,0,0,1,0,256));
        invalid(()->NovaPlacement.plan(schematic,0,0,0,0,0,0));
        invalid(()->NovaPlacement.plan(null,0,0,0,0,0,256));
        Sim s=new Sim(-1,-1); NovaPlacement.Outcome out=NovaPlacement.simulate(p,s);ok(out.result==NovaPlacement.Result.APPLIED&&out.completedWrites==210&&s.cells.size()==210);
        for(NovaPlacement.Entry e:p.entries()) ok(s.cells.get(key(e)).equal(e));
        out=NovaPlacement.simulate(p,s); ok(out.result==NovaPlacement.Result.ALREADY_PRESENT&&out.completedWrites==0&&s.calls==210);
        Sim fake=new Sim(-1,-1); fake.pretendWrite=true; out=NovaPlacement.simulate(p,fake);
        ok(out.result==NovaPlacement.Result.PARTIAL_FAILURE&&out.completedWrites==210&&fake.cells.isEmpty()&&fake.calls==210);
        Sim corrupt=new Sim(-1,-1); corrupt.corruptEarlier=true; out=NovaPlacement.simulate(p,corrupt);
        ok(out.result==NovaPlacement.Result.PARTIAL_FAILURE&&out.completedWrites==210&&corrupt.cells.isEmpty());
        Sim badRead=new Sim(-1,-1); badRead.failPostRead=true; out=NovaPlacement.simulate(p,badRead);
        ok(out.result==NovaPlacement.Result.PARTIAL_FAILURE&&out.completedWrites==210&&badRead.cells.size()==210);
        Sim badLock=new Sim(-1,-1); badLock.failLock=true; out=NovaPlacement.simulate(p,badLock);
        ok(out.result==NovaPlacement.Result.REJECTED&&out.completedWrites==0&&badLock.calls==0);
        Sim badUnlock=new Sim(-1,-1); badUnlock.failUnlock=true; out=NovaPlacement.simulate(p,badUnlock);
        ok(out.result==NovaPlacement.Result.PARTIAL_FAILURE&&out.completedWrites==210&&badUnlock.cells.size()==210);
        badUnlock.failUnlock=false; out=NovaPlacement.simulate(p,badUnlock);
        ok(out.result==NovaPlacement.Result.ALREADY_PRESENT&&out.completedWrites==0);
        badUnlock.failUnlock=true; out=NovaPlacement.simulate(p,badUnlock);
        ok(out.result==NovaPlacement.Result.REJECTED&&out.completedWrites==0);
        for(int mode=0;mode<4;mode++) { Sim t=new Sim(-1,-1); if(mode==0)t.available=false; if(mode==1)t.compatible=false; if(mode==2)t.replace=false; if(mode==3)t.failRead=true; out=NovaPlacement.simulate(p,t);ok(out.result==NovaPlacement.Result.REJECTED&&out.completedWrites==0&&t.cells.isEmpty()); }
        for(int index:new int[]{0,1,55,209}) for(boolean after:new boolean[]{false,true}) {
            Sim t=new Sim(-1,-1);t.failWrite=index;t.failAfter=after;out=NovaPlacement.simulate(p,t);
            ok(out.result==NovaPlacement.Result.PARTIAL_FAILURE&&out.completedWrites==index&&t.cells.size()==index+(after?1:0));
            if(index>0 || after) { t.failWrite=-1; out=NovaPlacement.simulate(p,t); ok(out.result==((index==209 && after)?NovaPlacement.Result.ALREADY_PRESENT:NovaPlacement.Result.REJECTED)&&out.completedWrites==0); }
        }
        Sim conflict=new Sim(-1,-1); conflict.cells.put(key(p.entries().get(209)),new Stored(p.entries().get(209)));
        out=NovaPlacement.simulate(p,conflict);ok(out.result==NovaPlacement.Result.REJECTED&&out.completedWrites==0);
        Sim concurrent=new Sim(-1,-1); ExecutorService pool=Executors.newFixedThreadPool(8); List<Future<NovaPlacement.Outcome>> fs=new ArrayList<>();
        for(int i=0;i<40;i++) fs.add(pool.submit(()->NovaPlacement.simulate(p,concurrent)));
        int applied=0,already=0; for(Future<NovaPlacement.Outcome> f:fs) { NovaPlacement.Result r=f.get().result;if(r==NovaPlacement.Result.APPLIED)applied++;if(r==NovaPlacement.Result.ALREADY_PRESENT)already++; }
        pool.shutdown();ok(applied==1&&already==39&&concurrent.calls==210);
        long begin=System.nanoTime(); for(int i=0;i<1000;i++) { NovaPlacement.Plan q=NovaPlacement.plan(schematic,0,40,0,0,0,256); if(q.entries().size()!=210) throw new AssertionError(); }
        long ms=(System.nanoTime()-begin)/1000000L;
        System.out.println("PASS "+checks+" checks; 210 writes, 4 full tile NBT, retry, injected failures, 40 concurrent calls; 1000 plans="+ms+"ms (local simulation only)");
    }
}
