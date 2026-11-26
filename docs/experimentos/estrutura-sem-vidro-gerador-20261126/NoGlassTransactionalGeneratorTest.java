package com.mikael8367.nexustech.worldgen;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Deterministic in-memory simulation, NOT Minecraft, Forge, or an atomicity proof. */
public class NoGlassTransactionalGeneratorTest {
  static int checks=0;
  static void check(boolean ok,String s) { checks++; if(!ok) throw new AssertionError(s); }
  static final int N=16*16*256;
  static int idx(int x,int y,int z) { if(x<0||x>=16||z<0||z>=16||y<0||y>=256) throw new AssertionError("outside chunk"); return (y*16+z)*16+x; }
  static class Snap implements NoGlassTransactionalGenerator.Snapshot {
    byte[] ids=new byte[N], metas=new byte[N]; boolean fresh=true,border=true,entities=false,tiles=false,throwRead=false; byte[] marker;
    Snap copy() { Snap s=new Snap();s.ids=ids.clone();s.metas=metas.clone();s.fresh=fresh;s.border=border;s.entities=entities;s.tiles=tiles;s.throwRead=throwRead;s.marker=marker==null?null:marker.clone();return s; }
    public int id(int x,int y,int z){if(throwRead)throw new IllegalStateException("read");return ids[idx(x,y,z)]&255;}
    public int meta(int x,int y,int z){return metas[idx(x,y,z)]&255;}
    public boolean hasTile(int x,int y,int z){idx(x,y,z);return tiles;}
    public boolean freshUnmodified(){return fresh;}
    public boolean insideWorldBorder(){return border;}
    public boolean hasAnyEntityOrTile(){return entities||tiles;}
    public byte[] marker(){return marker==null?null:marker.clone();}
    void set(int x,int y,int z,int id,int meta){ids[idx(x,y,z)]=(byte)id;metas[idx(x,y,z)]=(byte)meta;}
  }
  static Snap flat(int surface) { Snap s=new Snap(); for(int x=0;x<16;x++)for(int z=0;z<16;z++)for(int y=0;y<=surface;y++)s.set(x,y,z,y==surface?2:(y>=surface-3?3:1),0);return s; }
  static class Backend implements NoGlassTransactionalGenerator.Backend {
    Snap live; boolean unavailable,conflict,throwAcquire,throwCommit,mutateBeforeCompare,throwSnapshot,nullSnapshot,throwClose,throwError; int failAt=-1; int acquires,commits,writes,releases; boolean held;
    final IllegalStateException acquireException=new IllegalStateException("acquire"), snapshotException=new IllegalStateException("snapshot"), commitException=new IllegalStateException("commit"), closeException=new IllegalStateException("close");
    final AssertionError seriousException=new AssertionError("serious");
    Backend(Snap s){live=s;}
    public NoGlassTransactionalGenerator.Reservation acquireAlreadyLoadedFreshChunk(int x,int z){
      acquires++;if(throwAcquire)throw acquireException;if(unavailable)return null;
      if(held)throw new AssertionError("already held");held=true; final Snap detached=live.copy();
      return new NoGlassTransactionalGenerator.Reservation(){
        public NoGlassTransactionalGenerator.Snapshot snapshot(){if(throwError)throw seriousException;if(throwSnapshot)throw snapshotException;return nullSnapshot?null:detached;}
        public void close(){if(!held)throw new AssertionError("double release");held=false;releases++;if(throwClose)throw closeException;}
      };
    }
    public boolean compareAndCommit(int x,int z,NoGlassTransactionalGenerator.Snapshot expected,List<NoGlassPlan.Cell> plan,byte[] marker){
      if(!held)throw new AssertionError("commit without reservation");
      commits++; if(throwCommit)throw commitException; Snap before=(Snap)expected;
      if(mutateBeforeCompare)live.set(0,10,0,5,0); // concurrent external edit, not ours
      if(conflict || !live.fresh || !live.border || live.entities || live.tiles || live.marker!=null || !Arrays.equals(before.ids,live.ids) || !Arrays.equals(before.metas,live.metas))return false;
      Snap after=live.copy(); int staged=0; for(NoGlassPlan.Cell c:plan){if(staged==failAt)throw new IllegalStateException("injected staging failure");after.set(c.x,c.y,c.z,c.id,c.meta);staged++;} if(staged==failAt)throw new IllegalStateException("injected marker failure"); after.marker=marker.clone();after.fresh=false; live=after;writes+=staged;return true;
    }
  }
  static byte[] save(Snap s)throws Exception { ByteArrayOutputStream b=new ByteArrayOutputStream(); DataOutputStream d=new DataOutputStream(b);d.write(s.ids);d.write(s.metas);d.writeInt(s.marker.length);d.write(s.marker);d.writeBoolean(s.fresh);return b.toByteArray(); }
  static Snap reload(byte[] b)throws Exception {DataInputStream d=new DataInputStream(new ByteArrayInputStream(b));Snap s=new Snap();d.readFully(s.ids);d.readFully(s.metas);int n=d.readInt(); if(n<0||n>4096)throw new IOException();s.marker=new byte[n];d.readFully(s.marker);s.fresh=d.readBoolean();if(d.read()!=-1)throw new IOException();return s;}
  static void nbtCheck(byte[] b,long seed,int cx,int cz)throws Exception {
    DataInputStream d=new DataInputStream(new ByteArrayInputStream(b));check(d.readUnsignedByte()==10 && d.readUTF().equals("NexusNoGlass"),"NBT root");
    check(d.readUnsignedByte()==3 && d.readUTF().equals("Version") && d.readInt()==1,"NBT version");
    check(d.readUnsignedByte()==4 && d.readUTF().equals("Seed") && d.readLong()==seed,"NBT seed");
    check(d.readUnsignedByte()==3 && d.readUTF().equals("ChunkX") && d.readInt()==cx,"NBT x");
    check(d.readUnsignedByte()==3 && d.readUTF().equals("ChunkZ") && d.readInt()==cz,"NBT z");
    check(d.readUnsignedByte()==8 && d.readUTF().equals("AssetSHA256") && d.readUTF().equals("5333f61ae7f814e0632e03909b219af91e89480b8b78adaabbb9db35b4cdaf22"),"NBT asset");
    check(d.readUnsignedByte()==0 && d.read()==-1,"NBT end");
  }
  public static void main(String[] a)throws Exception {
    byte[] raw=Base64.getMimeDecoder().decode(Files.readAllBytes(Paths.get(a[0])));
    NoGlassSchematic sc=NoGlassSchematic.load(new ByteArrayInputStream(raw)); NoGlassTransactionalGenerator g=new NoGlassTransactionalGenerator(sc);
    long seed=42;int cx=0,cz=0; while(NoGlassPlan.select(seed,cx,cz)==null)cx++;
    NoGlassPlan.Candidate c=NoGlassPlan.select(seed,cx,cz);
    Backend b=new Backend(flat(64));NoGlassTransactionalGenerator.Result result=g.attempt(b,seed,cx,cz);
    check(result==NoGlassTransactionalGenerator.Result.COMMITTED,"flat placement "+result);
    check(b.writes==640 && b.commits==1,"640 atomic staged writes");
    for(int y=0;y<8;y++)for(int z=0;z<8;z++)for(int x=0;x<10;x++) {check(b.live.id(c.x+x,62+y,c.z+z)==sc.id(x,y,z),"asset id");check(b.live.meta(c.x+x,62+y,c.z+z)==sc.meta(x,y,z),"asset metadata");}
    nbtCheck(b.live.marker,seed,cx,cz); check(NoGlassTransactionalGenerator.Marker.matches(b.live.marker,seed,cx,cz),"marker matches");check(!NoGlassTransactionalGenerator.Marker.matches(b.live.marker,seed,cx+1,cz),"marker bound to chunk");
    Snap round=reload(save(b.live));check(Arrays.equals(b.live.ids,round.ids)&&Arrays.equals(b.live.metas,round.metas)&&Arrays.equals(b.live.marker,round.marker),"state roundtrip");
    check(g.attempt(new Backend(round),seed,cx,cz)==NoGlassTransactionalGenerator.Result.REJECTED,"replay rejected");
    byte[] malformed=round.marker.clone();malformed[malformed.length-1]=1;check(!NoGlassTransactionalGenerator.Marker.matches(malformed,seed,cx,cz),"bad marker rejected");
    Backend absent=new Backend(flat(64));absent.unavailable=true;check(g.attempt(absent,seed,cx,cz)==NoGlassTransactionalGenerator.Result.REJECTED && absent.commits==0,"unloaded");
    Backend old=new Backend(flat(64));old.live.fresh=false;check(g.attempt(old,seed,cx,cz)==NoGlassTransactionalGenerator.Result.REJECTED && old.commits==0,"old world");
    Backend border=new Backend(flat(64));border.live.border=false;check(g.attempt(border,seed,cx,cz)==NoGlassTransactionalGenerator.Result.REJECTED && border.commits==0,"border");
    Backend ent=new Backend(flat(64));ent.live.entities=true;check(g.attempt(ent,seed,cx,cz)==NoGlassTransactionalGenerator.Result.REJECTED && ent.commits==0,"entities");
    Backend te=new Backend(flat(64));te.live.tiles=true;check(g.attempt(te,seed,cx,cz)==NoGlassTransactionalGenerator.Result.REJECTED && te.commits==0,"tile");
    Backend marked=new Backend(flat(64));marked.live.marker=new byte[]{1};check(g.attempt(marked,seed,cx,cz)==NoGlassTransactionalGenerator.Result.REJECTED,"unknown marker fail closed");
    Backend construction=new Backend(flat(64));construction.live.set(c.x-1,64,c.z,5,0);check(g.attempt(construction,seed,cx,cz)==NoGlassTransactionalGenerator.Result.REJECTED,"nearby planks");
    Backend liquid=new Backend(flat(64));liquid.live.set(c.x,65,c.z,9,0);check(g.attempt(liquid,seed,cx,cz)==NoGlassTransactionalGenerator.Result.REJECTED,"water");
    Backend invalid=new Backend(flat(64));invalid.live.set(c.x,64,c.z,2,1);check(g.attempt(invalid,seed,cx,cz)==NoGlassTransactionalGenerator.Result.REJECTED,"invalid meta");
    Backend hillside=new Backend(flat(64));hillside.live.set(c.x,65,c.z,2,0);check(g.attempt(hillside,seed,cx,cz)==NoGlassTransactionalGenerator.Result.REJECTED,"normal hillside");
    Backend vegetation=new Backend(flat(64));vegetation.live.set(c.x+2,65,c.z+2,31,1);check(g.attempt(vegetation,seed,cx,cz)==NoGlassTransactionalGenerator.Result.REJECTED,"grass vegetation");
    Backend variant=new Backend(flat(64));variant.live.set(c.x-1,10,c.z,1,6);check(g.attempt(variant,seed,cx,cz)==NoGlassTransactionalGenerator.Result.COMMITTED,"natural stone variant");
    Backend conflict=new Backend(flat(64));conflict.conflict=true;byte[] saved=conflict.live.ids.clone();check(g.attempt(conflict,seed,cx,cz)==NoGlassTransactionalGenerator.Result.CONFLICT && Arrays.equals(saved,conflict.live.ids) && conflict.live.marker==null && conflict.writes==0,"conflict no writes");
    Backend fault=new Backend(flat(64));fault.throwAcquire=true;try {g.attempt(fault,seed,cx,cz);throw new AssertionError("missing acquire exception");} catch(IllegalStateException e) {check(e==fault.acquireException && fault.commits==0 && fault.releases==0,"acquire fault");}
    fault=new Backend(flat(64));fault.throwCommit=true;try {g.attempt(fault,seed,cx,cz);throw new AssertionError("missing commit exception");} catch(IllegalStateException e) {check(e==fault.commitException && fault.writes==0 && fault.live.marker==null && fault.releases==1 && !fault.held,"commit fault released");}
    for(int failure:new int[]{0,1,320,639,640}) { Backend f=new Backend(flat(64));f.failAt=failure;byte[] original=f.live.ids.clone();try {g.attempt(f,seed,cx,cz);throw new AssertionError("missing staged exception");} catch(IllegalStateException e) {check(Arrays.equals(original,f.live.ids) && f.live.marker==null && f.writes==0 && f.releases==1 && !f.held,"staging failure "+failure);} }
    NoGlassReservationTest.verify(g,seed,cx,cz,new Backend[]{old,border,ent,te,marked,construction,liquid,invalid,hillside,vegetation},absent,b,conflict);
    Backend race=new Backend(flat(64));race.mutateBeforeCompare=true;check(g.attempt(race,seed,cx,cz)==NoGlassTransactionalGenerator.Result.CONFLICT && race.writes==0 && race.live.id(0,10,0)==5,"concurrent edit survives");
    for(int height:new int[]{3,250,251}) { Backend h=new Backend(flat(height));NoGlassTransactionalGenerator.Result hr=g.attempt(h,seed,cx,cz);check((height==251?hr==NoGlassTransactionalGenerator.Result.REJECTED:hr==NoGlassTransactionalGenerator.Result.COMMITTED),"height "+height+" "+hr); }
    Backend extremes=new Backend(flat(64));check(g.attempt(extremes,seed,Integer.MIN_VALUE,Integer.MAX_VALUE)==NoGlassTransactionalGenerator.Result.REJECTED && extremes.acquires==0,"coordinate overflow");
    int unselected=0;while(NoGlassPlan.select(seed,unselected,cz)!=null)unselected++;Backend no=new Backend(flat(64));check(g.attempt(no,seed,unselected,cz)==NoGlassTransactionalGenerator.Result.NOT_SELECTED && no.acquires==0,"no access when unselected");
    System.out.println("PASS "+checks+" assertions; fake atomic backend only; candidate="+cx+","+cz);
  }
}
