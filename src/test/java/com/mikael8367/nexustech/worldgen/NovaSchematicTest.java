package com.mikael8367.nexustech.worldgen;
import java.util.*;
import java.io.*;
import java.security.MessageDigest;
import net.minecraft.nbt.*;
public class NovaSchematicTest {
    private static int checks;
    static void check(boolean b) { checks++; if(!b) throw new AssertionError("check "+checks); }
    static void rejected(NBTTagCompound n) { try { NovaSchematic.inspect(n); throw new AssertionError("accepted invalid NBT"); } catch(IllegalArgumentException expected) { checks++; } }
    static NBTTagCompound valid() {
        NBTTagCompound n=new NBTTagCompound(); n.setString("Materials","Alpha"); n.setShort("Width",(short)6); n.setShort("Height",(short)5); n.setShort("Length",(short)7);
        byte[] a=new byte[210]; Arrays.fill(a,(byte)1); n.setByteArray("Blocks",a); n.setByteArray("Data",new byte[210]); n.setTag("Entities",new NBTTagList()); n.setTag("TileEntities",new NBTTagList()); return n;
    }
    public static void main(String[] args) throws Exception {
        NovaSchematic s=NovaSchematic.loadResource(); List<NovaSchematic.Cell> cells=s.snapshot(); check(cells.size()==210); int tileCount=0,air=0;
        for(int i=0;i<cells.size();i++) { NovaSchematic.Cell c=cells.get(i); check(i==(c.y*7+c.z)*6+c.x); check(c.metadata<=15); if(c.id==0) air++; if(c.tileCopy()!=null) { tileCount++; NBTTagCompound t=c.tileCopy(); check(t.getInteger("x")==c.x&&t.getInteger("y")==c.y&&t.getInteger("z")==c.z); String id=t.getString("id"); t.setString("id","mutated"); check(c.tileCopy().getString("id").equals(id)); } }
        System.out.println("Resource: cells="+cells.size()+" tiles="+tileCount+" numeric-air="+air+" offsets="+Arrays.toString(s.worldEditOffsets())); check(tileCount==4); check(air==119);
        NBTTagCompound n=valid(); NovaSchematic snapshot=NovaSchematic.inspect(n); n.getByteArray("Blocks")[0]=2; check(snapshot.snapshot().get(0).id==1);
        n=valid(); n.setShort("Width",(short)-1); rejected(n);
        n=valid(); n.setByteArray("Blocks",new byte[209]); rejected(n);
        n=valid(); n.setByteArray("Data",new byte[211]); rejected(n);
        n=valid(); n.getByteArray("Data")[10]=(byte)16; rejected(n);
        for(int id:new int[]{20,95,102,160}) { n=valid(); n.getByteArray("Blocks")[33]=(byte)id; rejected(n); }
        n=valid(); n.getByteArray("Blocks")[33]=(byte)241; check(NovaSchematic.inspect(n).snapshot().get(33).id==241);
        n=valid(); n.setByteArray("AddBlocks",new byte[104]); rejected(n);
        n=valid(); n.setByteArray("AddBlocks",new byte[105]); n.getByteArray("AddBlocks")[0]=0x21; NovaSchematic extended=NovaSchematic.inspect(n); check(extended.snapshot().get(0).id==257); check(extended.snapshot().get(1).id==513);
        n=valid(); n.setInteger("WEOffsetX",1); rejected(n);
        n=valid(); NBTTagList list=new NBTTagList(); NBTTagCompound tile=new NBTTagCompound(); tile.setInteger("x",5);tile.setInteger("y",4);tile.setInteger("z",6);tile.setString("id","test");list.appendTag(tile); n.setTag("TileEntities",list); check(NovaSchematic.inspect(n).snapshot().get(209).tileCopy()!=null);
        tile.setInteger("x",6); rejected(n); tile.setInteger("x",5); list.appendTag(tile.copy()); rejected(n);
        list.removeTag(1); n.getByteArray("Blocks")[209]=0; rejected(n);
        n=valid(); n.setString("Materials","Sponge"); rejected(n);
        // Nested source and returned tile copies must not alias the inspected object.
        n=valid(); list=new NBTTagList(); tile=new NBTTagCompound(); tile.setInteger("x",1); tile.setInteger("y",0); tile.setInteger("z",0); tile.setString("id","test"); NBTTagCompound nested=new NBTTagCompound(); nested.setString("value","original"); tile.setTag("nested",nested); list.appendTag(tile); n.setTag("TileEntities",list);
        snapshot=NovaSchematic.inspect(n); nested.setString("value","source mutation"); check(snapshot.snapshot().get(1).tileCopy().getCompoundTag("nested").getString("value").equals("original"));
        NBTTagCompound returned=snapshot.snapshot().get(1).tileCopy(); returned.getCompoundTag("nested").setString("value","return mutation"); check(snapshot.snapshot().get(1).tileCopy().getCompoundTag("nested").getString("value").equals("original"));
        int[] offsets=snapshot.worldEditOffsets(); offsets[0]=999; check(snapshot.worldEditOffsets()[0]==0);
        n=valid(); n.setString("Blocks","wrong type"); rejected(n);
        n=valid(); n.setString("AddBlocks","wrong type"); rejected(n);
        n=valid(); n.setString("Entities","wrong type"); rejected(n);
        n=valid(); list=new NBTTagList(); list.appendTag(new NBTTagCompound()); n.setTag("Entities",list); rejected(n);
        n=valid(); list=new NBTTagList(); list.appendTag(new NBTTagString("not a compound")); n.setTag("TileEntities",list); rejected(n);
        n=valid(); list=new NBTTagList(); tile=new NBTTagCompound(); tile.setInteger("x",0);tile.setInteger("y",0);tile.setInteger("z",0);list.appendTag(tile); n.setTag("TileEntities",list); rejected(n);
        n=valid(); n.setString("WEOffsetX","bad"); n.setInteger("WEOffsetY",0);n.setInteger("WEOffsetZ",0); rejected(n);
        try { NovaSchematic.inspect(null); throw new AssertionError("null accepted"); } catch(IllegalArgumentException expected) { checks++; }
        compareOriginal(args[0],s);
        System.out.println("PASS "+checks+" checks (offline NBT only; no world generation)");
    }
    static String hex(byte[] b) { StringBuilder x=new StringBuilder(); for(byte v:b)x.append(String.format("%02x",v&255)); return x.toString(); }
    static void compareOriginal(String path,NovaSchematic inspected) throws Exception {
        byte[] input=java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(path));
        check(hex(MessageDigest.getInstance("SHA-256").digest(input)).equals("eb23b1ec67c9ea74a974eb09fcbe7fcfe175f122b2a12aeb0b15513a740655cd"));
        NBTTagCompound original=CompressedStreamTools.readCompressed(new ByteArrayInputStream(input));
        byte[] blocks=original.getByteArray("Blocks"), data=original.getByteArray("Data"); check(blocks.length==210 && data.length==210);
        byte[] add=original.hasKey("AddBlocks",7)?original.getByteArray("AddBlocks"):new byte[105]; check(add.length==105);
        NBTTagList tiles=original.getTagList("TileEntities",10); check(tiles.tagCount()==4); Map<Integer,NBTTagCompound> byIndex=new HashMap<>();
        for(int i=0;i<tiles.tagCount();i++) { NBTTagCompound t=tiles.getCompoundTagAt(i); int index=(t.getInteger("y")*7+t.getInteger("z"))*6+t.getInteger("x"); check(byIndex.put(index,t)==null); }
        int comparedTiles=0; List<NovaSchematic.Cell> cells=inspected.snapshot();
        for(int i=0;i<210;i++) { NovaSchematic.Cell c=cells.get(i); int expected=(blocks[i]&255)|((((add[i/2]&255)>>>((i&1)*4))&15)<<8); if(i==168) { check(expected==20 && (data[i]&255)==0); expected=0; }
            check(c.id==expected); check(c.metadata==(data[i]&255)); NBTTagCompound t=byIndex.get(i); check(Objects.equals(t,c.tileCopy())); if(t!=null) comparedTiles++;
        }
        check(comparedTiles==4); System.out.println("Original vs snapshot: all 210 IDs and metadata and all 4 full tile NBTs match except ID 20->0 at index 168");
    }

}
