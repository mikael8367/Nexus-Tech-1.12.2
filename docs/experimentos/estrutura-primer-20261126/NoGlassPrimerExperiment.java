package com.mikael8367.nexustech.worldgen;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.world.chunk.ChunkPrimer;
import net.minecraftforge.event.terraingen.ChunkGeneratorEvent.ReplaceBiomeBlocks;

/** UNREGISTERED experiment only. Do not call in a persistent world. */
public final class NoGlassPrimerExperiment {
    private NoGlassPrimerExperiment() {}
    public enum Status { INVALID, NOT_SELECTED, BAD_PALETTE, UNSAFE_TERRAIN, READY_WRITE_FAILED_PARTIAL, WRITTEN_TO_PRIMER, READ_FAILED }
    public static final class Report {
        public final Status status;
        /** Successfully returned write calls; on exception a further write may have happened. */
        public final int completedWrites;
        private Report(Status status, int n) { this.status=status; this.completedWrites=n; }
    }
    private static Report r(Status s) { return new Report(s,0); }

    /** Caller must run on the server generator thread, only in a disposable new world.
     * No proof of lifecycle, old-chunk protection, neighboring chunks or other mods.
     * No event result is changed. Nothing in this class registers a listener.
     */
    public static Report apply(ReplaceBiomeBlocks event, long seed, NoGlassSchematic schematic) {
        if(event==null || schematic==null) return r(Status.INVALID);
        final int cx=event.getX(), cz=event.getZ();
        long bx=(long)cx*16L, bz=(long)cz*16L;
        if(bx < -29999984L || bx+15 > 29999984L || bz < -29999984L || bz+15 > 29999984L) return r(Status.INVALID);
        NoGlassPlan.Candidate c=NoGlassPlan.select(seed,cx,cz);
        if(c==null) return r(Status.NOT_SELECTED);
        ChunkPrimer primer=event.getPrimer();
        if(primer==null || primer.getClass()!=ChunkPrimer.class) return r(Status.INVALID);
        if(c.x-2<0 || c.x+11>15 || c.z-2<0 || c.z+9>15) return r(Status.INVALID);
        final IBlockState[] states=new IBlockState[640];
        int surface=-1;
        try {
            IBlockState air=Blocks.AIR.getDefaultState(), stone=Blocks.STONE.getDefaultState(), bedrock=Blocks.BEDROCK.getDefaultState();
            // Read entire one/two-block margin to reject water, trees, foreign blocks,
            // non-default metadata and tile-bearing block states. Primer has no TE map.
            for(int z=c.z-2;z<=c.z+9;z++) for(int x=c.x-2;x<=c.x+11;x++) {
                int top=-1;
                for(int y=0;y<256;y++) {
                    IBlockState s=primer.getBlockState(x,y,z);
                    if(s==null || (s!=air && s!=stone && s!=bedrock) || s.getBlock().hasTileEntity(s)) return r(Status.UNSAFE_TERRAIN);
                    if(s!=air) top=y;
                }
                if(x>=c.x && x<c.x+10 && z>=c.z && z<c.z+8) {
                    if(top<4 || top>250 || primer.getBlockState(x,top,z)!=stone) return r(Status.UNSAFE_TERRAIN);
                    if(surface<0) surface=top; else if(surface!=top) return r(Status.UNSAFE_TERRAIN);
                    for(int y=top-3;y<=top;y++) if(primer.getBlockState(x,y,z)!=stone) return r(Status.UNSAFE_TERRAIN);
                }
            }
            int base=surface-2;
            if(base<1 || base+7>255) return r(Status.INVALID);
            int n=0,gravel=0;
            for(int y=0;y<8;y++) for(int z=0;z<8;z++) for(int x=0;x<10;x++) {
                int id=schematic.id(x,y,z), meta=schematic.meta(x,y,z); Block b;
                switch(id) { case 0:b=Blocks.AIR;break; case 1:b=Blocks.STONE;break; case 2:b=Blocks.GRASS;break; case 3:b=Blocks.DIRT;break; case 13:b=Blocks.GRAVEL;gravel++;break; case 15:b=Blocks.IRON_ORE;break; default:return r(Status.BAD_PALETTE); }
                IBlockState s=b.getStateFromMeta(meta);
                if(s==null || s.getBlock()!=b || b.getMetaFromState(s)!=meta || b.hasTileEntity(s)) return r(Status.BAD_PALETTE);
                if(id==13) {
                    int below=y==0 ? 1 : schematic.id(x,y-1,z);
                    if(below!=1 && below!=2 && below!=3 && below!=15) return r(Status.BAD_PALETTE);
                }
                states[n++]=s;
            }
            if(n!=640 || gravel!=12) return r(Status.BAD_PALETTE);
            int done=0;
            try {
                for(int y=0;y<8;y++) for(int z=0;z<8;z++) for(int x=0;x<10;x++) {
                    primer.setBlockState(c.x+x,base+y,c.z+z,states[done]);
                    done++;
                }
                return new Report(Status.WRITTEN_TO_PRIMER,done);
            } catch(RuntimeException e) { return new Report(Status.READY_WRITE_FAILED_PARTIAL,done); }
        } catch(RuntimeException e) { return r(Status.READ_FAILED); }
    }
}
