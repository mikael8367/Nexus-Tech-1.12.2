package com.mikael8367.nexustech.worldgen;

import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** TEST ONLY: installed by isolated CI, never distributed. */
public final class StructureForgeHarness {
    private static int depth=0;
    private static final List<String> placed = new ArrayList<>();
    private static boolean ran;
    public static void register() { MinecraftForge.EVENT_BUS.register(new StructureForgeHarness()); }
    public static void enter() { if (++depth != 1) throw new IllegalStateException("recursive structure generator invocation"); }
    public static void exit() { --depth; }
    public static void record(int cx,int cz,int x,int z,int surface) {
        String s=cx+","+cz+","+x+","+z+","+surface;
        placed.add(s); System.out.println("STRUCTURE_CANDIDATE_PLACED "+s);
    }
    private static void check(World w,String line,ExactSchematic sc) {
        String[] a=line.split(","); if(a.length!=5) throw new AssertionError("bad record");
        int cx=Integer.parseInt(a[0]),cz=Integer.parseInt(a[1]),lx=Integer.parseInt(a[2]),lz=Integer.parseInt(a[3]),surface=Integer.parseInt(a[4]);
        int solid=0,air=0;
        for(int y=0;y<5;y++) for(int z=0;z<6;z++) for(int x=0;x<6;x++) {
            BlockPos p=new BlockPos((cx<<4)+lx+x,surface-2+y,(cz<<4)+lz+z);
            IBlockState s=w.getBlockState(p); int id=Block.getIdFromBlock(s.getBlock()), meta=s.getBlock().getMetaFromState(s);
            int eid=sc.id(x,y,z),emeta=sc.meta(x,y,z);
            if(id!=eid || meta!=emeta) throw new AssertionError("mismatch at "+p+" got "+id+":"+meta+" expected "+eid+":"+emeta);
            if(eid==0) air++; else solid++;
        }
        if(solid!=130 || air!=50) throw new AssertionError("unexpected counts "+solid+" "+air);
        System.out.println("STRUCTURE_180_STATES_VERIFIED "+line+" solid="+solid+" air="+air);
    }
    @SubscribeEvent public void onTick(TickEvent.ServerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END || ran) return;
        ran=true;
        try {
            MinecraftServer server=net.minecraftforge.fml.common.FMLCommonHandler.instance().getMinecraftServerInstance();
            WorldServer w=server.getWorld(0); if(w==null) throw new AssertionError("no overworld");
            ExactSchematic sc=ExactSchematic.load(StructureForgeHarness.class.getResourceAsStream("/assets/nexustech/structures/original.schematic"));
            Path file=Paths.get("structure-harness-records.txt");
            if(!Files.exists(file)) {
                for(int sx:new int[]{-1,1}) for(int sz:new int[]{-1,1}) {
                    int before=placed.size(), attempted=0;
                    outer: for(int ax=100;ax<200;ax++) for(int az=100;az<200;az++) {
                        int cx=ax*sx,cz=az*sz;
                        if(StructurePlan.select(w.getSeed(),cx,cz)==null) continue;
                        attempted++;
                        // Four chunks cause vanilla population of the target where eligible.
                        w.getChunkFromChunkCoords(cx,cz); w.getChunkFromChunkCoords(cx+1,cz);
                        w.getChunkFromChunkCoords(cx,cz+1); w.getChunkFromChunkCoords(cx+1,cz+1);
                        boolean found=false;
                        for(int i=before;i<placed.size();i++) { String[] a=placed.get(i).split(","); if(Integer.parseInt(a[0])*sx>0 && Integer.parseInt(a[1])*sz>0) found=true; }
                        if(found) break outer;
                        if(attempted>=100) break outer;
                    }
                    boolean found=false; for(String line:placed) {String[] a=line.split(","); if(Integer.parseInt(a[0])*sx>0 && Integer.parseInt(a[1])*sz>0) found=true;}
                    if(!found) throw new AssertionError("no placement in quadrant "+sx+","+sz+" attempts="+attempted);
                }
                for(String s:placed) check(w,s,sc);
                Files.write(file,placed,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
                w.saveAllChunks(true,null);
                System.out.println("STRUCTURE_FIRST_BOOT_PASS count="+placed.size());
            } else {
                List<String> lines=Files.readAllLines(file,StandardCharsets.UTF_8);
                if(lines.size()<4) throw new AssertionError("too few saved placements");
                boolean[] quadrants=new boolean[4];
                for(String s:lines) {String[] a=s.split(",");int cx=Integer.parseInt(a[0]),cz=Integer.parseInt(a[1]); w.getChunkFromChunkCoords(cx,cz); check(w,s,sc);quadrants[(cx>0?2:0)+(cz>0?1:0)]=true;}
                for(boolean q:quadrants) if(!q) throw new AssertionError("missing quadrant on reload");
                System.out.println("STRUCTURE_RELOAD_PASS count="+lines.size());
            }
            server.initiateShutdown();
        } catch(Throwable t) { t.printStackTrace(); System.out.println("STRUCTURE_TEST_FAILED "+t); net.minecraftforge.fml.common.FMLCommonHandler.instance().getMinecraftServerInstance().initiateShutdown(); }
    }
}
