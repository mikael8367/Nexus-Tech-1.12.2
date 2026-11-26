package com.mikael8367.nexustech.worldgen;
// UNCOMPILED, UNEXECUTED draft; may contain errors.
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.block.state.IBlockState;
import net.minecraft.world.chunk.ChunkPrimer;
public final class NovaForgeLabTest {
 static int checks; static void ck(boolean v,String msg){checks++;if(!v)throw new AssertionError(msg);}
 static IBlockState[] snap(ChunkPrimer p){ IBlockState[] a=new IBlockState[65536]; for(int x=0;x<16;x++)for(int z=0;z<16;z++)for(int y=0;y<256;y++)a[(x*16+z)*256+y]=p.getBlockState(x,y,z); return a; }
 static void unchanged(ChunkPrimer p,IBlockState[] a){for(int x=0;x<16;x++)for(int z=0;z<16;z++)for(int y=0;y<256;y++)ck(a[(x*16+z)*256+y]==p.getBlockState(x,y,z),"mutation");}
 static ChunkPrimer flat(int x,int z,int y){ChunkPrimer p=new ChunkPrimer();for(int dx=0;dx<6;dx++)for(int dz=0;dz<7;dz++)p.setBlockState((x+dx)&15,y-1,(z+dz)&15,Blocks.STONE.getDefaultState());return p;}
 public static void main(String[] args)throws Exception {
  Bootstrap.register(); int x=-15,y=64,z=33,cx=-1,cz=2; ChunkPrimer p=flat(x,z,y); IBlockState[] before=snap(p);
  NovaForgeLab.Report r=NovaForgeLab.inspect(p,x,y,z,cx,cz);
  System.out.println("verdict="+r.verdict+" states="+r.states+" tiles="+r.tiles+" destination="+r.destinationOK+" support="+r.supportOK);
  ck(r.verdict==NovaForgeLab.Verdict.SUPPORT,"support blocked, actual="+r.verdict);ck(r.states==210&&r.tiles==4&&r.destinationOK&&!r.supportOK,"complete preflight"); unchanged(p,before);
  for(NovaForgeLab.PreparedTile pt:r.tiles()){NBTTagCompound n=pt.nbt(); TileEntity t=NovaForgeLab.instantiateForOfflineTest(pt);ck(!t.hasWorld(),"no world");ck(t.getPos().getX()==n.getInteger("x")&&t.getPos().getY()==n.getInteger("y")&&t.getPos().getZ()==n.getInteger("z"),"absolute xyz"); n.setInteger("x",999); ck(pt.nbt().getInteger("x")!=999,"copy");}
  p.setBlockState(x&15,y,z&15,Blocks.STONE.getDefaultState()); before=snap(p); r=NovaForgeLab.inspect(p,x,y,z,cx,cz);ck(r.verdict==NovaForgeLab.Verdict.TERRAIN,"destination rejection");unchanged(p,before);
  p=flat(x,z,y);p.setBlockState(x&15,y-1,z&15,Blocks.AIR.getDefaultState());r=NovaForgeLab.inspect(p,x,y,z,cx,cz);ck(r.verdict==NovaForgeLab.Verdict.TERRAIN,"support terrain rejection");
  ck(NovaForgeLab.inspect(p,x,0,z,cx,cz).verdict==NovaForgeLab.Verdict.INVALID,"height");ck(NovaForgeLab.inspect(p,-12,y,z,cx,cz).verdict==NovaForgeLab.Verdict.INVALID,"cross chunk");ck(NovaForgeLab.inspect(p,x,y,z,0,cz).verdict==NovaForgeLab.Verdict.INVALID,"wrong chunk");ck(NovaForgeLab.inspect(new ChunkPrimer(){},x,y,z,cx,cz).verdict==NovaForgeLab.Verdict.INVALID,"subclass");
  try {NovaForgeLab.publish();throw new AssertionError("published");}catch(UnsupportedOperationException ok){checks++;}
  System.out.println("PASS real Forge API memory smoke "+checks+" checks; no World, no JAR");
 }
}
