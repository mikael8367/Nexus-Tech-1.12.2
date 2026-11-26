package com.mikael8367.nexustech.worldgen;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class NoGlassPlanTest {
    private static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
    static final class Ground implements NoGlassPlan.Site {
        int level=64, badX=-1,badY=-1,badZ=-1,badId=-1,badMeta=0; boolean tile=false;
        public int id(int x,int y,int z){check(x>=3&&x<=12&&z>=3&&z<=12&&y>=0&&y<=255,"out of bounds read"); if(x==badX&&y==badY&&z==badZ)return badId; return y>level?0:y==level?2:3;}
        public int meta(int x,int y,int z){return x==badX&&y==badY&&z==badZ?badMeta:0;}
        public boolean hasTile(int x,int y,int z){return tile&&x==badX&&y==badY&&z==badZ;}
    }
    public static void main(String[] args) throws Exception {
        byte[] raw=Base64.getMimeDecoder().decode(Files.readAllBytes(Paths.get(args[0])));
        NoGlassSchematic s=NoGlassSchematic.load(new ByteArrayInputStream(raw));
        byte[] expected=Files.readAllBytes(Paths.get(args[1])); check(expected.length==1280,"reference length");
        for(int y=0;y<8;y++)for(int z=0;z<8;z++)for(int x=0;x<10;x++) {int i=(y*8+z)*10+x; check(s.id(x,y,z)==(expected[i]&255)&&s.meta(x,y,z)==(expected[640+i]&255),"position or meta "+i);}
        for(int[] p:new int[][]{{-1,0,0},{10,0,0},{0,-1,0},{0,8,0},{0,0,-1},{0,0,8}}) {try{s.id(p[0],p[1],p[2]);throw new AssertionError("index accepted");}catch(IndexOutOfBoundsException good){}}
        byte[] corrupted=raw.clone(); corrupted[corrupted.length/2]^=1;
        try {NoGlassSchematic.load(new ByteArrayInputStream(corrupted));throw new AssertionError("corrupt accepted");} catch(IOException good){}
        try {NoGlassSchematic.load(null);throw new AssertionError("missing accepted");} catch(IOException good){}
        int count=0; NoGlassPlan.Candidate c=null;
        for(int x=-512;x<512;x++)for(int z=-128;z<128;z++) {
            NoGlassPlan.Candidate a=NoGlassPlan.select(12345L,x,z),b=NoGlassPlan.select(12345L,x,z);
            check((a==null)==(b==null),"unstable");
            if(a!=null){count++;check(a.x==b.x&&a.z==b.z&&a.x==3&&a.z>=3&&a.z<=5,"unstable coord"); c=a;}
        }
        check(count>15500&&count<17300,"distribution "+count); check(c!=null,"no candidates");
        for(int x:new int[]{Integer.MIN_VALUE,-1,0,1,Integer.MAX_VALUE}) for(int z:new int[]{Integer.MIN_VALUE,-1,0,1,Integer.MAX_VALUE}) {
            NoGlassPlan.Candidate a=NoGlassPlan.select(Long.MIN_VALUE,x,z),b=NoGlassPlan.select(Long.MIN_VALUE,x,z);
            check((a==null)==(b==null),"extreme unstable");
        }
        Ground g=new Ground(); List<NoGlassPlan.Cell> cells=NoGlassPlan.plan(g,c,s);check(cells.size()==640,"flat not planned");
        for(int i=0;i<640;i++){int x=i%10,z=(i/10)%8,y=i/80; NoGlassPlan.Cell cell=cells.get(i);check(cell.x==c.x+x&&cell.y==62+y&&cell.z==c.z+z&&cell.id==s.id(x,y,z)&&cell.meta==s.meta(x,y,z),"bad plan "+i);}
        try {cells.clear();throw new AssertionError("mutable");} catch(UnsupportedOperationException good){}
        check(NoGlassPlan.plan(g,null,s).isEmpty(),"null candidate");
        g.badX=c.x;g.badZ=c.z;g.badY=70;g.badId=5; check(NoGlassPlan.plan(g,c,s).isEmpty(),"construction");
        g.badY=64;g.badId=2;g.badMeta=1;check(NoGlassPlan.plan(g,c,s).isEmpty(),"grass meta");
        g.badY=63;g.badId=54;g.badMeta=0;check(NoGlassPlan.plan(g,c,s).isEmpty(),"chest");
        g.badY=63;g.badId=3;g.tile=true;check(NoGlassPlan.plan(g,c,s).isEmpty(),"tile");
        g.badY=200;g.badId=0;g.tile=true;check(NoGlassPlan.plan(g,c,s).isEmpty(),"tile in air");
        g.tile=false;g.badY=64;g.badId=0;check(NoGlassPlan.plan(g,c,s).isEmpty(),"uneven surface");
        g.badY=61;g.badId=1;g.badMeta=1;check(NoGlassPlan.plan(g,c,s).isEmpty(),"unnatural support metadata");
        g.badY=255;g.badId=13;g.badMeta=0;check(NoGlassPlan.plan(g,c,s).isEmpty(),"obstruction overhead");
        g.badX=-1;g.tile=false;g.level=2;check(NoGlassPlan.plan(g,c,s).isEmpty(),"low");
        g.level=251;check(NoGlassPlan.plan(g,c,s).isEmpty(),"high");
        g.level=3;check(NoGlassPlan.plan(g,c,s).size()==640,"min level");
        g.level=250;check(NoGlassPlan.plan(g,c,s).size()==640,"max level");
        System.out.println("PASS: 640 cells, metadata, bounds, safety rejections, stable negatives/extremes; candidates="+count+"/262144");
    }
}
