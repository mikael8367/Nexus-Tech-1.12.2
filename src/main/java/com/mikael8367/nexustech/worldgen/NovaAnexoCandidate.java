package com.mikael8367.nexustech.worldgen;

import java.util.Random;

/**
 * Offline candidate selection only; no generator registration, World, chunk access,
 * writes, placement, rotation or WE offsets. The returned origin is the minimum
 * corner for NovaPlacement.planWithMargin, for the trusted 11x14x11 ANEXO resource.
 * The caller must separately load/verify that resource and validate any later plan.
 * This class does not inspect schematic blocks, so the 18 lava blocks in the anexo
 * do not count as terrain lava and are not approved for placement by this selector.
 *
 * The immutable input is a previously captured, classified single-chunk snapshot;
 * there are NO callbacks. Unknown data is rejected. AIR must mean proven empty;
 * SOLID must mean proven natural solid ground, never fluid, foliage, logs, structure,
 * tile entity, protected or otherwise unsafe block; everything else is BLOCKED or
 * UNKNOWN. A dishonest/stale/incomplete classifier invalidates the result.
 * A complete conservative reservation map is required: true marks any column with
 * known structure/feature/protection overlap, at any Y. It must include occupancy
 * relevant to the queried chunk, including structures originating elsewhere. It
 * cannot prove absence of structures outside the available information.
 *
 * One seeded attempt per chunk: frequency gate then one horizontal origin. Scan
 * y from highest to lowest; require a configurable depth of SOLID below the full
 * 11x11 footprint, and AIR from the origin through the top of the captured chunk
 * over the footprint AND horizontal margin. Reservations reject that entire area.
 * Thus caves inside the sampled support depth and all obstructions/fluids in the
 * sampled open volume are rejected; deeper caves, later changes and unsampled
 * external features cannot be ruled out. No placement or real-world safety claim.
 */
public final class NovaAnexoCandidate {
    public static final int WIDTH=11, HEIGHT=14, LENGTH=11;
    public static final byte UNKNOWN=0, AIR=1, SOLID=2, BLOCKED=3;
    private static final long SALT=0x56cd4f135ab1290dL;
    private NovaAnexoCandidate() { }

    public static final class Config {
        public final int margin, supportDepth, minY, maxY, frequencyDivisor;
        /** One in frequencyDivisor chunks passes the theoretical random gate; not a placement rate. */
        public Config(int margin,int supportDepth,int minY,int maxY,int frequencyDivisor) {
            if(margin<0 || margin>2 || supportDepth<1 || supportDepth>256 ||
               minY<supportDepth || maxY<minY || maxY>256-HEIGHT || frequencyDivisor<1)
                throw new IllegalArgumentException("invalid configuration");
            this.margin=margin; this.supportDepth=supportDepth; this.minY=minY;
            this.maxY=maxY; this.frequencyDivisor=frequencyDivisor;
        }
        public static Config conservativeDefaults() { return new Config(1,3,4,242,4); }
    }

    public static final class Snapshot {
        public final int chunkX, chunkZ, height;
        private final byte[] cells;
        private final boolean[] reserved;
        private final boolean reservationsComplete;
        /** cells indexed (y*16+z)*16+x, x/z local to this chunk, y in [0,height). */
        public Snapshot(int chunkX,int chunkZ,int height,byte[] cells,boolean[] reserved,boolean reservationsComplete) {
            if(height<1 || height>256 || cells==null || cells.length!=height*256 ||
               reserved==null || reserved.length!=256) throw new IllegalArgumentException("invalid snapshot");
            this.chunkX=chunkX; this.chunkZ=chunkZ; this.height=height;
            this.cells=cells.clone(); this.reserved=reserved.clone();
            this.reservationsComplete=reservationsComplete;
            for(byte cell:this.cells) if(cell<UNKNOWN || cell>BLOCKED) throw new IllegalArgumentException("invalid cell classification");
        }
        private byte at(int x,int y,int z) { return cells[(y*16+z)*16+x]; }
    }

    public static final class Candidate {
        public final int x,y,z,chunkX,chunkZ,margin;
        private Candidate(int x,int y,int z,int cx,int cz,int margin) {
            this.x=x; this.y=y; this.z=z; chunkX=cx; chunkZ=cz; this.margin=margin;
        }
    }

    /** Null means no candidate. No retries or neighbour access. */
    public static Candidate select(long seed,Snapshot snapshot,Config config) {
        if(snapshot==null || config==null) throw new IllegalArgumentException("null input");
        long bx=(long)snapshot.chunkX*16, bz=(long)snapshot.chunkZ*16;
        if(bx<Integer.MIN_VALUE || bx+15>Integer.MAX_VALUE || bz<Integer.MIN_VALUE || bz+15>Integer.MAX_VALUE ||
           !snapshot.reservationsComplete || config.minY+HEIGHT>snapshot.height) return null;
        long mixed=mix(seed ^ SALT ^ mix((long)snapshot.chunkX * 0x9e3779b97f4a7c15L) ^
                       Long.rotateLeft(mix((long)snapshot.chunkZ * 0xc2b2ae3d27d4eb4fL), 27));
        Random random=new Random(mixed);
        if(random.nextInt(config.frequencyDivisor)!=0) return null;
        int choices=16-WIDTH-2*config.margin+1;
        int x=config.margin+random.nextInt(choices), z=config.margin+random.nextInt(choices);
        int fromX=x-config.margin, toX=x+WIDTH-1+config.margin;
        int fromZ=z-config.margin, toZ=z+LENGTH-1+config.margin;
        for(int lz=fromZ;lz<=toZ;lz++) for(int lx=fromX;lx<=toX;lx++)
            if(snapshot.reserved[lz*16+lx]) return null;
        int top=Math.min(config.maxY,snapshot.height-HEIGHT);
        for(int y=top;y>=config.minY;y--) {
            boolean valid=true;
            for(int lz=fromZ;lz<=toZ && valid;lz++) for(int lx=fromX;lx<=toX && valid;lx++) {
                for(int yy=y;yy<snapshot.height;yy++) if(snapshot.at(lx,yy,lz)!=AIR) { valid=false; break; }
                if(valid && lx>=x && lx<x+WIDTH && lz>=z && lz<z+LENGTH)
                    for(int yy=y-config.supportDepth;yy<y;yy++) if(snapshot.at(lx,yy,lz)!=SOLID) { valid=false; break; }
            }
            if(valid) return new Candidate((int)(bx+x),y,(int)(bz+z),snapshot.chunkX,snapshot.chunkZ,config.margin);
        }
        return null;
    }
    private static long mix(long x) {
        x=(x^(x>>>30))*0xbf58476d1ce4e5b9L;
        x=(x^(x>>>27))*0x94d049bb133111ebL;
        return x^(x>>>31);
    }
}
