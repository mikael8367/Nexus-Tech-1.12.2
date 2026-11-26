package com.mikael8367.nexustech.worldgen;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Pure model tests. They do not simulate Minecraft lighting or chunk internals. */
public final class StructurePlanTest {
    static void check(boolean value,String message) { if(!value) throw new AssertionError(message); }
    static final class Site implements StructurePlan.Site {
        final int[][][] ids=new int[16][256][16], metas=new int[16][256][16];
        final boolean[][][] tiles=new boolean[16][256][16];
        int writes;
        Site() { for(int x=0;x<16;x++) for(int z=0;z<16;z++) { for(int y=1;y<64;y++) ids[x][y][z]=3; ids[x][0][z]=7; ids[x][64][z]=2; } }
        void bounds(int x,int y,int z) { check(x>=0&&x<=15&&z>=0&&z<=15&&y>=0&&y<256,"out-of-chunk/bounds access"); }
        public int id(int x,int y,int z) { bounds(x,y,z); return ids[x][y][z]; }
        public int meta(int x,int y,int z) { bounds(x,y,z); return metas[x][y][z]; }
        public boolean hasTile(int x,int y,int z) { bounds(x,y,z); return tiles[x][y][z]; }
        public void set(int x,int y,int z,int id,int meta) { bounds(x,y,z); writes++; ids[x][y][z]=id;metas[x][y][z]=meta; }
    }
    static StructurePlan.Candidate candidate() { for(int x=-1000;x<1000;x++) { StructurePlan.Candidate c=StructurePlan.select(100,x,-23); if(c!=null)return c; } throw new AssertionError(); }
    static String hex(byte[] b) { StringBuilder s=new StringBuilder();for(byte v:b)s.append(String.format("%02x",v&255));return s.toString(); }
    static void rejected(Site site,StructurePlan.Candidate c,ExactSchematic s,String reason) { check(!StructurePlan.apply(site,c,s),reason+" should reject");check(site.writes==0,reason+" wrote"); }
    public static void main(String[] args) throws Exception {
        byte[] file=Files.readAllBytes(Paths.get("src/main/resources/assets/nexustech/structures/original.schematic"));
        ExactSchematic s=ExactSchematic.load(new ByteArrayInputStream(file));
        MessageDigest digest=MessageDigest.getInstance("SHA-256"); int air=0,solid=0;
        for(int y=0;y<5;y++) for(int z=0;z<6;z++) for(int x=0;x<6;x++) {
            int id=s.id(x,y,z),meta=s.meta(x,y,z);digest.update((byte)id);digest.update((byte)meta);
            if(id==0)air++;else solid++;
        }
        check(hex(digest.digest()).equals("b435039bfb22151e36c3d62f37bbd075511d0c672f4d04132e49a6c3122e2db5"),"all 180 ID/meta and orientation");
        check(air==50 && solid==130,"counts");
        check(s.id(3,3,5)==108 && s.meta(3,3,5)==0 && s.id(5,3,5)==108 && s.meta(5,3,5)==7,"stairs metadata orientation");
        byte[] corrupt=file.clone();corrupt[corrupt.length/2]^=1;boolean refused=false;
        try { ExactSchematic.load(new ByteArrayInputStream(corrupt)); } catch(Exception e){refused=true;} check(refused,"corrupt file rejected");
        refused=false;try {ExactSchematic.load(null);}catch(Exception e){refused=true;}check(refused,"missing file rejected");
        int selected=0;
        for(int cx=-250;cx<=250;cx++) for(int cz=-250;cz<=250;cz++) {
            StructurePlan.Candidate a=StructurePlan.select(987654321L,cx,cz),b=StructurePlan.select(987654321L,cx,cz);
            check((a==null)==(b==null),"determinism");
            if(a!=null) {selected++;check(a.x==b.x&&a.z==b.z,"offset determinism");check(a.x>=3&&a.x+5<=12&&a.z>=3&&a.z+5<=12,"margin");check(((cx*16+a.x)>>4)==cx && ((cz*16+a.z)>>4)==cz,"negative chunk conversion");}
        }
        check(selected>15000&&selected<16500,"1/16 frequency"); for(long seed:new long[]{0L,-1L,Long.MIN_VALUE,Long.MAX_VALUE}) {int n=0; for(int x=-250;x<=250;x++) for(int z=-250;z<=250;z++) if(StructurePlan.select(seed,x,z)!=null) n++; check(n>15000&&n<16500,"1/16 frequency seed="+seed+" n="+n); System.out.println("seed="+seed+" selected="+n+" / 251001");}
        StructurePlan.Candidate c=candidate();Site clean=new Site();
        check(StructurePlan.apply(clean,c,s),"flat natural terrain accepted");
        for(int y=0;y<5;y++) for(int z=0;z<6;z++) for(int x=0;x<6;x++)
            check(clean.ids[c.x+x][62+y][c.z+z]==s.id(x,y,z)&&clean.metas[c.x+x][62+y][c.z+z]==s.meta(x,y,z),"exact generated block/air");
        check(clean.writes>0,"no writes");
        for(int y=61;y<=66;y++) { Site t=new Site();t.tiles[c.x+2][y][c.z+2]=true;rejected(t,c,s,"tile y="+y); }
        Site roof=new Site();roof.ids[c.x][200][c.z]=5;rejected(roof,c,s,"building above");
        Site mod=new Site();mod.ids[c.x+2][63][c.z+2]=401;rejected(mod,c,s,"mod block");
        Site chest=new Site();chest.ids[c.x+2][64][c.z+2]=54;chest.tiles[c.x+2][64][c.z+2]=true;rejected(chest,c,s,"chest");
        Site slope=new Site();slope.ids[c.x+2][65][c.z+2]=2;rejected(slope,c,s,"slope");
        Site water=new Site();water.ids[c.x+2][65][c.z+2]=9;rejected(water,c,s,"water");
        Site unsupported=new Site();unsupported.ids[c.x+2][61][c.z+2]=0;rejected(unsupported,c,s,"unsupported");
        Site variant=new Site();variant.metas[c.x+2][63][c.z+2]=1;rejected(variant,c,s,"unnatural soil metadata");
        Site invisible=new Site();invisible.tiles[c.x+2][200][c.z+2]=true;rejected(invisible,c,s,"tile in air");
        Site nearby=new Site();nearby.ids[0][65][0]=45;rejected(nearby,c,s,"nearby building outside footprint");
        Site buried=new Site();buried.ids[15][20][15]=45;rejected(buried,c,s,"buried structure");
        Site cave=new Site();cave.ids[0][25][15]=0;check(StructurePlan.apply(cave,c,s),"deep cave should be tolerated");
        Site ore=new Site();ore.ids[0][20][15]=15;ore.ids[c.x][62][c.z]=16;check(StructurePlan.apply(ore,c,s),"natural ore should be tolerated");
        Site plants=new Site();plants.ids[c.x][65][c.z]=31;plants.ids[0][65][0]=37;check(StructurePlan.apply(plants,c,s),"common plants should be tolerated");
        Site island=new Site();for(int x=0;x<16;x++) for(int z=0;z<16;z++) for(int y=0;y<60;y++) island.ids[x][y][z]=0;rejected(island,c,s,"floating island");
        Site sky=new Site();for(int x=0;x<16;x++) for(int z=0;z<16;z++) for(int y=0;y<256;y++) sky.ids[x][y][z]=0;rejected(sky,c,s,"empty sky");
        Site waterEdge=new Site();waterEdge.ids[15][65][0]=9;rejected(waterEdge,c,s,"water at chunk edge");
        Site tree=new Site();tree.ids[15][65][15]=17;rejected(tree,c,s,"nearby tree");
        Site hillside=new Site();hillside.ids[0][65][15]=2;rejected(hillside,c,s,"slope outside footprint");
        Site low=new Site();for(int x=0;x<16;x++) for(int z=0;z<16;z++){for(int y=60;y<=64;y++)low.ids[x][y][z]=0;low.ids[x][59][z]=2;}rejected(low,c,s,"surface below 60");
        Site high=new Site();for(int x=0;x<16;x++) for(int z=0;z<16;z++){high.ids[x][64][z]=3;for(int y=65;y<101;y++)high.ids[x][y][z]=3;high.ids[x][101][z]=2;}rejected(high,c,s,"surface above 100");
        Site absent=new Site();check(!StructurePlan.apply(absent,null,s)&&absent.writes==0,"no candidate");
        System.out.println("PASS: 180 exact ID/meta; 130 solids + 50 air; orientation; integrity; 251001 signed chunks, "+selected+" candidates; local bounds; terrain/TE rejections and preflight");
        System.out.println("LIMIT: model does not prove real Forge lighting, chunk loading, atomicity or old-world safety");
    }
}
