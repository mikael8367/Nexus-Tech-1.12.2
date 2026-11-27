package com.mikael8367.nexustech.worldgen;
import java.io.*;
import java.util.*;
import net.minecraft.nbt.*;
/** Offline only: no Forge world or terrain. Input argument is original attachment. */
public final class NovaSmallTest {
 static int checks;
 static void ok(boolean v){checks++; if(!v)throw new AssertionError("check "+checks);}
 static void bad(Runnable r){try{r.run();throw new AssertionError("accepted invalid");}catch(IllegalArgumentException e){checks++;}}
 static class Sim implements NovaPlacement.SimulationBackend {
  Map<String,NovaPlacement.Entry> m=new HashMap<>(); int calls,fail=-1; boolean after,compat=true,available=true,replace=true,unlockFail=false;
  String key(NovaPlacement.Entry e){return e.x+":"+e.y+":"+e.z;}
  public void lock(){} public void unlock(){if(unlockFail)throw new IllegalStateException();}
  public boolean authorisedChunkAvailable(int x,int z){return available && x==-1&&z==-1;}
  public boolean compatible(NovaPlacement.Entry e){ok((e.x>>4)==-1 && (e.z>>4)==-1); return compat;}
  public boolean equal(NovaPlacement.Entry e){NovaPlacement.Entry a=m.get(key(e));return a!=null && a.id==e.id && a.metadata==e.metadata && Objects.equals(a.tileCopy(),e.tileCopy());}
  public boolean mayReplace(NovaPlacement.Entry e){return replace && !m.containsKey(key(e));}
  public void write(NovaPlacement.Entry e){int i=calls++; if(i==fail&&!after)throw new IllegalStateException();m.put(key(e),e);if(i==fail)throw new IllegalStateException();}
 }
 public static void main(String[] args)throws Exception {
  NovaSchematic.Resource typ=NovaSchematic.Resource.ANEXO_5X2X5;
  if(args.length>0 && args[0].equals("checksum-negative")) {try { NovaSchematic.loadResource(typ); throw new AssertionError("tampered accepted"); } catch(IOException e) { if(!"resource checksum mismatch".equals(e.getMessage())) throw e; } System.out.println("PASS checksum mismatch rejected"); return;}
  NovaSchematic s=NovaSchematic.loadResource(typ); ok(s.width()==5&&s.height()==2&&s.length()==5&&s.volume()==50);
  NBTTagCompound orig;try(InputStream in=new FileInputStream(args[0])){orig=CompressedStreamTools.readCompressed(in);}
  byte[] raw;try(InputStream in=NovaSmallTest.class.getResourceAsStream("/assets/nexustech/structures/nova_anexo_5x2x5_3c17a590.schematic.b64")){ByteArrayOutputStream b=new ByteArrayOutputStream();int c;while((c=in.read())!=-1)b.write(c);raw=Base64.getMimeDecoder().decode(b.toByteArray());}
  NBTTagCompound res=CompressedStreamTools.readCompressed(new ByteArrayInputStream(raw));ok(orig.equals(res));
  byte[] blocks=orig.getByteArray("Blocks"), data=orig.getByteArray("Data");byte[] add=orig.hasKey("AddBlocks",7)?orig.getByteArray("AddBlocks"):new byte[25];
  List<NovaSchematic.Cell> cells=s.snapshot();ok(cells.size()==50);
  NovaPlacement.Plan p=NovaPlacement.plan(s,-16,254,-16,-1,-1,256);ok(p.entries().size()==50);
  Map<String,Integer> counts=new TreeMap<>();
  for(int i=0;i<50;i++){NovaSchematic.Cell c=cells.get(i);NovaPlacement.Entry e=p.entries().get(i);int id=(blocks[i]&255)|((((add[i/2]&255)>>>((i&1)*4))&15)<<8);int meta=data[i]&255;
   ok(i==(c.y*5+c.z)*5+c.x);ok(c.id==id&&c.metadata==meta&&c.tileCopy()==null);ok(e.id==id&&e.metadata==meta&&e.tileCopy()==null&&e.x==-16+c.x&&e.y==254+c.y&&e.z==-16+c.z);String k=id+":"+meta;counts.put(k,counts.getOrDefault(k,0)+1);
  }
  for(int margin=0;margin<=7;margin++)for(int x=0;x<16;x++)for(int z=0;z<16;z++){
   boolean yes=x>=margin&&z>=margin&&x+4+margin<16&&z+4+margin<16;
   try{NovaPlacement.Plan q=NovaPlacement.planWithMargin(s,x-16,0,z-16,-1,-1,256,margin);ok(yes&&q.entries().size()==50);}catch(IllegalArgumentException e){ok(!yes);}
  }
  bad(()->NovaPlacement.plan(s,-4,0,-16,-1,-1,256));bad(()->NovaPlacement.plan(s,-16,255,-16,-1,-1,256));bad(()->NovaPlacement.plan(s,-16,-1,-16,-1,-1,256));bad(()->NovaPlacement.plan(s,Integer.MAX_VALUE,0,0,Integer.MAX_VALUE>>4,0,256));bad(()->NovaPlacement.planWithMargin(s,Integer.MIN_VALUE,0,0,Integer.MIN_VALUE>>4,0,256,1));bad(()->NovaPlacement.plan(s,-16,0,-16,0,-1,256));bad(()->NovaPlacement.planWithMargin(s,-16,0,-16,-1,-1,256,1));bad(()->NovaPlacement.plan(s,-16,0,-16,-1,-1,257));
  ok(NovaPlacement.plan(s,-16,0,-16,-1,-1,2).entries().size()==50);
  bad(()->NovaSchematic.inspect(orig));bad(()->NovaSchematic.inspect(orig,NovaSchematic.Resource.ANEXO_11X14X11));
  NBTTagCompound n=orig.copy();n.setByteArray("Blocks",new byte[49]);bad(()->NovaSchematic.inspect(n,typ));
  NBTTagCompound n2=orig.copy();n2.getByteArray("Data")[0]=16;bad(()->NovaSchematic.inspect(n2,typ));
  NBTTagCompound n3=orig.copy();n3.getByteArray("Blocks")[0]=20;bad(()->NovaSchematic.inspect(n3,typ));
  NBTTagCompound n4=orig.copy();NBTTagList ts=new NBTTagList();NBTTagCompound tile=new NBTTagCompound();ts.appendTag(tile);n4.setTag("TileEntities",ts);bad(()->NovaSchematic.inspect(n4,typ));
  Sim sim=new Sim();NovaPlacement.Outcome o=NovaPlacement.simulate(p,sim);ok(o.result==NovaPlacement.Result.APPLIED&&o.completedWrites==50&&sim.m.size()==50);o=NovaPlacement.simulate(p,sim);ok(o.result==NovaPlacement.Result.ALREADY_PRESENT&&o.completedWrites==0&&sim.calls==50);
  for(int i:new int[]{0,1,25,49})for(boolean after:new boolean[]{false,true}){Sim f=new Sim();f.fail=i;f.after=after;o=NovaPlacement.simulate(p,f);ok(o.result==NovaPlacement.Result.PARTIAL_FAILURE&&o.completedWrites==i&&f.m.size()==i+(after?1:0));if(i>0||after){f.fail=-1;o=NovaPlacement.simulate(p,f);ok(o.result==((i==49&&after)?NovaPlacement.Result.ALREADY_PRESENT:NovaPlacement.Result.REJECTED)&&o.completedWrites==0);}}
  Sim c=new Sim();c.compat=false;o=NovaPlacement.simulate(p,c);ok(o.result==NovaPlacement.Result.REJECTED&&c.calls==0);Sim u=new Sim();u.unlockFail=true;o=NovaPlacement.simulate(p,u);ok(o.result==NovaPlacement.Result.PARTIAL_FAILURE&&o.completedWrites==50);
  ok(!NovaPlacement.FORGE_GENERATION_ENABLED);System.out.println("PASS "+checks+" checks states="+counts+" offsets="+Arrays.toString(s.worldEditOffsets()));
 }
}