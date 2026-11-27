package com.mikael8367.nexustech.worldgen;

import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;

/** Tests a real mapped ChunkPrimer in a partial, artificial block bootstrap, not a game. */
public final class Fazenda5DeepPrimerTest {
 static long checks=0; static int cases=0;
 static void ok(boolean v,String m){checks++;if(!v)throw new AssertionError(m);}
 interface Task {void run() throws Exception;}
 static void rejects(Task t,String label){try{t.run();throw new AssertionError("accepted "+label);}catch(IllegalArgumentException expected){checks++;}catch(Exception e){throw new AssertionError(label,e);}}
 static IBlockState[] base(int x,int y,int z){IBlockState[] a=new IBlockState[Fazenda5ForgePrimer.SIZE];Arrays.fill(a,Blocks.AIR.getDefaultState());for(int dx=0;dx<5;dx++)for(int dz=0;dz<5;dz++)a[Fazenda5ForgePrimer.index((x+dx)&15,y-1,(z+dz)&15)]=Blocks.STONE.getDefaultState();return a;}
 static int idx(int x,int y,int z){return Fazenda5ForgePrimer.index(x&15,y,z&15);}
 public static void main(String[] args)throws Exception {
  final Fazenda5ForgePrimer.Mapping h=Fazenda5ForgePrimer.Mapping.VANILLA_HYPOTHESIS_ONLY;
  NovaSchematic s=NovaSchematic.loadResource(NovaSchematic.Resource.ANEXO_5X2X5);
  final int x=-15,y=64,z=33,cx=-1,cz=2;
  List<NovaPlacement.Entry> es=NovaPlacement.plan(s,x,y,z,cx,cz,256).entries();
  IBlockState[] a=base(x,y,z), original=a.clone();
  for(int i=0;i<50;i++){
   NovaPlacement.Entry e=es.get(i); int n=idx(e.x,e.y,e.z); a[n]=Blocks.GLASS.getDefaultState();
   rejects(()->Fazenda5ForgePrimer.run(a,x,y,z,cx,cz,h),"collision at "+n); a[n]=original[n]; cases++;
  }
  for(int dx=0;dx<5;dx++)for(int dz=0;dz<5;dz++){
   int n=idx(x+dx,y-1,z+dz); a[n]=Blocks.AIR.getDefaultState();
   rejects(()->Fazenda5ForgePrimer.run(a,x,y,z,cx,cz,h),"support at "+n);a[n]=original[n];cases++;
  }
  System.err.println("DEEP: completed 50 collisions and 25 support checks");
  for(int i=0;i<50;i++)for(boolean after:new boolean[]{false,true}){
   if(!after && i%10==0) System.err.println("DEEP: injection index="+i);
   try {Fazenda5ForgePrimer.runForTest(a,x,y,z,cx,cz,h,i,after);throw new AssertionError("injection missing "+i);}catch(IllegalStateException expected){checks++;}
   ok(Arrays.equals(a,original),"source changed after injection "+i);cases++;
  }
  int[][] positions={{0,1,0},{11,254,11},{-16,1,-16},{-5,254,-5},{-15,64,33},{Integer.MIN_VALUE,10,Integer.MIN_VALUE},{Integer.MAX_VALUE-4,200,Integer.MAX_VALUE-4}};
  Random rng=new Random(0x5A17C0DEL); int randomWrites=200;
  for(int[] pos:positions){
   int px=pos[0],py=pos[1],pz=pos[2],pcx=px>>4,pcz=pz>>4;
   IBlockState[] seed=base(px,py,pz); boolean[] protectedArea=new boolean[seed.length];
   for(int dx=0;dx<5;dx++)for(int dz=0;dz<5;dz++)for(int dy=-1;dy<2;dy++)protectedArea[idx(px+dx,py+dy,pz+dz)]=true;
   IBlockState[] palette={Blocks.GLASS.getDefaultState(),Blocks.DIRT.getStateFromMeta(1),Blocks.WATER.getDefaultState(),Blocks.FARMLAND.getDefaultState(),Blocks.WHEAT.getDefaultState(),Blocks.OAK_FENCE.getDefaultState(),Blocks.STONE.getDefaultState()};
   for(int n=0;n<randomWrites;n++){int j;do{j=rng.nextInt(seed.length);}while(protectedArea[j]);seed[j]=palette[rng.nextInt(palette.length)];}
   IBlockState[] expected=seed.clone(), copy=seed.clone();
   for(NovaPlacement.Entry e:NovaPlacement.plan(s,px,py,pz,pcx,pcz,256).entries())expected[idx(e.x,e.y,e.z)]=Fazenda5ForgePrimer.resolve(e.id,e.metadata);
   Fazenda5ForgePrimer.Result r=Fazenda5ForgePrimer.run(seed,px,py,pz,pcx,pcz,h);
   ok(Arrays.equals(seed,copy),"seed mutated");
   for(int lx=0;lx<16;lx++)for(int lz=0;lz<16;lz++)for(int ly=0;ly<256;ly++)ok(r.get(lx,ly,lz)==expected[Fazenda5ForgePrimer.index(lx,ly,lz)],"all cells at "+Arrays.toString(pos));
   seed[0]=seed[0]==Blocks.AIR.getDefaultState()?Blocks.STONE.getDefaultState():Blocks.AIR.getDefaultState();
   ok(r.get(0,0,0)==expected[0],"result not isolated");
   rejects(()->Fazenda5ForgePrimer.run(expected,px,py,pz,pcx,pcz,h),"reapply is rejected, not idempotent success");
   cases++;
  }
  for(int id:new int[]{0,2,3,9,59,60,85})for(int meta=0;meta<16;meta++){
   final int fid=id,fmeta=meta;
   if(meta==0||id==3&&meta==1){IBlockState t=Fazenda5ForgePrimer.resolve(id,meta);ok(Block.getIdFromBlock(t.getBlock())==id && t.getBlock().getMetaFromState(t)==meta,"mapping "+id+":"+meta);}else rejects(()->Fazenda5ForgePrimer.resolve(fid,fmeta),"invalid palette metadata");cases++;
  }
  for(int id:new int[]{-1,1,8,20,255,256,Integer.MAX_VALUE}){final int n=id;rejects(()->Fazenda5ForgePrimer.resolve(n,0),"unknown id "+n);cases++;}
  rejects(()->Fazenda5ForgePrimer.run(a,-4,y,z,cx,cz,h),"chunk crossing");cases++;
  rejects(()->Fazenda5ForgePrimer.run(a,Integer.MAX_VALUE,y,z,Integer.MAX_VALUE>>4,cz,h),"coordinate overflow");cases++;
  rejects(()->Fazenda5ForgePrimer.run(a,x,255,z,cx,cz,h),"height overflow");cases++;
  rejects(()->Fazenda5ForgePrimer.index(-1,0,0),"index negative");cases++;
  rejects(()->Fazenda5ForgePrimer.index(0,256,0),"index y");cases++;
  ok(!NovaPlacement.FORGE_GENERATION_ENABLED,"generation enabled");
  System.out.println("DEEP PASS: "+cases+" scenarios; "+checks+" assertions; seed=0x5A17C0DE; 7 full snapshots x 65536; all 50 collisions, 25 support, 100 injection failures, reapply rejected; no World or JAR");
 }
}
