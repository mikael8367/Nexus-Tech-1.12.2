package com.mikael8367.nexustech.worldgen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Offline-only planner. Does not implement IWorldGenerator or mutate any world. */
public final class NoGlassPlan {
    private NoGlassPlan() {}
    /** Read-only; implementation MUST NOT load or generate chunks. Local x/z only. */
    public interface Site {
        int id(int x, int y, int z);
        int meta(int x, int y, int z);
        boolean hasTile(int x, int y, int z);
    }
    public static final class Candidate {
        public final int x, z;
        private Candidate(int x, int z) { this.x=x; this.z=z; }
    }
    public static final class Cell {
        public final int x,y,z,id,meta;
        private Cell(int x,int y,int z,int id,int meta) {this.x=x;this.y=y;this.z=z;this.id=id;this.meta=meta;}
    }
    /** Stable roughly 1/16 chunks, independent of caller RNG; not an in-game rate. */
    public static Candidate select(long seed,int cx,int cz) {
        long n=seed ^ ((long)cx*0x9E3779B97F4A7C15L) ^ ((long)cz*0xC2B2AE3D27D4EB4FL) ^ 0x72A94D8306E5B1C9L;
        n=(n^(n>>>30))*0xbf58476d1ce4e5b9L;
        n=(n^(n>>>27))*0x94d049bb133111ebL;
        Random r=new Random(n^(n>>>31));
        if(r.nextInt(16)!=0) return null;
        return new Candidate(3,3+r.nextInt(3)); // 10x8 occupies local 3..12 with one+ block margin
    }
    /** Empty list means rejected; no writes occur. A nonempty list is NOT permission to place. */
    public static List<Cell> plan(Site site,Candidate c,NoGlassSchematic schematic) {
        if(site==null || c==null || schematic==null || c.x<3 || c.x+9>12 || c.z<3 || c.z+7>12) return Collections.emptyList();
        int surface=-1;
        for(int z=0;z<8;z++) for(int x=0;x<10;x++) {
            int px=c.x+x,pz=c.z+z,y=255;
            while(y>=0 && site.id(px,y,pz)==0 && site.meta(px,y,pz)==0 && !site.hasTile(px,y,pz)) y--;
            if(y<3 || y>250 || site.id(px,y,pz)!=2 || site.meta(px,y,pz)!=0 || site.hasTile(px,y,pz)) return Collections.emptyList();
            if(surface<0) surface=y; else if(surface!=y) return Collections.emptyList();
            for(int dy=-3;dy<0;dy++) {
                int id=site.id(px,y+dy,pz),meta=site.meta(px,y+dy,pz);
                if(!((id==3 || id==1) && meta==0) || site.hasTile(px,y+dy,pz)) return Collections.emptyList();
            }
        }
        // All cells, including air; lower Y before upper Y. No write/placement API exists.
        List<Cell> result=new ArrayList<Cell>(640);
        for(int y=0;y<8;y++) for(int z=0;z<8;z++) for(int x=0;x<10;x++)
            result.add(new Cell(c.x+x,surface-2+y,c.z+z,schematic.id(x,y,z),schematic.meta(x,y,z)));
        return Collections.unmodifiableList(result);
    }
}
