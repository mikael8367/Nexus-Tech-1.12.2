package com.mikael8367.nexustech.worldgen;

import com.mikael8367.nexustech.worldgen.Fazenda5Candidate.*;

/** Offline adversarial border contract; never loads Minecraft. */
public final class Fazenda5BorderTest {
    static int checks;
    static void ok(boolean b, String msg) { checks++; if (!b) throw new AssertionError(msg); }
    static void invalid(double a, double b, double c, double d) {
        checks++; try { new Border(a,b,c,d); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("invalid border accepted");
    }
    static Snapshot snapshot(int x,int z) {
        Cell[] cells = Fazenda5CandidateTest.cells(60,63,62);
        return new Snapshot(x,z,60,63,Source.CONFIRMED_LOADED_CHUNK,cells,new boolean[cells.length]);
    }
    static Result select(Snapshot s,long seed,Border b) {
        return Fazenda5Candidate.select(s,seed,62,2,254,1,b);
    }
    static void rejected(Result r,Rejection why) { ok(r.rejection==why,"expected "+why+" got "+r.rejection); }
    public static void main(String[] args) {
        Snapshot s=snapshot(0,0);
        rejected(Fazenda5Candidate.select(s,42,62,2,254,1),Rejection.UNKNOWN_BORDER);
        rejected(select(s,42,null),Rejection.UNKNOWN_BORDER);
        for (double v: new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY}) {
            invalid(v,0,10,10); invalid(0,v,10,10); invalid(0,0,v,10); invalid(0,0,10,v);
        }
        invalid(0,0,0,10); invalid(1,0,0,10); invalid(0,0,10,0); invalid(0,1,10,0);
        Border global=new Border(-30000000,-30000000,30000000,30000000);
        Border vanilla=new Border(-29999984,-29999984,29999984,29999984);
        for (int c: new int[]{Integer.MIN_VALUE,-134217729,-1875001,1875000,134217728,Integer.MAX_VALUE}) {
            rejected(select(snapshot(c,0),42,global),Rejection.CHUNK_BOUNDS);
            rejected(select(snapshot(0,c),42,global),Rejection.CHUNK_BOUNDS);
        }
        // Default border is separate from legal BlockPos bounds: both signs, both axes.
        for (int c: new int[]{-1875000,1874999}) {
            rejected(select(snapshot(c,0),42,vanilla),Rejection.BORDER_BOUNDS);
            rejected(select(snapshot(0,c),42,vanilla),Rejection.BORDER_BOUNDS);
            ok(select(snapshot(c,0),42,global).accepted(),"legal x chunk");
            ok(select(snapshot(0,c),42,global).accepted(),"legal z chunk");
        }
        for (int c: new int[]{-1874999,1874998}) {
            ok(select(snapshot(c,0),42,vanilla).accepted(),"adjacent x default border");
            ok(select(snapshot(0,c),42,vanilla).accepted(),"adjacent z default border");
        }
        int partialPass=0, partialReject=0;
        Border partial=new Border(4.5,3.5,12.5,13.5);
        for (long seed=0; seed<10000; seed++) {
            Result base=select(s,seed,global); ok(base.accepted(),"base accepted");
            double x=base.x, z=base.z;
            // Exact fit at all four margin faces is safe; epsilon inside clips a face.
            ok(select(s,seed,new Border(x-1,z-1,x+6,z+6)).accepted(),"exact fit");
            rejected(select(s,seed,new Border(x-0.999,z-1,x+6,z+6)),Rejection.BORDER_BOUNDS);
            rejected(select(s,seed,new Border(x-1,z-0.999,x+6,z+6)),Rejection.BORDER_BOUNDS);
            rejected(select(s,seed,new Border(x-1,z-1,x+5.999,z+6)),Rejection.BORDER_BOUNDS);
            rejected(select(s,seed,new Border(x-1,z-1,x+6,z+5.999)),Rejection.BORDER_BOUNDS);
            Result p=select(s,seed,partial);
            boolean expected=x-1>=4.5 && z-1>=3.5 && x+6<=12.5 && z+6<=13.5;
            ok(p.accepted()==expected,"partial border seed="+seed);
            if (expected) partialPass++; else { partialReject++; rejected(p,Rejection.BORDER_BOUNDS); }
        }
        ok(partialPass>0 && partialReject>0,"partial crosses candidates");
        for (long seed: new long[]{Long.MIN_VALUE,-1,Long.MAX_VALUE}) ok(select(s,seed,global).accepted(),"extreme seed");
        System.out.println("PASS border checks="+checks+" seeds=10000 partialPass="+partialPass+" partialReject="+partialReject+"; Java="+System.getProperty("java.version"));
    }
}
