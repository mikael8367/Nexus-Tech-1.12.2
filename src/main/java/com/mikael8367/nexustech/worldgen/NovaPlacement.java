package com.mikael8367.nexustech.worldgen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.nbt.NBTTagCompound;

/** Experimental placement algorithm. NOT registered, NOT a Forge world adapter.
 * This API is for isolated simulations only; it cannot establish safety of a real World.
 * Coordinates supplied to plan are the minimum corner (WE offsets are NOT applied).
 */
public final class NovaPlacement {
    public static final boolean FORGE_GENERATION_ENABLED = false;
    private NovaPlacement() { }

    public static final class Entry {
        public final int x, y, z, id, metadata;
        private final NBTTagCompound tile;
        private Entry(int x, int y, int z, int id, int metadata, NBTTagCompound tile) {
            this.x=x; this.y=y; this.z=z; this.id=id; this.metadata=metadata;
            this.tile=tile==null?null:tile.copy();
        }
        public NBTTagCompound tileCopy() { return tile==null?null:tile.copy(); }
    }

    public static final class Plan {
        public final int chunkX, chunkZ;
        private final List<Entry> entries;
        private Plan(int cx,int cz,List<Entry> entries) {
            chunkX=cx; chunkZ=cz; this.entries=Collections.unmodifiableList(entries);
        }
        public List<Entry> entries() { return entries; }
    }

    /** Limit to exactly the authorised chunk; never ask a backend about a neighbour.
     * yMaxExclusive is the simulated world's height, at most 256 in 1.12.2.
     */
    public static Plan plan(NovaSchematic schematic,int x,int y,int z,int chunkX,int chunkZ,int yMaxExclusive) {
        if(schematic==null || yMaxExclusive<=0 || yMaxExclusive>256) throw new IllegalArgumentException("invalid input");
        long maxX=(long)x+5, maxY=(long)y+4, maxZ=(long)z+6;
        if(y<0 || maxY>=yMaxExclusive || maxX>Integer.MAX_VALUE || maxZ>Integer.MAX_VALUE)
            throw new IllegalArgumentException("coordinate overflow or height");
        if((x>>4)!=chunkX || ((int)maxX>>4)!=chunkX || (z>>4)!=chunkZ || ((int)maxZ>>4)!=chunkZ)
            throw new IllegalArgumentException("outside authorised chunk");
        List<Entry> out=new ArrayList<>(210);
        for(NovaSchematic.Cell c:schematic.snapshot()) {
            int wx=x+c.x, wy=y+c.y, wz=z+c.z;
            NBTTagCompound tile=c.tileCopy();
            if(tile!=null) { tile.setInteger("x",wx); tile.setInteger("y",wy); tile.setInteger("z",wz); }
            out.add(new Entry(wx,wy,wz,c.id,c.metadata,tile));
        }
        if(out.size()!=210) throw new IllegalArgumentException("invalid volume");
        return new Plan(chunkX,chunkZ,out);
    }

    /** Contract is simulation-only: exclusive lock covers validation and every write.
     * compatible must reject unproven mappings and tile incompatibilities; mayReplace
     * must reject protected blocks, inventories, machines and existing tiles.
     * equal must include full tile NBT, not just block and metadata.
     * Calls must never consult chunks outside the plan; a real World does not satisfy
     * this contract merely by implementing these methods. No real adapter exists.
     */
    public interface SimulationBackend {
        void lock();
        void unlock();
        boolean authorisedChunkAvailable(int cx,int cz);
        boolean compatible(Entry e);
        boolean equal(Entry e);
        boolean mayReplace(Entry e);
        void write(Entry e);
    }
    public enum Result { APPLIED, ALREADY_PRESENT, REJECTED, PARTIAL_FAILURE }
    public static final class Outcome {
        public final Result result;
        /** Number of write calls that returned normally, not a rollback guarantee. */
        public final int completedWrites;
        private Outcome(Result result,int writes) { this.result=result; this.completedWrites=writes; }
    }
    /** No rollback. A write can mutate before throwing; partial means unknown state.
     * Exceptions in preflight return REJECTED with zero write calls. Do not use in game.
     */
    public static Outcome simulate(Plan plan, SimulationBackend backend) {
        if(plan==null || backend==null) throw new IllegalArgumentException("null argument");
        int writes=0; boolean writing=false;
        backend.lock();
        try {
            if(!backend.authorisedChunkAvailable(plan.chunkX,plan.chunkZ)) return new Outcome(Result.REJECTED,0);
            boolean allEqual=true;
            for(Entry e:plan.entries) {
                if(!backend.compatible(e)) return new Outcome(Result.REJECTED,0);
                if(!backend.equal(e)) allEqual=false;
            }
            if(allEqual) return new Outcome(Result.ALREADY_PRESENT,0);
            for(Entry e:plan.entries) if(!backend.mayReplace(e)) return new Outcome(Result.REJECTED,0);
            writing=true;
            for(Entry e:plan.entries) { backend.write(e); writes++; }
            return new Outcome(Result.APPLIED,writes);
        } catch(RuntimeException ex) {
            return new Outcome(writing?Result.PARTIAL_FAILURE:Result.REJECTED,writes);
        } finally { backend.unlock(); }
    }
}
