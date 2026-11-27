package com.mikael8367.nexustech.worldgen;

import java.util.Arrays;
import java.util.Random;

/** Offline 5x2x5 site selection only. No World access, loading, placement or registration.
 * Air in the schematic is NOT used as terrain data; every check uses the snapshot.
 * Capture must be an atomic, authoritative, already-loaded chunk snapshot with a
 * complete protection mask; the caller must attest this. This class cannot verify
 * the attestation, detect protected areas outside the sampled cells, or guarantee
 * the state stays unchanged between this check and any future placement.
 */
public final class Fazenda5Candidate {
    private Fazenda5Candidate() {}
    public static final int WIDTH = 5, HEIGHT = 2, LENGTH = 5;
    public static final int CHUNK = 16, MAX_HEIGHT = 256;
    // 1.12.2 World horizontal BlockPos validity: -30,000,000 inclusive to +30,000,000 exclusive.
    public static final int WORLD_LIMIT = 30000000;
    public enum Cell { AIR, NATURAL_SOLID, WATER, OBSTACLE, UNKNOWN }
    public enum Source { CONFIRMED_LOADED_CHUNK, UNCONFIRMED }
    public enum Rejection { NONE, INVALID_CONFIG, UNSAFE_SOURCE, CHUNK_BOUNDS, HEIGHT_BOUNDS, RARITY, TERRAIN, UNKNOWN_BORDER, BORDER_BOUNDS }
    /** Caller-attested current effective border faces, continuous coordinates. Not a World query. */
    public static final class Border {
        public final double minX, minZ, maxX, maxZ;
        public Border(double minX, double minZ, double maxX, double maxZ) {
            if (!finite(minX) || !finite(minZ) || !finite(maxX) || !finite(maxZ)
                    || minX >= maxX || minZ >= maxZ)
                throw new IllegalArgumentException("invalid border");
            this.minX=minX; this.minZ=minZ; this.maxX=maxX; this.maxZ=maxZ;
        }
        private static boolean finite(double d) { return !Double.isNaN(d) && !Double.isInfinite(d); }
        // Entire block volumes plus one-block horizontal margin must fit inside all faces.
        private boolean containsMargin(long x, long z) {
            return x-1L >= minX && x+WIDTH+1L <= maxX
                    && z-1L >= minZ && z+LENGTH+1L <= maxZ;
        }
    }
    public static final class Result {
        public final Rejection rejection;
        public final int x, y, z;
        private Result(Rejection reason, int x, int y, int z) {
            this.rejection = reason; this.x = x; this.y = y; this.z = z;
        }
        public boolean accepted() { return rejection == Rejection.NONE; }
    }
    /** Immutable: exactly the full chunk x/z and y range, defensive copies.
     * Index = ((y-minY)*16 + localZ)*16 + localX.
     * Protection bits must be complete for the same bounds, not merely known claims.
     */
    public static final class Snapshot {
        private final int chunkX, chunkZ, minY, maxY;
        private final Source source;
        private final Cell[] cells;
        private final boolean[] reserved;
        public Snapshot(int chunkX, int chunkZ, int minY, int maxY,
                        Source source, Cell[] cells, boolean[] reserved) {
            if (minY < 0 || maxY >= MAX_HEIGHT || minY > maxY || source == null || cells == null || reserved == null)
                throw new IllegalArgumentException("invalid snapshot bounds or input");
            int n = (maxY-minY+1)*CHUNK*CHUNK;
            if (cells.length != n || reserved.length != n) throw new IllegalArgumentException("incomplete snapshot");
            Cell[] copy = cells.clone();
            for (Cell c : copy) if (c == null) throw new IllegalArgumentException("unknown cell: use UNKNOWN");
            this.chunkX=chunkX; this.chunkZ=chunkZ; this.minY=minY; this.maxY=maxY;
            this.source=source; this.cells=copy; this.reserved=reserved.clone();
        }
        private int index(int localX, int y, int localZ) {
            if (localX < 0 || localX >= CHUNK || localZ < 0 || localZ >= CHUNK || y < minY || y > maxY)
                throw new IllegalArgumentException("outside snapshot");
            return ((y-minY)*CHUNK+localZ)*CHUNK+localX;
        }
        private boolean is(int localX, int y, int localZ, Cell expected) {
            int i=index(localX,y,localZ); return !reserved[i] && cells[i] == expected;
        }
    }
    /** One attempt per chunk: divisor is an independent uniform 1/divisor gate;
     * local origins are 1..10 inclusive (5-wide plus 1 margin). y is supplied,
     * not searched. Check y-2/y-1 for solid support beneath ALL 25 columns,
     * including schematic-air columns: intentionally stricter than necessary.
     * Rechecking current state and locking placement are future integration work.
     */
    /** Legacy callers without a border cannot be accepted. */
    public static Result select(Snapshot s, long worldSeed, int floorY, int minFloorY, int maxFloorY, int divisor) {
        return select(s, worldSeed, floorY, minFloorY, maxFloorY, divisor, null);
    }
    public static Result select(Snapshot s, long worldSeed, int floorY, int minFloorY, int maxFloorY, int divisor, Border border) {
        if (s == null || divisor < 1 || divisor > 1000000 || minFloorY < 2 || maxFloorY > 254 || minFloorY > maxFloorY)
            return no(Rejection.INVALID_CONFIG);
        if (s.source != Source.CONFIRMED_LOADED_CHUNK) return no(Rejection.UNSAFE_SOURCE);
        if (border == null) return no(Rejection.UNKNOWN_BORDER);
        long baseX=(long)s.chunkX*16L, baseZ=(long)s.chunkZ*16L;
        // Entire chunk, including margin, must fit the vanilla horizontal coordinate range.
        if (baseX < -WORLD_LIMIT || baseX+15L >= WORLD_LIMIT || baseZ < -WORLD_LIMIT || baseZ+15L >= WORLD_LIMIT)
            return no(Rejection.CHUNK_BOUNDS);
        if (floorY < minFloorY || floorY > maxFloorY || floorY-2 < s.minY || floorY+1 > s.maxY)
            return no(Rejection.HEIGHT_BOUNDS);
        // Intentional 64-bit modular arithmetic for stable seed mixing.
        long h = mix(worldSeed ^ mix(((long)s.chunkX << 32) ^ (s.chunkZ & 0xffffffffL)) ^ 0x7f4a7c159e3779b9L);
        Random rng=new Random(h);
        if (rng.nextInt(divisor) != 0) return no(Rejection.RARITY);
        int lx=1+rng.nextInt(10), lz=1+rng.nextInt(10);
        if (!border.containsMargin(baseX+lx, baseZ+lz)) return no(Rejection.BORDER_BOUNDS);
        for (int dz=0; dz<LENGTH; dz++) for (int dx=0; dx<WIDTH; dx++) {
            int x=lx+dx, z=lz+dz;
            if (!s.is(x,floorY-2,z,Cell.NATURAL_SOLID) || !s.is(x,floorY-1,z,Cell.NATURAL_SOLID)
                    || !s.is(x,floorY,z,Cell.AIR) || !s.is(x,floorY+1,z,Cell.AIR)) return no(Rejection.TERRAIN);
        }
        return new Result(Rejection.NONE,(int)(baseX+lx),floorY,(int)(baseZ+lz));
    }
    private static Result no(Rejection why) { return new Result(why,0,0,0); }
    private static long mix(long v) {
        v ^= v >>> 30; v *= 0xbf58476d1ce4e5b9L;
        v ^= v >>> 27; v *= 0x94d049bb133111ebL;
        return v ^ (v >>> 31);
    }
}
