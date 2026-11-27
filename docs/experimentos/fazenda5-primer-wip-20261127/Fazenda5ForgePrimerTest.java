package com.mikael8367.nexustech.worldgen;
import java.util.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Blocks;

/** Standalone offline test against actual mapped Forge ChunkPrimer, not a server. */
public final class Fazenda5ForgePrimerTest {
 static int checks;
 static void ok(boolean v,String m){checks++;if(!v)throw new AssertionError(m);}
 interface Checked {void run() throws Exception;}
 static void bad(Checked r){try{r.run();throw new AssertionError("accepted invalid");}catch(IllegalArgumentException expected){checks++;}catch(Exception e){throw new AssertionError(e);}}
 static IBlockState[] terrain(int x,int y,int z){IBlockState[] a=new IBlockState[Fazenda5ForgePrimer.SIZE];Arrays.fill(a,Blocks.AIR.getDefaultState());for(int dx=0;dx<5;dx++)for(int dz=0;dz<5;dz++)a[Fazenda5ForgePrimer.index((x+dx)&15,y-1,(z+dz)&15)]=Blocks.STONE.getDefaultState();return a;}
 public static void main(String[] args)throws Exception {
  Bootstrap.register(); final int x=-15,y=64,z=33,cx=-1,cz=2;
  final Fazenda5ForgePrimer.Mapping h=Fazenda5ForgePrimer.Mapping.VANILLA_HYPOTHESIS_ONLY;
  final IBlockState[] a=terrain(x,y,z);a[Fazenda5ForgePrimer.index(15,10,15)]=Blocks.GLASS.getDefaultState();
  final IBlockState[] original=a.clone();
  Fazenda5ForgePrimer.Result result=Fazenda5ForgePrimer.run(a,x,y,z,cx,cz,h);
  ok(Arrays.equals(a,original),"input unchanged");
  NovaSchematic s=NovaSchematic.loadResource(NovaSchematic.Resource.ANEXO_5X2X5);
  NovaPlacement.Plan plan=NovaPlacement.plan(s,x,y,z,cx,cz,256);
  ok(plan.entries().size()==50,"50 cells"); Map<String,Integer> counts=new TreeMap<>();
  for(NovaPlacement.Entry e:plan.entries()) {
   IBlockState actual=result.get(e.x&15,e.y,e.z&15);
   ok(actual==Fazenda5ForgePrimer.resolve(e.id,e.metadata),"cell");
   ok(actual.getBlock().getMetaFromState(actual)==e.metadata,"metadata");
   String key=e.id+":"+e.metadata;counts.put(key,counts.containsKey(key)?counts.get(key)+1:1);
  }
  ok(counts.toString().equals("{0:0=13, 2:0=10, 3:0=6, 3:1=4, 59:0=2, 60:0=4, 85:0=10, 9:0=1}"),"palette counts "+counts);
  for(int px=0;px<16;px++)for(int pz=0;pz<16;pz++)for(int py=0;py<256;py++){
   boolean inside=px>=1&&px<6&&pz>=1&&pz<6&&py>=y&&py<y+2;
   if(!inside)ok(result.get(px,py,pz)==original[Fazenda5ForgePrimer.index(px,py,pz)],"outside changed");
  }
  a[0]=Blocks.STONE.getDefaultState();ok(result.get(0,0,0)==original[0],"snapshot isolation");a[0]=original[0];
  for(int i:new int[]{0,1,25,49})for(boolean after:new boolean[]{false,true}){
   try{Fazenda5ForgePrimer.runForTest(a,x,y,z,cx,cz,h,i,after);throw new AssertionError("failure not injected");}catch(IllegalStateException expected){checks++;}
   ok(Arrays.equals(a,original),"failure changed input");
  }
  bad(()->Fazenda5ForgePrimer.run(a,x,y,z,cx,cz,null));
  bad(()->Fazenda5ForgePrimer.run(null,x,y,z,cx,cz,h));
  bad(()->Fazenda5ForgePrimer.run(new IBlockState[1],x,y,z,cx,cz,h));
  bad(()->Fazenda5ForgePrimer.run(a,x,0,z,cx,cz,h));
  bad(()->Fazenda5ForgePrimer.run(a,x,255,z,cx,cz,h));
  bad(()->Fazenda5ForgePrimer.run(a,-3,y,z,cx,cz,h));
  bad(()->Fazenda5ForgePrimer.run(a,x,y,z,0,cz,h));
  bad(()->Fazenda5ForgePrimer.run(a,Integer.MAX_VALUE,y,z,Integer.MAX_VALUE>>4,cz,h));
  bad(()->Fazenda5ForgePrimer.resolve(20,0));bad(()->Fazenda5ForgePrimer.resolve(9,1));bad(()->Fazenda5ForgePrimer.resolve(3,2));bad(()->Fazenda5ForgePrimer.resolve(59,7));bad(()->Fazenda5ForgePrimer.resolve(85,1));
  int dest=Fazenda5ForgePrimer.index(x&15,y,z&15);a[dest]=Blocks.GLASS.getDefaultState();bad(()->Fazenda5ForgePrimer.run(a,x,y,z,cx,cz,h));a[dest]=original[dest];
  int below=Fazenda5ForgePrimer.index(x&15,y-1,z&15);a[below]=Blocks.AIR.getDefaultState();bad(()->Fazenda5ForgePrimer.run(a,x,y,z,cx,cz,h));a[below]=original[below];
  a[0]=null;bad(()->Fazenda5ForgePrimer.run(a,x,y,z,cx,cz,h));a[0]=original[0];
  ok(Arrays.equals(a,original),"rejections untouched");
  IBlockState[] top=terrain(11,254,11);ok(Fazenda5ForgePrimer.run(top,11,254,11,0,0,h)!=null,"top edge");
  ok(!NovaPlacement.FORGE_GENERATION_ENABLED,"generation disabled");
  System.out.println("PASS "+checks+" checks; private ChunkPrimer; 50 cells "+counts+"; failures before/after; no World or JAR");
 }
}
