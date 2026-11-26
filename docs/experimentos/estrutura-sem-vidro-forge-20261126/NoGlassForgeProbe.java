package com.mikael8367.nexustech.worldgen;

import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.util.ClassInheritanceMultiMap;
import net.minecraft.world.chunk.Chunk;

/**
 * Read-only Forge 1.12.2 diagnostic. NOT a world generator. Do not register it.
 * The caller supplies an already obtained Chunk; this class never asks a World or
 * chunk provider for one, nor writes any block/marker. Observations are NOT a
 * reservation, a freshness proof, a protection check or permission to place.
 * Call only on the server thread in a disposable test world. A coremod may
 * transform even vanilla methods; no guarantee is made for hostile mods.
 */
public final class NoGlassForgeProbe implements NoGlassTransactionalGenerator.Backend {
    public enum Status { INVALID, NOT_SELECTED, LOADED_ENTITY_OR_TILE, UNKNOWN_BLOCK,
        TERRAIN_REJECTED, DIAGNOSTIC_PLAN_ONLY, READ_FAILED }
    public static final class Report {
        public final Status status;
        public final int plannedCells;
        private Report(Status status, int plannedCells) {
            this.status=status; this.plannedCells=plannedCells;
        }
    }
    private static Report report(Status s) { return new Report(s,0); }

    /** Every production attempt is blocked even if somebody passes this backend to the core. */
    @Override public NoGlassTransactionalGenerator.Reservation acquireAlreadyLoadedFreshChunk(int cx, int cz) {
        return null;
    }
    /** A direct call is also fail-closed; no state, lock or storage is touched. */
    @Override public boolean compareAndCommit(int cx,int cz,NoGlassTransactionalGenerator.Snapshot expected,
                                               List<NoGlassPlan.Cell> cells,byte[] marker) {
        throw new UnsupportedOperationException("BLOCKED: no durable atomic Forge transaction or recovery");
    }

    /** The returned plan count is diagnostic only: never a placement authorization. */
    public static Report inspect(Chunk chunk,long seed,NoGlassSchematic schematic) {
        if(chunk==null || schematic==null || chunk.getClass()!=Chunk.class) return report(Status.INVALID);
        long bx=(long)chunk.x*16L,bz=(long)chunk.z*16L;
        if(bx < -29999984L || bx+15 > 29999984L || bz < -29999984L || bz+15 > 29999984L)
            return report(Status.INVALID);
        NoGlassPlan.Candidate c=NoGlassPlan.select(seed,chunk.x,chunk.z);
        if(c==null) return report(Status.NOT_SELECTED);
        try {
            if(!chunk.isLoaded() || chunk.unloadQueued) return report(Status.INVALID);
            // Only currently materialized entities/TEs; pending/persisted/claims remain unknown.
            if(chunk.getTileEntityMap()==null || !chunk.getTileEntityMap().isEmpty())
                return report(Status.LOADED_ENTITY_OR_TILE);
            ClassInheritanceMultiMap<Entity>[] lists=chunk.getEntityLists();
            if(lists==null) return report(Status.INVALID);
            for(ClassInheritanceMultiMap<Entity> list:lists)
                if(list==null || !list.isEmpty()) return report(Status.LOADED_ENTITY_OR_TILE);
            final byte[] ids=new byte[16*16*256];
            final byte[] metas=new byte[ids.length];
            // Candidate margins are local coordinates strictly within 0..15. No other
            // chunk is looked up, queried, loaded, or checked.
            for(int z=c.z-2;z<=c.z+9;z++) for(int x=c.x-2;x<=c.x+11;x++) for(int y=0;y<256;y++) {
                if(x<0 || x>15 || z<0 || z>15) return report(Status.INVALID);
                IBlockState s=chunk.getBlockState(x,y,z);
                if(s==null) return report(Status.UNKNOWN_BLOCK);
                Block b=s.getBlock();
                // Identity whitelist: no arbitrary third-party blocks or metadata calls.
                int id;
                if(b==Blocks.AIR) id=0; else if(b==Blocks.STONE) id=1;
                else if(b==Blocks.GRASS) id=2; else if(b==Blocks.DIRT) id=3;
                else if(b==Blocks.GRAVEL) id=13; else if(b==Blocks.IRON_ORE) id=15;
                else return report(Status.UNKNOWN_BLOCK);
                // Reject classes outside the vanilla namespace. This is NOT proof:
                // a mod can define a class there or transform vanilla bytecode.
                if(!b.getClass().getName().startsWith("net.minecraft.block.")) return report(Status.UNKNOWN_BLOCK);
                int meta=b.getMetaFromState(s);
                if(!((id==1 && meta>=0 && meta<=6) || (id!=1 && meta==0)))
                    return report(Status.UNKNOWN_BLOCK);
                int i=(y*16+z)*16+x; ids[i]=(byte)id; metas[i]=(byte)meta;
            }
            NoGlassPlan.Site site=new NoGlassPlan.Site() {
                private int index(int x,int y,int z) {
                    if(x<0 || x>15 || z<0 || z>15 || y<0 || y>255) throw new IllegalArgumentException("out of chunk");
                    return (y*16+z)*16+x;
                }
                public int id(int x,int y,int z) { return ids[index(x,y,z)] & 255; }
                public int meta(int x,int y,int z) { return metas[index(x,y,z)] & 255; }
                public boolean hasTile(int x,int y,int z) { index(x,y,z); return false; }
            };
            List<NoGlassPlan.Cell> plan=NoGlassPlan.plan(site,c,schematic);
            if(plan.size()!=640) return report(Status.TERRAIN_REJECTED);
            // Deliberately does not return the mutable world or a commit handle. In
            // particular this does NOT run the core's gravel/marker/freshness checks.
            return new Report(Status.DIAGNOSTIC_PLAN_ONLY,plan.size());
        } catch(RuntimeException e) {
            return report(Status.READ_FAILED);
        }
    }
}
