package com.mikael8367.nexustech.worldgen;

import java.io.*;
import java.util.*;

/** EXPERIMENT ONLY: no Forge registration or production backend. See README. */
public final class NoGlassTransactionalGenerator {
    public enum Result { NOT_SELECTED, REJECTED, COMMITTED, CONFLICT, ERROR }
    /** All coordinates are chunk-local. Snapshot must be a detached immutable view.
     * acquisition MUST NOT load, generate, or touch any other chunk.
     * freshUnmodified must be proven by a trustworthy generation lifecycle, not inferred
     * from natural blocks, absent markers, inhabitedTime or a newly-loaded chunk. */
    public interface Snapshot extends NoGlassPlan.Site {
        boolean freshUnmodified();
        boolean hasAnyEntityOrTile(); // includes persisted/pending entities, not only active TEs
        boolean insideWorldBorder(); // entire chunk, now and at commit
        byte[] marker(); // null only when definitely absent
    }
    /** An exclusively owned reservation, released exactly once by attempt after acquisition.
     * snapshot() is detached and immutable. close() must release all resources, including after
     * successful commit, conflict or exception; it must not itself commit or undo a commit.
     * A close failure is propagated, including after a potentially successful commit. */
    public interface Reservation extends AutoCloseable {
        Snapshot snapshot();
        @Override void close();
    }
    /** REQUIRED contract only, NOT an implementation or proof of Forge atomicity:
     * acquire must touch no other chunk; a null return means no reservation was retained;
     * if acquire throws it must clean up any resources it acquired before throwing.
     * compareAndCommit must atomically compare ALL snapshot data, freshness, entity/TE state,
     * loaded identity, border and marker, then write ALL cells and marker as ONE durable unit.
     * On false or exception, no effects anywhere; no chunk loads, callbacks, physics, lighting,
     * neighbours or observable writes until commit. The reservation remains held through commit.
     * A dishonest or broken backend can violate these requirements. If they cannot be proven,
     * never instantiate/register this generator. */
    public interface Backend {
        Reservation acquireAlreadyLoadedFreshChunk(int cx,int cz);
        boolean compareAndCommit(int cx,int cz,Snapshot expected,List<NoGlassPlan.Cell> plan,byte[] marker);
    }
    private final NoGlassSchematic schematic;
    public NoGlassTransactionalGenerator(NoGlassSchematic schematic) {
        if(schematic==null) throw new IllegalArgumentException("missing asset");
        this.schematic=schematic;
    }
    public Result attempt(Backend backend,long seed,int cx,int cz) {
        if(backend==null) return Result.REJECTED;
        // World border and overflow: block coordinates remain strictly within +/-30,000,000.
        long bx=(long)cx*16, bz=(long)cz*16;
        if(bx < -29999984L || bx+15 > 29999984L || bz < -29999984L || bz+15 > 29999984L) return Result.REJECTED;
        NoGlassPlan.Candidate candidate=NoGlassPlan.select(seed,cx,cz);
        if(candidate==null) return Result.NOT_SELECTED;
        // Java try-with-resources uses finally and preserves a primary exception, adding a
        // close exception as suppressed. Acquisition failures are the backend's responsibility.
        try (Reservation reservation=backend.acquireAlreadyLoadedFreshChunk(cx,cz)) {
            if(reservation==null) return Result.REJECTED;
            Snapshot s=reservation.snapshot();
            if(s==null || !s.freshUnmodified() || !s.insideWorldBorder() || s.hasAnyEntityOrTile() || s.marker()!=null) return Result.REJECTED;
            // Check a margin on all sides, all y. No unknown blocks or metadata, TE, liquids,
            // vegetation, structures, rails etc. No assumption that natural blocks prove freshness.
            for(int z=candidate.z-2;z<=candidate.z+9;z++) for(int x=candidate.x-2;x<=candidate.x+11;x++) for(int y=0;y<256;y++) {
                int id=s.id(x,y,z), m=s.meta(x,y,z);
                if(s.hasTile(x,y,z) || !natural(id,m)) return Result.REJECTED;
            }
            List<NoGlassPlan.Cell> plan=NoGlassPlan.plan(s,candidate,schematic);
            if(plan.size()!=640 || !stableGravel(plan,s)) return Result.REJECTED;
            byte[] marker=Marker.encode(seed,cx,cz);
            return backend.compareAndCommit(cx,cz,s,plan,marker) ? Result.COMMITTED : Result.CONFLICT;
        }
    }
    private static boolean natural(int id,int m) {
        return (id==0 && m==0) || (id==1 && m>=0 && m<=6) ||
               ((id==2 || id==3 || id==13 || id==15) && m==0);
    }
    private static boolean stableGravel(List<NoGlassPlan.Cell> cells,Snapshot s) {
        Map<Integer,NoGlassPlan.Cell> byPosition=new HashMap<Integer,NoGlassPlan.Cell>();
        for(NoGlassPlan.Cell c:cells) byPosition.put((c.y*16+c.z)*16+c.x,c);
        for(NoGlassPlan.Cell c:cells) if(c.id==13) {
            if(c.y<=0) return false;
            NoGlassPlan.Cell below=byPosition.get(((c.y-1)*16+c.z)*16+c.x);
            int id=below==null?s.id(c.x,c.y-1,c.z):below.id;
            if(id!=1 && id!=2 && id!=3 && id!=13 && id!=15) return false;
        }
        return true;
    }
    /** Minimal uncompressed NBT compound: strict version, seed, chunk coordinates and asset ID. */
    public static final class Marker {
        private static final String ASSET="5333f61ae7f814e0632e03909b219af91e89480b8b78adaabbb9db35b4cdaf22";
        public static byte[] encode(long seed,int cx,int cz) {
            try {
                ByteArrayOutputStream b=new ByteArrayOutputStream(); DataOutputStream d=new DataOutputStream(b);
                d.writeByte(10); d.writeUTF("NexusNoGlass");
                d.writeByte(3); d.writeUTF("Version"); d.writeInt(1);
                d.writeByte(4); d.writeUTF("Seed"); d.writeLong(seed);
                d.writeByte(3); d.writeUTF("ChunkX"); d.writeInt(cx);
                d.writeByte(3); d.writeUTF("ChunkZ"); d.writeInt(cz);
                d.writeByte(8); d.writeUTF("AssetSHA256"); d.writeUTF(ASSET);
                d.writeByte(0); d.flush(); return b.toByteArray();
            } catch(IOException e) { throw new IllegalStateException(e); }
        }
        /** Strict canonical marker validation; malformed/unknown state must never mean fresh. */
        public static boolean matches(byte[] nbt,long seed,int cx,int cz) {
            return nbt!=null && Arrays.equals(nbt,encode(seed,cx,cz));
        }
    }
}
