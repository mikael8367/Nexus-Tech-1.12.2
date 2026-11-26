#!/usr/bin/env python3
"""Standalone behavioural tests with fake Forge classes. No server or JAR is built.
Arguments: <java8-bin-dir> <this-dir> <planner-dir> <transaction-dir> <asset-b64>.
Run `df -h /` before running: this writes small temporary .java/.class files.
"""
import os, subprocess, sys, tempfile
from pathlib import Path
jdk, here, planner, txn, asset = map(Path, sys.argv[1:])
files = {
'net/minecraft/block/state/IBlockState.java': '''package net.minecraft.block.state; import net.minecraft.block.Block; public interface IBlockState {Block getBlock();}''',
'net/minecraft/block/Block.java': '''package net.minecraft.block; import net.minecraft.block.state.IBlockState; public class Block {public final int meta; public Block(int m){meta=m;} public int getMetaFromState(IBlockState s){return meta;}}''',
'net/minecraft/block/State.java': '''package net.minecraft.block; import net.minecraft.block.state.IBlockState; public class State implements IBlockState {final Block b; public State(Block b){this.b=b;} public Block getBlock(){return b;}}''',
'net/minecraft/init/Blocks.java': '''package net.minecraft.init; import net.minecraft.block.Block; public class Blocks {public static Block AIR=new Block(0),STONE=new Block(0),GRASS=new Block(0),DIRT=new Block(0),GRAVEL=new Block(0),IRON_ORE=new Block(0);}''',
'net/minecraft/entity/Entity.java': '''package net.minecraft.entity; public class Entity {}''',
'net/minecraft/util/ClassInheritanceMultiMap.java': '''package net.minecraft.util; public class ClassInheritanceMultiMap<T> extends java.util.HashSet<T> {}''',
'net/minecraft/world/chunk/Chunk.java': '''package net.minecraft.world.chunk;
import java.util.*; import net.minecraft.block.*; import net.minecraft.block.state.*; import net.minecraft.entity.*; import net.minecraft.init.Blocks; import net.minecraft.util.ClassInheritanceMultiMap;
public class Chunk {
 public final int x,z; public boolean unloadQueued,loaded=true,thrown=false; public int reads=0;
 public int badx=-1,bady=-1,badz=-1, height=64; public Block bad=null;
 public Map<Object,Object> tiles=new HashMap<>(); public ClassInheritanceMultiMap<Entity>[] entities;
 @SuppressWarnings("unchecked") public Chunk(int x,int z){this.x=x;this.z=z; entities=(ClassInheritanceMultiMap<Entity>[])new ClassInheritanceMultiMap[16]; for(int i=0;i<16;i++)entities[i]=new ClassInheritanceMultiMap<>();}
 public boolean isLoaded(){return loaded;} public Map<Object,Object> getTileEntityMap(){return tiles;} public ClassInheritanceMultiMap<Entity>[] getEntityLists(){return entities;}
 public IBlockState getBlockState(int x,int y,int z){ if(x<0||x>15||z<0||z>15||y<0||y>255)throw new AssertionError("neighbor read"); reads++; if(thrown)throw new IllegalStateException("fault"); if(x==badx&&y==bady&&z==badz)return new State(bad); return new State(y>height?Blocks.AIR:y==height?Blocks.GRASS:y>=height-3?Blocks.DIRT:Blocks.STONE); }
}''',
'com/mikael8367/nexustech/worldgen/ProbeTest.java': '''package com.mikael8367.nexustech.worldgen;
import java.io.*; import java.nio.file.*; import java.util.*; import net.minecraft.world.chunk.Chunk; import net.minecraft.init.Blocks; import net.minecraft.block.Block; import net.minecraft.entity.Entity;
public class ProbeTest {
 static int checks; static void ck(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
 static void eq(NoGlassForgeProbe.Report r,NoGlassForgeProbe.Status s){ck(r.status==s,"expected "+s+" got "+r.status);}
 public static void main(String[] args)throws Exception {
  NoGlassSchematic sc=NoGlassSchematic.load(new ByteArrayInputStream(Base64.getMimeDecoder().decode(Files.readAllBytes(Paths.get(args[0]))))); long seed=42; int cx=0;while(NoGlassPlan.select(seed,cx,0)==null)cx++;
  Chunk c=new Chunk(cx,0); NoGlassForgeProbe.Report r=NoGlassForgeProbe.inspect(c,seed,sc);eq(r,NoGlassForgeProbe.Status.DIAGNOSTIC_PLAN_ONLY);ck(r.plannedCells==640,"plan size");ck(c.reads==14*12*256,"only local bounded reads");
  NoGlassTransactionalGenerator g=new NoGlassTransactionalGenerator(sc); NoGlassForgeProbe b=new NoGlassForgeProbe();ck(g.attempt(b,seed,cx,0)==NoGlassTransactionalGenerator.Result.REJECTED,"commit blocked"); ck(b.acquireAlreadyLoadedFreshChunk(cx,0)==null,"no reservation");try{b.compareAndCommit(cx,0,null,null,null);throw new AssertionError("commit allowed");}catch(UnsupportedOperationException ok){ck(true,"commit denied");}
  eq(NoGlassForgeProbe.inspect(null,seed,sc),NoGlassForgeProbe.Status.INVALID);eq(NoGlassForgeProbe.inspect(c,seed,null),NoGlassForgeProbe.Status.INVALID);
  c.loaded=false;eq(NoGlassForgeProbe.inspect(c,seed,sc),NoGlassForgeProbe.Status.INVALID);c.loaded=true;c.unloadQueued=true;eq(NoGlassForgeProbe.inspect(c,seed,sc),NoGlassForgeProbe.Status.INVALID);c.unloadQueued=false;
  c.tiles.put(new Object(),new Object());eq(NoGlassForgeProbe.inspect(c,seed,sc),NoGlassForgeProbe.Status.LOADED_ENTITY_OR_TILE);c.tiles.clear();c.entities[0].add(new Entity());eq(NoGlassForgeProbe.inspect(c,seed,sc),NoGlassForgeProbe.Status.LOADED_ENTITY_OR_TILE);c.entities[0].clear();
  c.entities[0]=null;eq(NoGlassForgeProbe.inspect(c,seed,sc),NoGlassForgeProbe.Status.LOADED_ENTITY_OR_TILE);c.entities=new Chunk(cx,0).entities;
  c.thrown=true;eq(NoGlassForgeProbe.inspect(c,seed,sc),NoGlassForgeProbe.Status.READ_FAILED);c.thrown=false;
  NoGlassPlan.Candidate pick=NoGlassPlan.select(seed,cx,0);c.badx=pick.x;c.badz=pick.z;c.bady=65;c.bad=new Block(0);eq(NoGlassForgeProbe.inspect(c,seed,sc),NoGlassForgeProbe.Status.UNKNOWN_BLOCK);
  c.bad=Blocks.GRASS;eq(NoGlassForgeProbe.inspect(c,seed,sc),NoGlassForgeProbe.Status.TERRAIN_REJECTED);
  Block realGrass=Blocks.GRASS; Blocks.GRASS=new Block(1); c.bad=Blocks.GRASS; eq(NoGlassForgeProbe.inspect(c,seed,sc),NoGlassForgeProbe.Status.UNKNOWN_BLOCK); Blocks.GRASS=realGrass;
  c.bad=new Block(0);c.badx=pick.x-2;c.badz=pick.z-2;c.bady=0;eq(NoGlassForgeProbe.inspect(c,seed,sc),NoGlassForgeProbe.Status.UNKNOWN_BLOCK);
  c.badx=pick.x+11;c.badz=pick.z+9;c.bady=255;eq(NoGlassForgeProbe.inspect(c,seed,sc),NoGlassForgeProbe.Status.UNKNOWN_BLOCK);
  c.badx=-1; c.height=251;eq(NoGlassForgeProbe.inspect(c,seed,sc),NoGlassForgeProbe.Status.TERRAIN_REJECTED);
  Chunk extreme=new Chunk(Integer.MAX_VALUE,Integer.MIN_VALUE);eq(NoGlassForgeProbe.inspect(extreme,seed,sc),NoGlassForgeProbe.Status.INVALID);ck(extreme.reads==0,"extreme no read");
  Chunk sub=new Chunk(cx,0){};eq(NoGlassForgeProbe.inspect(sub,seed,sc),NoGlassForgeProbe.Status.INVALID);
  int unselected=0;while(NoGlassPlan.select(seed,unselected,0)!=null)unselected++;Chunk n=new Chunk(unselected,0);eq(NoGlassForgeProbe.inspect(n,seed,sc),NoGlassForgeProbe.Status.NOT_SELECTED);ck(n.reads==0,"noncandidate no read");
  int found=0;for(int x=-1000;x<=1000 && found<75;x++)if(NoGlassPlan.select(seed,x,-3)!=null){Chunk q=new Chunk(x,-3);eq(NoGlassForgeProbe.inspect(q,seed,sc),NoGlassForgeProbe.Status.DIAGNOSTIC_PLAN_ONLY);ck(q.reads==43008,"bounded all candidates");ck(g.attempt(b,seed,x,-3)==NoGlassTransactionalGenerator.Result.REJECTED,"always blocked");found++;}ck(found==75,"candidate sample");
  System.out.println("PASS stub-based Forge adapter: "+checks+" assertions, 75 selected chunks; no mutation API");
 }
}'''
}
with tempfile.TemporaryDirectory(prefix='nexus-forge-probe-') as td:
    root=Path(td); src=root/'src'; out=root/'classes'; out.mkdir()
    paths=[]
    for name, text in files.items():
        p=src/name; p.parent.mkdir(parents=True,exist_ok=True); p.write_text(text); paths.append(str(p))
    paths += [str(planner/'NoGlassSchematic.java'),str(planner/'NoGlassPlan.java'),str(txn/'NoGlassTransactionalGenerator.java'),str(here/'NoGlassForgeProbe.java')]
    subprocess.run([str(jdk/'javac'),'-source','8','-target','8','-d',str(out)]+paths,check=True)
    subprocess.run([str(jdk/'java'),'-cp',str(out),'com.mikael8367.nexustech.worldgen.ProbeTest',str(asset)],check=True)
