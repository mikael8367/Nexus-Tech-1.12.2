package com.mikael8367.nexustech.worldgen;
import java.io.*;
import java.util.*;
import net.minecraft.nbt.*;

/** Offline simulation and exact resource checks only, never use a real world. */
public final class NovaAnexoTest {
    static int checks;
    static void ok(boolean x) { checks++; if(!x) throw new AssertionError("check "+checks); }
    static void bad(Runnable r) { try {r.run(); throw new AssertionError("unexpected acceptance");} catch(IllegalArgumentException expected) {checks++;} }
    static final class Sim implements NovaPlacement.SimulationBackend {
        Map<String,NovaPlacement.Entry> map=new HashMap<>(); int calls, fail=-1; boolean failAfter, badLock, badUnlock, compatible=true;
        String key(NovaPlacement.Entry e) {return e.x+":"+e.y+":"+e.z;}
        public void lock() {if(badLock)throw new IllegalStateException();}
        public void unlock() {if(badUnlock)throw new IllegalStateException();}
        public boolean authorisedChunkAvailable(int x,int z) {return x==-1&&z==-1;}
        public boolean compatible(NovaPlacement.Entry e) {ok(e.x>>4==-1 && e.z>>4==-1); return compatible;}
        public boolean equal(NovaPlacement.Entry e) {NovaPlacement.Entry p=map.get(key(e)); return p!=null&&p.id==e.id&&p.metadata==e.metadata&&Objects.equals(p.tileCopy(),e.tileCopy());}
        public boolean mayReplace(NovaPlacement.Entry e) {return !map.containsKey(key(e));}
        public void write(NovaPlacement.Entry e) {int i=calls++; if(i==fail&&!failAfter)throw new IllegalStateException(); map.put(key(e),e); if(i==fail)throw new IllegalStateException();}
    }
    public static void main(String[] args) throws Exception {
        if(args.length>0 && "checksum-negative".equals(args[0])) {
            try { NovaSchematic.loadResource(NovaSchematic.Resource.ANEXO_11X14X11); throw new AssertionError("tampered resource accepted"); }
            catch(IOException expected) { if(!"resource checksum mismatch".equals(expected.getMessage())) throw expected; }
            System.out.println("PASS tampered anexo checksum rejected"); return;
        }
        NovaSchematic s=NovaSchematic.loadResource(NovaSchematic.Resource.ANEXO_11X14X11);
        ok(s.width()==11&&s.height()==14&&s.length()==11&&s.volume()==1694);
        ok(Arrays.equals(s.worldEditOffsets(),new int[]{3,-12,4}));
        for(int margin=0;margin<=3;margin++) for(int localX=0;localX<16;localX++) for(int localZ=0;localZ<16;localZ++) {
            boolean allowed=localX>=margin && localZ>=margin && localX+10+margin<16 && localZ+10+margin<16;
            try { NovaPlacement.Plan q=NovaPlacement.planWithMargin(s,localX-16,0,localZ-16,-1,-1,256,margin); ok(allowed && q.entries().size()==1694); }
            catch(IllegalArgumentException e) { ok(!allowed); }
        }
        NBTTagCompound n; try(InputStream in=NovaAnexoTest.class.getResourceAsStream("/assets/nexustech/structures/nova_anexo_11x14x11_sem_vidro.schematic")) {n=CompressedStreamTools.readCompressed(in);}
        byte[] blocks=n.getByteArray("Blocks"), data=n.getByteArray("Data");
        byte[] add=n.hasKey("AddBlocks",7)?n.getByteArray("AddBlocks"):new byte[847];
        int air=0,lava0=0,lava8=0; List<NovaSchematic.Cell> cells=s.snapshot(); ok(cells.size()==1694);
        NovaPlacement.Plan p=NovaPlacement.planWithMargin(s,-15,10,-15,-1,-1,256,1); ok(p.entries().size()==1694);
        for(int i=0;i<1694;i++) {
            NovaSchematic.Cell c=cells.get(i); NovaPlacement.Entry e=p.entries().get(i);
            int id=(blocks[i]&255)|((((add[i/2]&255)>>>((i&1)*4))&15)<<8);
            ok(i==(c.y*11+c.z)*11+c.x); ok(c.id==id&&c.metadata==(data[i]&255));
            ok(e.x==-15+c.x&&e.y==10+c.y&&e.z==-15+c.z&&e.id==c.id&&e.metadata==c.metadata&&e.tileCopy()==null);
            if(id==0)air++; if(id==11&&c.metadata==0)lava0++; if(id==11&&c.metadata==8)lava8++;
            ok(c.tileCopy()==null);
        }
        ok(air==1306&&lava0==3&&lava8==15);
        NovaSchematic.Cell removed=cells.get((13*11)*11); ok(removed.x==0&&removed.y==13&&removed.z==0&&removed.id==0&&removed.metadata==0);
        System.out.println("anexo air="+air+" lava0="+lava0+" lava8="+lava8+" offsets="+Arrays.toString(s.worldEditOffsets()));
        bad(()->NovaSchematic.inspect(n));
        bad(()->NovaSchematic.inspect(n,NovaSchematic.Resource.LEGACY_6X5X7));
        bad(()->NovaSchematic.inspect(n,null));
        NBTTagCompound changed=n.copy(); changed.setShort("Width",(short)6); final NBTTagCompound wrongWidth=changed; bad(()->NovaSchematic.inspect(wrongWidth,NovaSchematic.Resource.ANEXO_11X14X11));
        changed=n.copy(); changed.setByteArray("Blocks",new byte[1693]); final NBTTagCompound a=changed; bad(()->NovaSchematic.inspect(a,NovaSchematic.Resource.ANEXO_11X14X11));
        changed=n.copy(); changed.getByteArray("Data")[7]=16; final NBTTagCompound b=changed; bad(()->NovaSchematic.inspect(b,NovaSchematic.Resource.ANEXO_11X14X11));
        changed=n.copy(); NBTTagList ts=new NBTTagList(); NBTTagCompound tile=new NBTTagCompound(); tile.setString("id","test"); tile.setInteger("x",0);tile.setInteger("y",0);tile.setInteger("z",0); ts.appendTag(tile); changed.setTag("TileEntities",ts); final NBTTagCompound c=changed; bad(()->NovaSchematic.inspect(c,NovaSchematic.Resource.ANEXO_11X14X11));
        changed=n.copy(); changed.getByteArray("Blocks")[0]=20; final NBTTagCompound d=changed; bad(()->NovaSchematic.inspect(d,NovaSchematic.Resource.ANEXO_11X14X11));
        changed=n.copy(); changed.setInteger("WEOffsetX",1); changed.removeTag("WEOffsetY"); final NBTTagCompound f=changed; bad(()->NovaSchematic.inspect(f,NovaSchematic.Resource.ANEXO_11X14X11));
        bad(()->NovaPlacement.plan(s,-10,10,-15,-1,-1,256));
        bad(()->NovaPlacement.plan(s,-15,243,-15,-1,-1,256));
        bad(()->NovaPlacement.plan(s,-15,-1,-15,-1,-1,256));
        bad(()->NovaPlacement.plan(s,Integer.MAX_VALUE,0,0,Integer.MAX_VALUE>>4,0,256));
        bad(()->NovaPlacement.planWithMargin(s,Integer.MIN_VALUE,0,0,Integer.MIN_VALUE>>4,0,256,1));
        bad(()->NovaPlacement.planWithMargin(s,-16,0,-15,-1,-1,256,1));
        bad(()->NovaPlacement.planWithMargin(s,-15,0,-15,-1,-1,256,-1));
        bad(()->NovaPlacement.planWithMargin(s,-14,0,-14,-1,-1,256,3));
        ok(NovaPlacement.planWithMargin(s,-14,242,-14,-1,-1,256,2).entries().size()==1694);
        Sim sim=new Sim(); NovaPlacement.Outcome out=NovaPlacement.simulate(p,sim);ok(out.result==NovaPlacement.Result.APPLIED&&out.completedWrites==1694&&sim.map.size()==1694);
        out=NovaPlacement.simulate(p,sim);ok(out.result==NovaPlacement.Result.ALREADY_PRESENT&&out.completedWrites==0);
        for(int index:new int[]{0,1,847,1693}) for(boolean after:new boolean[]{false,true}) { Sim t=new Sim();t.fail=index;t.failAfter=after;out=NovaPlacement.simulate(p,t);ok(out.result==NovaPlacement.Result.PARTIAL_FAILURE&&out.completedWrites==index&&t.map.size()==index+(after?1:0)); }
        Sim locked=new Sim();locked.badLock=true;out=NovaPlacement.simulate(p,locked);ok(out.result==NovaPlacement.Result.REJECTED&&out.completedWrites==0);
        Sim unlocked=new Sim();unlocked.badUnlock=true;out=NovaPlacement.simulate(p,unlocked);ok(out.result==NovaPlacement.Result.PARTIAL_FAILURE&&out.completedWrites==1694);
        Sim reject=new Sim();reject.compatible=false;out=NovaPlacement.simulate(p,reject);ok(out.result==NovaPlacement.Result.REJECTED&&reject.calls==0);
        ok(!NovaPlacement.FORGE_GENERATION_ENABLED);ok(NovaSchematic.loadResource().volume()==210);
        System.out.println("PASS "+checks+" checks; exact resource, full metadata and coordinates; simulation only");
    }
}
