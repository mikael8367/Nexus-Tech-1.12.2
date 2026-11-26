package com.mikael8367.nexustech.worldgen;

import java.util.Random;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraftforge.fml.common.IWorldGenerator;

/** NOT REGISTERED: the 1.12 population callback cannot prove a chunk is new. */
public final class ExperimentalStructureGenerator implements IWorldGenerator {
    @Override public void generate(Random ignored,int cx,int cz,World world,IChunkGenerator generator,IChunkProvider provider) {
        if(world.isRemote || world.provider.getDimension()!=0) return;
        StructurePlan.Candidate candidate=StructurePlan.select(world.getSeed(),cx,cz);
        if(candidate==null) return;
        final Chunk chunk=provider.getLoadedChunk(cx,cz); // never provide/load a chunk
        if(chunk==null) return;
        final ExactSchematic schematic;
        try { schematic=ExactSchematic.load(ExperimentalStructureGenerator.class.getResourceAsStream("/assets/nexustech/structures/original.schematic")); }
        catch(Exception invalid) { throw new IllegalStateException("Nexus structure resource invalid; refusing generation",invalid); }
        // Validate all targets before doing anything. Never silently normalize metadata.
        final IBlockState[][] states=new IBlockState[256][16];
        for(int y=0;y<5;y++) for(int z=0;z<6;z++) for(int x=0;x<6;x++) {
            int id=schematic.id(x,y,z),meta=schematic.meta(x,y,z);
            if(states[id][meta]!=null) continue;
            Block block=vanilla(id);
            if(block==null || Block.getBlockById(id)!=block) return;
            IBlockState state=block.getStateFromMeta(meta);
            if(block.getMetaFromState(state)!=meta || block.hasTileEntity(state)) return;
            states[id][meta]=state;
        }
        StructurePlan.apply(new StructurePlan.Site() {
            private BlockPos pos(int x,int y,int z) {
                if(x<0 || x>15 || z<0 || z>15 || y<0 || y>255) throw new IllegalArgumentException("out of chunk-safe bounds");
                return new BlockPos((cx<<4)+x,y,(cz<<4)+z);
            }
            public int id(int x,int y,int z) {
                Block b=chunk.getBlockState(pos(x,y,z)).getBlock();
                if(b==Blocks.AIR) return 0; if(b==Blocks.STONE) return 1; if(b==Blocks.GRASS) return 2; if(b==Blocks.DIRT) return 3; if(b==Blocks.BEDROCK) return 7;
                return -1;
            }
            public int meta(int x,int y,int z) {
                IBlockState s=chunk.getBlockState(pos(x,y,z)); return s.getBlock().getMetaFromState(s);
            }
            public boolean hasTile(int x,int y,int z) {
                BlockPos p=pos(x,y,z); IBlockState s=chunk.getBlockState(p);
                return s.getBlock().hasTileEntity(s) || chunk.getTileEntity(p,Chunk.EnumCreateEntityType.CHECK)!=null;
            }
            public void set(int x,int y,int z,int id,int meta) {
                if(!world.setBlockState(pos(x,y,z),states[id][meta],2|16)) throw new IllegalStateException("partial structure write");
            }
        },candidate,schematic);
    }
    private static Block vanilla(int id) {
        switch(id) {
            case 0:return Blocks.AIR; case 1:return Blocks.STONE; case 2:return Blocks.GRASS;
            case 3:return Blocks.DIRT; case 44:return Blocks.STONE_SLAB;
            case 45:return Blocks.BRICK_BLOCK; case 108:return Blocks.BRICK_STAIRS;
            default:return null;
        }
    }
}
