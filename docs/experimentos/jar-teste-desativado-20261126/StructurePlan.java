package com.mikael8367.nexustech.worldgen;

import java.util.Random;

/** Experimental pure planner. Coordinates passed to Site are chunk-local. */
public final class StructurePlan {
    private StructurePlan() {}
    public interface Site {
        int id(int localX,int y,int localZ);
        int meta(int localX,int y,int localZ);
        boolean hasTile(int localX,int y,int localZ);
        void set(int localX,int y,int localZ,int id,int meta);
    }
    public static final class Candidate {
        public final int x,z;
        private Candidate(int x,int z) { this.x=x; this.z=z; }
    }
    /** Stable, independent of population random state; approximately 1/16 candidate chunks. */
    public static Candidate select(long seed,int chunkX,int chunkZ) {
        long n=seed ^ ((long)chunkX*0x9E3779B97F4A7C15L) ^ ((long)chunkZ*0xC2B2AE3D27D4EB4FL) ^ 0x72A94D8306E5B1C9L;
        n=(n^(n>>>30))*0xbf58476d1ce4e5b9L;
        n=(n^(n>>>27))*0x94d049bb133111ebL;
        Random r=new Random(n^(n>>>31));
        if(r.nextInt(16)!=0) return null;
        // Positions 3..12 inclusive; even direct adjacent state reads stay local.
        return new Candidate(3+r.nextInt(5),3+r.nextInt(5));
    }
    /** Must only run synchronously during population of a fresh, never-player-modified chunk. */
    public static boolean apply(Site site, Candidate c, ExactSchematic schematic) {
        if(c==null || schematic==null || c.x<3 || c.z<3 || c.x+5>12 || c.z+5>12) return false;
        // Deliberately restrictive: inspect EVERY column of the current chunk, not
        // just the 6x6 footprint. No neighboring chunk may be read or loaded.
        int surface=-1;
        for(int z=0;z<16;z++) for(int x=0;x<16;x++) {
            int y=255;
            while(y>=0 && site.id(x,y,z)==0 && !site.hasTile(x,y,z)) y--;
            // Surface elevation is a heuristic, not proof of natural terrain.
            if(y<63 || y>100 || site.id(x,y,z)!=2 || site.meta(x,y,z)!=0 || site.hasTile(x,y,z)) return false;
            if(surface==-1) surface=y; else if(surface!=y) return false;
            // A continuous, narrowly whitelisted column to bedrock rejects open
            // caves, water, floating platforms, buried structures and unknowns.
            for(int depth=y-1;depth>=0;depth--) {
                int id=site.id(x,depth,z), meta=site.meta(x,depth,z);
                boolean allowed=depth==0 ? id==7 :
                        (id==1 || id==3 || (id==7 && depth<=4));
                if(!allowed || meta!=0 || site.hasTile(x,depth,z)) return false;
            }
        }
        // Full preflight precedes all writes. Still not an atomic world transaction.
        for(int y=0;y<5;y++) for(int z=0;z<6;z++) for(int x=0;x<6;x++) {
            int px=c.x+x,py=surface-2+y,pz=c.z+z;
            int id=schematic.id(x,y,z),meta=schematic.meta(x,y,z);
            if(site.id(px,py,pz)!=id || site.meta(px,py,pz)!=meta) site.set(px,py,pz,id,meta);
        }
        return true;
    }
}
