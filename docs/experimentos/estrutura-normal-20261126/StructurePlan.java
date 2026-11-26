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
    private static boolean plant(int id) { return id==31 || id==37 || id==38 || id==39 || id==40; }
    private static boolean natural(int id) {
        return id==1 || id==3 || id==7 || id==12 || id==13 || id==14 || id==15 || id==16 || id==21 || id==56 || id==73 || id==74 || id==129;
    }
    /** Must only run synchronously during population of a fresh, never-player-modified chunk. */
    public static boolean apply(Site site, Candidate c, ExactSchematic schematic) {
        if(c==null || schematic==null || c.x<3 || c.z<3 || c.x+5>12 || c.z+5>12) return false;
        // TEST-ONLY heuristic, not proof of a fresh chunk. Do not register in a release.
        // Require the footprint itself to be level, but allow nearby gentle slopes
        // and common harmless plants. All reads remain in this loaded chunk.
        int surface=-1;
        for(int z=0;z<16;z++) for(int x=0;x<16;x++) {
            int y=255;
            while(y>=0 && (site.id(x,y,z)==0 || plant(site.id(x,y,z)))) {
                if(site.hasTile(x,y,z)) return false;
                y--;
            }
            if(y<60 || y>100 || site.id(x,y,z)!=2 || site.meta(x,y,z)!=0 || site.hasTile(x,y,z)) return false;
            boolean footprint=x>=c.x && x<c.x+6 && z>=c.z && z<c.z+6;
            if(footprint) {
                if(surface==-1) surface=y; else if(surface!=y) return false;
            }
            // Reject artificial/unknown materials in a shallow safety band.
            // Deep caves and ore are natural and do not invalidate the site.
            for(int d=y-1;d>=Math.max(1,y-10);d--) {
                int id=site.id(x,d,z);
                if(!natural(id) || (id==3 && site.meta(x,d,z)!=0) || site.hasTile(x,d,z)) return false;
            }
            // A simple grounding check tolerates caves but rejects empty sky.
            int solid=0,total=0;
            for(int d=5;d<y-10;d++) {
                int id=site.id(x,d,z);
                if(id!=0 && !natural(id)) return false;
                if(id!=0) solid++;
                if(site.hasTile(x,d,z)) return false;
                total++;
            }
            if(total==0 || solid*2<total) return false;
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
