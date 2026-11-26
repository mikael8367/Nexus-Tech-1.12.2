package com.mikael8367.nexustech.worldgen;
import java.util.*;
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
        for(int id:new int[]{20,95,102,160,241}) { n=valid(); n.getByteArray("Blocks")[33]=(byte)id; rejected(n); }
        n=valid(); n.setByteArray("AddBlocks",new byte[104]); rejected(n);
        n=valid(); n.setByteArray("AddBlocks",new byte[105]); n.getByteArray("AddBlocks")[0]=0x21; NovaSchematic extended=NovaSchematic.inspect(n); check(extended.snapshot().get(0).id==257); check(extended.snapshot().get(1).id==513);
        n=valid(); n.setInteger("WEOffsetX",1); rejected(n);
        n=valid(); NBTTagList list=new NBTTagList(); NBTTagCompound tile=new NBTTagCompound(); tile.setInteger("x",5);tile.setInteger("y",4);tile.setInteger("z",6);tile.setString("id","test");list.appendTag(tile); n.setTag("TileEntities",list); check(NovaSchematic.inspect(n).snapshot().get(209).tileCopy()!=null);
        tile.setInteger("x",6); rejected(n); tile.setInteger("x",5); list.appendTag(tile.copy()); rejected(n);
        list.removeTag(1); n.getByteArray("Blocks")[209]=0; rejected(n);
        n=valid(); n.setString("Materials","Sponge"); rejected(n);
        System.out.println("PASS "+checks+" checks (offline NBT only; no world generation)");
    }
}
