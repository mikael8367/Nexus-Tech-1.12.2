#!/usr/bin/env python3
"""Run adapter against stub API; proves direct bounds/guards only, NOT real Forge lighting."""
from pathlib import Path
import os, shutil, subprocess
P=Path(__file__).resolve().parents[1]
R=P.parent/'structure-stub'; S=R/'src'; C=R/'classes'
files={
'net/minecraft/block/Block.java':'''package net.minecraft.block;
import net.minecraft.block.state.IBlockState;
public class Block { public final int id; private static final Block[] registry=new Block[256]; public Block(int id){this.id=id;registry[id]=this;} public static Block getBlockById(int id){return registry[id];} public IBlockState getStateFromMeta(int m){return new IBlockState(this,m);} public int getMetaFromState(IBlockState s){return s.meta;} public boolean hasTileEntity(IBlockState s){return false;} }
''',
'net/minecraft/block/state/IBlockState.java':'''package net.minecraft.block.state; import net.minecraft.block.Block;
public class IBlockState { private final Block block; public final int meta; public IBlockState(Block b,int m){block=b;meta=m;} public Block getBlock(){return block;} }
''',
'net/minecraft/init/Blocks.java':'''package net.minecraft.init; import net.minecraft.block.Block;
public class Blocks {public static final Block AIR=new Block(0),STONE=new Block(1),GRASS=new Block(2),DIRT=new Block(3),BEDROCK=new Block(7),STONE_SLAB=new Block(44),BRICK_BLOCK=new Block(45),BRICK_STAIRS=new Block(108),CHEST=new Block(54),SAND=new Block(12),GRAVEL=new Block(13),GOLD_ORE=new Block(14),IRON_ORE=new Block(15),COAL_ORE=new Block(16),LAPIS_ORE=new Block(21),TALLGRASS=new Block(31),YELLOW_FLOWER=new Block(37),RED_FLOWER=new Block(38),BROWN_MUSHROOM=new Block(39),RED_MUSHROOM=new Block(40),DIAMOND_ORE=new Block(56),REDSTONE_ORE=new Block(73),LIT_REDSTONE_ORE=new Block(74),EMERALD_ORE=new Block(129);}
''',
'net/minecraft/util/math/BlockPos.java':'''package net.minecraft.util.math; public class BlockPos { public final int x,y,z; public BlockPos(int x,int y,int z){this.x=x;this.y=y;this.z=z;} }
''',
'net/minecraft/world/gen/IChunkGenerator.java':'package net.minecraft.world.gen; public interface IChunkGenerator {}',
'net/minecraft/world/chunk/IChunkProvider.java':'''package net.minecraft.world.chunk; public interface IChunkProvider { Chunk getLoadedChunk(int x,int z); }''',
'net/minecraftforge/fml/common/IWorldGenerator.java':'''package net.minecraftforge.fml.common; import java.util.Random;import net.minecraft.world.World;import net.minecraft.world.gen.IChunkGenerator;import net.minecraft.world.chunk.IChunkProvider;
public interface IWorldGenerator {void generate(Random r,int x,int z,World w,IChunkGenerator g,IChunkProvider p);}''',
'net/minecraft/world/chunk/Chunk.java':'''package net.minecraft.world.chunk;
import net.minecraft.block.state.IBlockState;import net.minecraft.init.Blocks;import net.minecraft.util.math.BlockPos;
public class Chunk {public enum EnumCreateEntityType {CHECK} public int cx,cz; public IBlockState[][][] a=new IBlockState[16][256][16]; public boolean[][][] tiles=new boolean[16][256][16];
 public Chunk(int x,int z){cx=x;cz=z;for(int i=0;i<16;i++)for(int k=0;k<16;k++)for(int y=0;y<256;y++)a[i][y][k]=(y==0?Blocks.BEDROCK:y<64?Blocks.DIRT:y==64?Blocks.GRASS:Blocks.AIR).getStateFromMeta(0);}
 void bound(BlockPos p){if((p.x>>4)!=cx||(p.z>>4)!=cz||p.y<0||p.y>255) throw new AssertionError("bad coordinates");}
 public IBlockState getBlockState(BlockPos p){bound(p);return a[p.x&15][p.y][p.z&15];}
 public Object getTileEntity(BlockPos p,EnumCreateEntityType type){bound(p);return tiles[p.x&15][p.y][p.z&15]?new Object():null;}
 public void set(BlockPos p,IBlockState s){bound(p);a[p.x&15][p.y][p.z&15]=s;}
}''',
'net/minecraft/world/World.java':'''package net.minecraft.world; import net.minecraft.block.state.IBlockState;import net.minecraft.util.math.BlockPos;import net.minecraft.world.chunk.Chunk;
public class World {public boolean isRemote; public Provider provider=new Provider();public static class Provider{public int dimension;public int getDimension(){return dimension;}} public long seed=10;public Chunk chunk;public int writes;
 public long getSeed(){return seed;} public boolean setBlockState(BlockPos p,IBlockState state,int flags){if(flags!=18)throw new AssertionError("flags");chunk.set(p,state);writes++;return true;}
}''',
'AdapterStubTest.java':'''import java.util.*;import net.minecraft.world.World;import net.minecraft.world.chunk.*;import net.minecraft.init.Blocks;import com.mikael8367.nexustech.worldgen.*;
public class AdapterStubTest {
 static void check(boolean v,String msg){if(!v)throw new AssertionError(msg);} static class Provider implements IChunkProvider {Chunk c;int calls;Provider(Chunk c){this.c=c;}public Chunk getLoadedChunk(int x,int z){calls++;if(c!=null&&(x!=c.cx||z!=c.cz))throw new AssertionError("neighbor access");return c;}}
 public static void main(String[] a) throws Exception {int cx=-1,cz=-100;StructurePlan.Candidate found=null;for(cx=-2000;cx<0;cx++){found=StructurePlan.select(10,cx,cz);if(found!=null)break;}check(found!=null,"candidate");final int x=cx,z=cz; World w=new World();w.chunk=new Chunk(x,z);Provider p=new Provider(w.chunk);ExperimentalStructureGenerator g=new ExperimentalStructureGenerator();
 g.generate(new Random(),x,z,w,null,p);check(w.writes>0&&p.calls==1,"generation and loaded-only lookup");ExactSchematic s=ExactSchematic.load(AdapterStubTest.class.getResourceAsStream("/assets/nexustech/structures/original.schematic"));
 for(int y=0;y<5;y++)for(int dz=0;dz<6;dz++)for(int dx=0;dx<6;dx++){net.minecraft.block.state.IBlockState b=w.chunk.a[found.x+dx][62+y][found.z+dz];check(b.getBlock().id==s.id(dx,y,dz)&&b.meta==s.meta(dx,y,dz),"exact adapter output");}
 World t=new World();t.chunk=new Chunk(x,z);t.chunk.tiles[found.x+1][63][found.z+1]=true;g.generate(new Random(),x,z,t,null,new Provider(t.chunk));check(t.writes==0,"tile");
 World edge=new World();edge.chunk=new Chunk(x,z);edge.chunk.a[0][65][0]=Blocks.CHEST.getStateFromMeta(0);Provider pe=new Provider(edge.chunk);g.generate(new Random(),x,z,edge,null,pe);check(edge.writes==0&&pe.calls==1,"nearby building edge");
 World cave=new World();cave.chunk=new Chunk(x,z);cave.chunk.a[15][20][15]=Blocks.AIR.getStateFromMeta(0);Provider pc=new Provider(cave.chunk);g.generate(new Random(),x,z,cave,null,pc);check(cave.writes>0&&pc.calls==1,"deep cave tolerated");
 World d=new World();d.provider.dimension=-1;Provider pd=new Provider(null);g.generate(new Random(),x,z,d,null,pd);check(pd.calls==0&&d.writes==0,"dimension");d.provider.dimension=0;d.isRemote=true;g.generate(new Random(),x,z,d,null,pd);check(pd.calls==0&&d.writes==0,"remote");d.isRemote=false;g.generate(new Random(),x,z,d,null,pd);check(pd.calls==1&&d.writes==0,"unloaded");
 System.out.println("PASS adapter stubs: negative chunk, exact 180 states, chunk-local direct reads/writes, flags 18, unloaded/dimension/client/tile guards");System.out.println("LIMIT: stubs do not model Forge internals, callbacks, lighting, other mods or persistence"); }
}'''
}
for path,text in files.items():
    p=S/path;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(text)
C.mkdir(parents=True,exist_ok=True)
java_home=os.environ.get('JAVA_HOME')
javac=str(Path(java_home)/'bin/javac') if java_home else shutil.which('javac')
java=str(Path(java_home)/'bin/java') if java_home else shutil.which('java')
if not javac or not java: raise RuntimeError('Set JAVA_HOME to a JDK with javac and java')
sources=list(S.rglob('*.java'))+list((P/'src/main/java/com/mikael8367/nexustech/worldgen').glob('*.java'))
subprocess.run([javac,'-source','8','-target','8','-d',str(C)]+list(map(str,sources)),check=True)
subprocess.run([java,'-cp',str(C)+':'+str(P/'src/main/resources'),'AdapterStubTest'],check=True,cwd=P)
