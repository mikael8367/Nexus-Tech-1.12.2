package com.mikael8367.nexustech.worldgen;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityBed;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.tileentity.TileEntityFlowerPot;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.ChunkPrimer;

/** INCOMPLETE, UNTESTED offline experiment; unregistered, never publish a chunk. */
public final class NovaForgeLab {
    private NovaForgeLab() { }
    public enum Verdict { BLOCKED, INVALID, PALETTE, TERRAIN, SUPPORT, READ_FAILURE }
    public static final class PreparedTile {
        public final int blockId; private final NBTTagCompound tag;
        private PreparedTile(int id,NBTTagCompound tag){this.blockId=id;this.tag=tag.copy();}
        public NBTTagCompound nbt(){return tag.copy();}
    }
    public static final class Report {
        public final Verdict verdict; public final int states, tiles; public final boolean destinationOK, supportOK;
        private final List<PreparedTile> data;
        private Report(Verdict v,int states,List<PreparedTile> data,boolean dst,boolean sup){
            this.verdict=v;this.states=states;this.data=Collections.unmodifiableList(new ArrayList<>(data));
            this.tiles=data.size();this.destinationOK=dst;this.supportOK=sup;
        }
        public List<PreparedTile> tiles(){return data;}
    }
    private static Report report(Verdict v,int count,List<PreparedTile> ts,boolean d,boolean s){return new Report(v,count,ts,d,s);}
    private static Block known(int id) {
        switch(id){
            case 0:return Blocks.AIR; case 2:return Blocks.GRASS; case 3:return Blocks.DIRT;
            case 5:return Blocks.PLANKS; case 26:return Blocks.BED; case 44:return Blocks.STONE_SLAB;
            case 58:return Blocks.CRAFTING_TABLE; case 61:return Blocks.FURNACE;
            case 97:return Blocks.MONSTER_EGG; case 106:return Blocks.VINE;
            case 139:return Blocks.COBBLESTONE_WALL; case 140:return Blocks.FLOWER_POT;
            case 171:return Blocks.CARPET; default:return null;
        }
    }
    private static boolean allowed(int id,int meta) {
        switch(id) {
            case 0: case 2: case 3: case 5: case 58: case 140: return meta==0;
            case 26:return meta==1||meta==9; case 44:return meta==3;
            case 61:return meta==5; case 97:return meta==1||meta==2||meta==3;
            case 106:return meta==1||meta==2||meta==4; case 139:return meta==0||meta==1;
            case 171:return meta==14; default:return false;
        }
    }
    private static String expectedTile(int id){return id==26?"minecraft:bed":id==61?"minecraft:furnace":id==140?"minecraft:flower_pot":null;}
    /** Exact resource only; 210 states including air, four copied tile NBTs. */
    public static Report inspect(ChunkPrimer primer,int x,int y,int z,int cx,int cz) throws IOException {
        List<PreparedTile> tiles=new ArrayList<>(); int count=0;
        if(primer==null||primer.getClass()!=ChunkPrimer.class||y<1||y>251)
            return report(Verdict.INVALID,0,tiles,false,false);
        final NovaPlacement.Plan plan;
        try {plan=NovaPlacement.plan(NovaSchematic.loadResource(),x,y,z,cx,cz,256);}
        catch(IllegalArgumentException e){return report(Verdict.INVALID,0,tiles,false,false);}
        IBlockState[] states=new IBlockState[210];
        try {
            for(NovaPlacement.Entry e:plan.entries()) {
                Block b=known(e.id);
                if(b==null||!allowed(e.id,e.metadata)||Block.getIdFromBlock(b)!=e.id)
                    return report(Verdict.PALETTE,count,tiles,false,false);
                IBlockState s=b.getStateFromMeta(e.metadata);
                if(s==null||s.getBlock()!=b||b.getMetaFromState(s)!=e.metadata)
                    return report(Verdict.PALETTE,count,tiles,false,false);
                NBTTagCompound n=e.tileCopy(); String expected=expectedTile(e.id);
                if((n==null)!=(expected==null)||b.hasTileEntity(s)!=(expected!=null))
                    return report(Verdict.PALETTE,count,tiles,false,false);
                if(n!=null){
                    if(!expected.equals(n.getString("id"))||n.getInteger("x")!=e.x||n.getInteger("y")!=e.y||n.getInteger("z")!=e.z)
                        return report(Verdict.PALETTE,count,tiles,false,false);
                    tiles.add(new PreparedTile(e.id,n));
                }
                states[count++]=s;
            }
            if(count!=210||tiles.size()!=4) return report(Verdict.PALETTE,count,tiles,false,false);
            for(NovaPlacement.Entry e:plan.entries()) {
                IBlockState old=primer.getBlockState(e.x&15,e.y,e.z&15);
                if(old!=Blocks.AIR.getDefaultState()) return report(Verdict.TERRAIN,count,tiles,false,false);
            }
            for(int dz=0;dz<7;dz++) for(int dx=0;dx<6;dx++)
                if(primer.getBlockState((x+dx)&15,y-1,(z+dz)&15)!=Blocks.STONE.getDefaultState())
                    return report(Verdict.TERRAIN,count,tiles,false,false);
            boolean support=true;
            for(int i=0;i<210;i++) {
                NovaPlacement.Entry e=plan.entries().get(i);
                if(e.id==0) continue;
                if(e.id==106) {support=false;continue;}
                if(e.y>y) { IBlockState below=states[i-42]; if(!below.isFullCube()) support=false; }
            }
            if(!support) return report(Verdict.SUPPORT,count,tiles,true,false);
            return report(Verdict.BLOCKED,count,tiles,true,true);
        } catch(RuntimeException ex) {return report(Verdict.READ_FAILURE,count,tiles,false,false);}
    }
    /** Offline smoke experiment only. Never install into a live world. */
    public static TileEntity instantiateForOfflineTest(PreparedTile p) {
        if(p==null) throw new IllegalArgumentException("tile");
        NBTTagCompound n=p.nbt(); TileEntity t;
        if(p.blockId==26 && "minecraft:bed".equals(n.getString("id"))) t=new TileEntityBed();
        else if(p.blockId==61 && "minecraft:furnace".equals(n.getString("id"))) t=new TileEntityFurnace();
        else if(p.blockId==140 && "minecraft:flower_pot".equals(n.getString("id"))) t=new TileEntityFlowerPot();
        else throw new IllegalArgumentException("unrecognized tile");
        t.readFromNBT(n);
        if(t.hasWorld()||!t.getPos().equals(new BlockPos(n.getInteger("x"),n.getInteger("y"),n.getInteger("z"))))
            throw new IllegalStateException("unexpected tile state");
        return t;
    }
    public static void publish() { throw new UnsupportedOperationException("BLOCKED: no owned private Chunk/TE delivery lifecycle"); }
}
