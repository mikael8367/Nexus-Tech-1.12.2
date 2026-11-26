package com.mikael8367.nexustech.worldgen;
import net.minecraft.nbt.*;
import java.io.*;
import java.util.*;
public class CompareSchematic {
 public static void main(String[] args) throws Exception {
  NBTTagCompound a,b;
  try(InputStream x=new FileInputStream(args[0])) { a=CompressedStreamTools.readCompressed(x); }
  byte[] encoded=java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(args[1]));
  b=CompressedStreamTools.readCompressed(new ByteArrayInputStream(Base64.getMimeDecoder().decode(encoded)));
  System.out.println("keys="+a.getKeySet());
  byte[] aa=a.getByteArray("Blocks"),bb=b.getByteArray("Blocks"),ad=a.getByteArray("Data"),bd=b.getByteArray("Data");
  if(aa.length!=210||bb.length!=210||ad.length!=210||bd.length!=210) throw new AssertionError();
  int changes=0; for(int i=0;i<210;i++) if(aa[i]!=bb[i]||ad[i]!=bd[i]) { changes++; System.out.println("diff index="+i+" id="+(aa[i]&255)+"->"+(bb[i]&255)+" data="+(ad[i]&255)+"->"+(bd[i]&255)); if(i!=168||(aa[i]&255)!=20||(bb[i]&255)!=0||(ad[i]&255)!=0||(bd[i]&255)!=0) throw new AssertionError("unexpected difference"); }
  if(changes!=1) throw new AssertionError("diff count"); a.removeTag("Blocks"); b.removeTag("Blocks"); a.removeTag("Data"); b.removeTag("Data"); if(!a.equals(b)) throw new AssertionError("other NBT changed");
  NBTTagList tiles=b.getTagList("TileEntities",10); for(int i=0;i<tiles.tagCount();i++) System.out.println("TE="+tiles.getCompoundTagAt(i));
  System.out.println("PASS exactly one glass block changed; all other NBT equal");
 }
}
