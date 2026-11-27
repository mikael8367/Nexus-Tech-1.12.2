package com.mikael8367.nexustech.worldgen;

import java.util.Arrays;
import com.mikael8367.nexustech.worldgen.Fazenda5Candidate.Cell;
import com.mikael8367.nexustech.worldgen.Fazenda5Candidate.Source;

public final class Fazenda5CandidateTest {
    static int checks;
    static void yes(boolean v, String m) { checks++; if (!v) throw new AssertionError(m); }
    static void fail(Runnable r) { checks++; try {r.run();} catch (IllegalArgumentException ex) {return;} throw new AssertionError("expected invalid input"); }
    static int idx(int x,int y,int z,int minY) { return ((y-minY)*16+z)*16+x; }
    static Cell[] cells(int minY,int maxY,int floor) {
        Cell[] a=new Cell[(maxY-minY+1)*256];
        for(int y=minY;y<=maxY;y++) for(int z=0;z<16;z++) for(int x=0;x<16;x++)
            a[idx(x,y,z,minY)]= y<floor ? Cell.NATURAL_SOLID : Cell.AIR;
        return a;
    }
    static Fazenda5Candidate.Snapshot snap(int cx,int cz,int min,int max,Source src,Cell[] a,boolean[] r) {
        return new Fazenda5Candidate.Snapshot(cx,cz,min,max,src,a,r);
    }
    static Fazenda5Candidate.Result go(Fazenda5Candidate.Snapshot s,long seed,int floor,int min,int max,int d) {
        return Fazenda5Candidate.select(s,seed,floor,min,max,d);
    }
    static void reason(Fazenda5Candidate.Result r,Fazenda5Candidate.Rejection wanted) {yes(r.rejection==wanted,"wanted "+wanted+" got "+r.rejection);}
    public static void main(String[] args) {
        Cell[] a=cells(60,63,62); boolean[] r=new boolean[a.length];
        Fazenda5Candidate.Snapshot flat=snap(-3,4,60,63,Source.CONFIRMED_LOADED_CHUNK,a,r);
        Fazenda5Candidate.Result first=go(flat,123,62,2,254,1);
        yes(first.accepted(),"flat accepted");
        int ox=first.x-(-3*16), oz=first.z-(4*16);
        yes(ox>=1 && ox<=10 && oz>=1 && oz<=10,"margin");
        int gate=0; boolean[][] seen=new boolean[10][10];
        for (int seed=0;seed<10000;seed++) {
            Fazenda5Candidate.Result t=go(flat,seed,62,2,254,1), u=go(flat,seed,62,2,254,1);
            yes(t.accepted() && t.x==u.x && t.y==u.y && t.z==u.z,"determinism");
            int x=t.x+48,z=t.z-64; yes(x>=1 && x<=10 && z>=1 && z<=10,"chunk margin"); seen[x-1][z-1]=true;
            if (go(flat,seed,62,2,254,16).accepted()) gate++;
        }
        int positions=0; for(boolean[] row:seen) for(boolean b:row) if(b) positions++;
        yes(positions==100,"reachable positions"); yes(gate>450 && gate<800,"frequency sanity: "+gate);
        // Every one of the 100 sampled cells, and all unsuitable terrain types.
        Cell[] bad={Cell.AIR,Cell.WATER,Cell.OBSTACLE,Cell.UNKNOWN};
        for(int y=60;y<=63;y++) for(int z=oz;z<oz+5;z++) for(int x=ox;x<ox+5;x++) {
            Cell expected=y<62?Cell.NATURAL_SOLID:Cell.AIR;
            for(Cell c:Cell.values()) if(c!=expected) {
                Cell[] mod=a.clone(); mod[idx(x,y,z,60)]=c;
                reason(go(snap(-3,4,60,63,Source.CONFIRMED_LOADED_CHUNK,mod,r),123,62,2,254,1),Fazenda5Candidate.Rejection.TERRAIN);
            }
            boolean[] reserved=r.clone(); reserved[idx(x,y,z,60)]=true;
            reason(go(snap(-3,4,60,63,Source.CONFIRMED_LOADED_CHUNK,a,reserved),123,62,2,254,1),Fazenda5Candidate.Rejection.TERRAIN);
        }
        // Schematic water is not ground water; this test only passes terrain AIR in the full volume.
        Cell[] detached=a.clone(); boolean[] flags=r.clone();
        Fazenda5Candidate.Snapshot copied=snap(-3,4,60,63,Source.CONFIRMED_LOADED_CHUNK,detached,flags);
        Arrays.fill(detached,Cell.WATER); Arrays.fill(flags,true);
        yes(go(copied,123,62,2,254,1).accepted(),"snapshot defensive copy");
        reason(go(snap(-3,4,60,63,Source.UNCONFIRMED,a,r),123,62,2,254,1),Fazenda5Candidate.Rejection.UNSAFE_SOURCE);
        reason(go(null,1,62,2,254,1),Fazenda5Candidate.Rejection.INVALID_CONFIG);
        for(int d:new int[]{Integer.MIN_VALUE,-1,0,1000001,Integer.MAX_VALUE}) reason(go(flat,0,62,2,254,d),Fazenda5Candidate.Rejection.INVALID_CONFIG);
        reason(go(flat,0,62,1,254,1),Fazenda5Candidate.Rejection.INVALID_CONFIG);
        reason(go(flat,0,62,2,255,1),Fazenda5Candidate.Rejection.INVALID_CONFIG);
        reason(go(flat,0,62,100,99,1),Fazenda5Candidate.Rejection.INVALID_CONFIG);
        for(int y:new int[]{Integer.MIN_VALUE,-1,0,1,61,63,255,256,Integer.MAX_VALUE}) reason(go(flat,0,y,2,254,1),Fazenda5Candidate.Rejection.HEIGHT_BOUNDS);
        Cell[] low=cells(0,3,2), high=cells(252,255,254);
        yes(go(snap(0,0,0,3,Source.CONFIRMED_LOADED_CHUNK,low,new boolean[low.length]),0,2,2,254,1).accepted(),"min height");
        yes(go(snap(0,0,252,255,Source.CONFIRMED_LOADED_CHUNK,high,new boolean[high.length]),0,254,2,254,1).accepted(),"max height");
        for(int c:new int[]{-134217728,134217727}) {
            Fazenda5Candidate.Result e=go(snap(c,c,60,63,Source.CONFIRMED_LOADED_CHUNK,a,r),0,62,2,254,1);
            yes(e.accepted(),"boundary chunk"); long bx=(long)c*16; yes(e.x>bx && e.x+4L<bx+15,"boundary x");
        }
        for(int c:new int[]{Integer.MIN_VALUE,-134217729,134217728,Integer.MAX_VALUE}) {
            reason(go(snap(c,0,60,63,Source.CONFIRMED_LOADED_CHUNK,a,r),0,62,2,254,1),Fazenda5Candidate.Rejection.CHUNK_BOUNDS);
            reason(go(snap(0,c,60,63,Source.CONFIRMED_LOADED_CHUNK,a,r),0,62,2,254,1),Fazenda5Candidate.Rejection.CHUNK_BOUNDS);
        }
        fail(()->snap(0,0,-1,3,Source.CONFIRMED_LOADED_CHUNK,a,r));
        fail(()->snap(0,0,0,256,Source.CONFIRMED_LOADED_CHUNK,a,r));
        fail(()->snap(0,0,3,2,Source.CONFIRMED_LOADED_CHUNK,a,r));
        fail(()->snap(0,0,60,63,null,a,r)); fail(()->snap(0,0,60,63,Source.CONFIRMED_LOADED_CHUNK,null,r));
        fail(()->snap(0,0,60,63,Source.CONFIRMED_LOADED_CHUNK,a,null));
        fail(()->snap(0,0,60,63,Source.CONFIRMED_LOADED_CHUNK,new Cell[0],r));
        fail(()->snap(0,0,60,63,Source.CONFIRMED_LOADED_CHUNK,a,new boolean[0]));
        Cell[] n=a.clone(); n[0]=null; fail(()->snap(0,0,60,63,Source.CONFIRMED_LOADED_CHUNK,n,r));
        System.out.println("PASS checks="+checks+" seeds=10000 positions="+positions+" rarity16="+gate+"; Java="+System.getProperty("java.version"));
    }
}
