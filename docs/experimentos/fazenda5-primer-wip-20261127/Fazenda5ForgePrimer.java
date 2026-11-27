package com.mikael8367.nexustech.worldgen;

import java.io.IOException;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.chunk.ChunkPrimer;

/** Offline experiment only; never registered as a generator. No World or provider primer. */
public final class Fazenda5ForgePrimer {
    public static final int SIZE=16*16*256;
    public enum Mapping { VANILLA_HYPOTHESIS_ONLY }
    private Fazenda5ForgePrimer() { }
    public static int index(int x,int y,int z) {
        if(x<0||x>15||y<0||y>255||z<0||z>15) throw new IllegalArgumentException("bounds");
        return (x*16+z)*256+y;
    }
    /** The primer is not exposed; its result cannot be handed to a chunk provider. */
    public static final class Result {
        private final ChunkPrimer primer;
        private Result(ChunkPrimer p){primer=p;}
        public IBlockState get(int x,int y,int z){index(x,y,z);return primer.getBlockState(x,y,z);}
    }
    static IBlockState resolve(int id,int meta) {
        Block b; String name;
        switch(id){
            case 0:b=Blocks.AIR;name="air";break;
            case 2:b=Blocks.GRASS;name="grass";break;
            case 3:b=Blocks.DIRT;name="dirt";break;
            case 9:b=Blocks.WATER;name="water";break;
            case 59:b=Blocks.WHEAT;name="wheat";break;
            case 60:b=Blocks.FARMLAND;name="farmland";break;
            case 85:b=Blocks.OAK_FENCE;name="fence";break;
            default:throw new IllegalArgumentException("ID not in palette");
        }
        if(!(meta==0 || id==3 && meta==1)) throw new IllegalArgumentException("metadata not in palette");
        if(Block.getIdFromBlock(b)!=id || !new ResourceLocation("minecraft",name).equals(b.getRegistryName()) || Block.REGISTRY.getObject(new ResourceLocation("minecraft",name))!=b) throw new IllegalArgumentException("registry mismatch");
        IBlockState s=b.getStateFromMeta(meta);
        if(s==null || s.getBlock()!=b || b.getMetaFromState(s)!=meta || b.hasTileEntity(s)) throw new IllegalArgumentException("state roundtrip or tile mismatch");
        return s;
    }
    /** x/y/z are minimum world coordinates; cx/cz are the one permitted chunk.
     * Snapshot is a 65536-element local x,z,y array. Only air is replaceable; the
     * 5x5 layer below must be default stone. No claim about source registry provenance.
     */
    public static Result run(IBlockState[] terrain,int x,int y,int z,int cx,int cz,Mapping mapping) throws IOException {
        return runForTest(terrain,x,y,z,cx,cz,mapping,-1,false);
    }
    static Result runForTest(IBlockState[] terrain,int x,int y,int z,int cx,int cz,Mapping mapping,int failAt,boolean failAfter) throws IOException {
        if(mapping!=Mapping.VANILLA_HYPOTHESIS_ONLY || terrain==null || terrain.length!=SIZE || y<1) throw new IllegalArgumentException("input");
        IBlockState[] source=terrain.clone();
        for(IBlockState s:source) if(s==null) throw new IllegalArgumentException("null terrain");
        NovaSchematic s=NovaSchematic.loadResource(NovaSchematic.Resource.ANEXO_5X2X5);
        if(s.width()!=5||s.height()!=2||s.length()!=5||s.volume()!=50) throw new IllegalArgumentException("geometry");
        NovaPlacement.Plan plan=NovaPlacement.plan(s,x,y,z,cx,cz,256);
        List<NovaPlacement.Entry> entries=plan.entries();
        if(entries.size()!=50) throw new IllegalArgumentException("cell count");
        IBlockState air=Blocks.AIR.getDefaultState(), stone=Blocks.STONE.getDefaultState();
        IBlockState[] states=new IBlockState[50]; boolean[] seen=new boolean[SIZE];
        for(int i=0;i<50;i++) {
            NovaPlacement.Entry e=entries.get(i);
            if((e.x>>4)!=cx || (e.z>>4)!=cz || e.y<0 || e.y>255 || e.tileCopy()!=null) throw new IllegalArgumentException("entry outside chunk or tile");
            int idx=index(e.x&15,e.y,e.z&15);
            if(seen[idx]) throw new IllegalArgumentException("duplicate entry");
            seen[idx]=true;states[i]=resolve(e.id,e.metadata);
            if(source[idx]!=air) throw new IllegalArgumentException("protected destination");
        }
        for(int dx=0;dx<5;dx++) for(int dz=0;dz<5;dz++)
            if(source[index((x+dx)&15,y-1,(z+dz)&15)]!=stone) throw new IllegalArgumentException("unsupported base");
        ChunkPrimer p=new ChunkPrimer();
        for(int px=0;px<16;px++) for(int pz=0;pz<16;pz++) for(int py=0;py<256;py++) {
            IBlockState state=source[index(px,py,pz)];
            if(state!=air) p.setBlockState(px,py,pz,state);
        }
        // Reject unrepresentable states rather than silently changing unrelated blocks.
        for(int px=0;px<16;px++) for(int pz=0;pz<16;pz++) for(int py=0;py<256;py++)
            if(p.getBlockState(px,py,pz)!=source[index(px,py,pz)]) throw new IllegalArgumentException("terrain not representable in primer");
        for(int i=0;i<50;i++) {
            if(i==failAt && !failAfter) throw new IllegalStateException("injected before write");
            NovaPlacement.Entry e=entries.get(i);p.setBlockState(e.x&15,e.y,e.z&15,states[i]);
            if(i==failAt && failAfter) throw new IllegalStateException("injected after write");
        }
        for(int i=0;i<50;i++) {NovaPlacement.Entry e=entries.get(i); if(p.getBlockState(e.x&15,e.y,e.z&15)!=states[i]) throw new IllegalStateException("verification failed");}
        return new Result(p);
    }
}
