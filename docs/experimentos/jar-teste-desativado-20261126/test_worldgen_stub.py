#!/usr/bin/env python3
"""Compile the actual placement/generator against tiny Minecraft test doubles.
This is not a real Forge runtime test; the observer side effect is modelled."""
from pathlib import Path
import subprocess
import os
base = Path(__file__).resolve().parent
p = base / 'project'
s = base / 'stub-src'
out = base / 'stub-classes'
files = {
'net/minecraft/util/math/BlockPos.java': '''package net.minecraft.util.math; public class BlockPos { public final int x,y,z; public BlockPos(int x,int y,int z){this.x=x;this.y=y;this.z=z;} }''',
'net/minecraft/block/Block.java': '''package net.minecraft.block; import net.minecraft.block.state.IBlockState; public class Block { public IBlockState getDefaultState(){ return new IBlockState(this); } }''',
'net/minecraft/block/state/IBlockState.java': '''package net.minecraft.block.state; import net.minecraft.block.Block; public class IBlockState { private final Block b; public IBlockState(Block b){this.b=b;} public Block getBlock(){return b;} }''',
'net/minecraft/init/Blocks.java': '''package net.minecraft.init; import net.minecraft.block.Block; public class Blocks { public static final Block STONE=new Block(), DIRT=new Block(); }''',
'net/minecraft/world/gen/IChunkGenerator.java': 'package net.minecraft.world.gen; public interface IChunkGenerator {}',
'net/minecraft/world/chunk/IChunkProvider.java': 'package net.minecraft.world.chunk; public interface IChunkProvider {}',
'net/minecraftforge/fml/common/IWorldGenerator.java': '''package net.minecraftforge.fml.common; import java.util.Random; import net.minecraft.world.World; import net.minecraft.world.gen.IChunkGenerator; import net.minecraft.world.chunk.IChunkProvider; public interface IWorldGenerator { void generate(Random r,int x,int z,World w,IChunkGenerator g,IChunkProvider p); }''',
'net/minecraft/world/World.java': '''package net.minecraft.world;
import net.minecraft.util.math.BlockPos; import net.minecraft.init.Blocks; import net.minecraft.block.state.IBlockState;
public class World { public boolean isRemote, loaded=true, stone=true; public Provider provider=new Provider(); public int cx,cz, writes, boundaryWrites, unwantedLoads, invalidFlags; public static class Provider { public int dimension; public int getDimension(){return dimension;} }
 public boolean isBlockLoaded(BlockPos p){ return loaded; }
 public IBlockState getBlockState(BlockPos p){ if(Math.floorDiv(p.x,16)!=cx || Math.floorDiv(p.z,16)!=cz) throw new AssertionError("cross-chunk read"); return (stone?Blocks.STONE:Blocks.DIRT).getDefaultState(); }
 public void setBlockState(BlockPos p,IBlockState state,int flags){ if(Math.floorDiv(p.x,16)!=cx || Math.floorDiv(p.z,16)!=cz) throw new AssertionError("cross-chunk write"); writes++; if(flags!=18)invalidFlags++; int x=Math.floorMod(p.x,16),z=Math.floorMod(p.z,16); if(x==0||x==15||z==0||z==15) {boundaryWrites++; if((flags&16)==0) unwantedLoads++;} }
}''',
'com/mikael8367/nexustech/ores/OreRegistry.java': '''package com.mikael8367.nexustech.ores; import java.util.*; import net.minecraft.block.Block; public class OreRegistry { public static final Map<OreType,Block> ORES=new EnumMap<>(OreType.class); static {for(OreType t:OreType.values()) ORES.put(t,new Block());} }''',
'WorldgenStubTest.java': '''import java.util.Random; import net.minecraft.world.World; import com.mikael8367.nexustech.ores.OreWorldGenerator;
public class WorldgenStubTest { static void check(boolean c,String msg){if(!c)throw new AssertionError(msg);} static void run(World w,int x,int z,int seed){w.cx=x;w.cz=z;new OreWorldGenerator().generate(new Random(seed),x,z,w,null,null);}
 public static void main(String[] a){ int boundaries=0,writes=0; for(int i=0;i<1000;i++){World w=new World(); run(w,i%55-27,i%71-35,i); check(w.invalidFlags==0,"wrong flags");check(w.unwantedLoads==0,"observer loads");boundaries+=w.boundaryWrites;writes+=w.writes;} check(boundaries>0,"no boundary exercised");
 World n=new World();n.provider.dimension=-1;run(n,0,0,0);check(n.writes==0,"other dimension"); World r=new World();r.isRemote=true;run(r,0,0,0);check(r.writes==0,"remote"); World u=new World();u.loaded=false;run(u,0,0,0);check(u.writes==0,"unloaded"); World d=new World();d.stone=false;run(d,0,0,0);check(d.writes==0,"not stone"); System.out.println("PASS stub generator: 1000 chunks; "+writes+" writes, "+boundaries+" boundary writes; flags=18; guards pass"); }
}'''
}
for name, content in files.items():
    dest=s/name; dest.parent.mkdir(parents=True,exist_ok=True); dest.write_text(content)
out.mkdir(exist_ok=True)
jdk=Path(os.environ['JAVA_HOME']) / 'bin'
sources=list(s.rglob('*.java')) + [p/'src/main/java/com/mikael8367/nexustech/ores/OreType.java',p/'src/main/java/com/mikael8367/nexustech/ores/OrePlacement.java',p/'src/main/java/com/mikael8367/nexustech/ores/OreWorldGenerator.java',p/'src/test/java/com/mikael8367/nexustech/ores/OrePlacementTest.java']
subprocess.run([str(jdk/'javac'),'-source','8','-target','8','-d',str(out)]+[str(x) for x in sources],check=True)
for test in ['com.mikael8367.nexustech.ores.OrePlacementTest','WorldgenStubTest']:
    subprocess.run([str(jdk/'java'),'-cp',str(out),test],check=True)
