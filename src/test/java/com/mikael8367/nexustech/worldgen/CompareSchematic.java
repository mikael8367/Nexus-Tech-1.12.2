package com.mikael8367.nexustech.worldgen;
import net.minecraft.nbt.*;
import java.io.*;
import java.util.*;
public class CompareSchematic {
 public static void main(String[] args) throws Exception {
  NBTTagCompound a,b;
  try(InputStream x=new FileInputStream(args[0]); InputStream y=new FileInputStream(args[1])) { a=CompressedStreamTools.readCompressed(x); b=CompressedStreamTools.readCompressed(y); }
  System.out.println("keys="+a.getKeySet());
  byte[] aa=a.getByteArray("Blocks"),bb=b.getByteArray("Blocks"),ad=a.getByteArray("Data"),bd=b.getByteArray("Data");
  if(aa.length!=210||bb.length!=210||ad.length!=210||bd.length!=210) throw new AssertionError();
  int changes=0; for(int i=0;i<210;i++) if(aa[i]!=bb[i]||ad[i]!=bd[i]) { changes++; System.out.println("diff index="+i+" id="+(aa[i]&255)+"->"+(bb[i]&255)+" data="+(ad[i]&255)+"->"+(bd[i]&255)); if((aa[i]&255)!=20) throw new AssertionError("unexpected old id"); }
  if(changes!=1) throw new AssertionError("diff count"); a.removeTag("Blocks"); b.removeTag("Blocks"); a.removeTag("Data"); b.removeTag("Data"); if(!a.equals(b)) throw new AssertionError("other NBT changed");
  NBTTagList tiles=b.getTagList("TileEntities",10); for(int i=0;i<tiles.tagCount();i++) System.out.println("TE="+tiles.getCompoundTagAt(i));
  System.out.println("PASS exactly one glass block changed; all other NBT equal");
 }
}
