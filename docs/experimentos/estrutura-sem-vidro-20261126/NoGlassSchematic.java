package com.mikael8367.nexustech.worldgen;

import java.io.*;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.zip.GZIPInputStream;

/** A strictly pinned, read-only MCEdit Alpha schematic. No external libraries required. */
public final class NoGlassSchematic {
    private static final String SHA256 = "5333f61ae7f814e0632e03909b219af91e89480b8b78adaabbb9db35b4cdaf22";
    private final byte[] blocks, data;
    private NoGlassSchematic(byte[] blocks, byte[] data) { this.blocks=blocks; this.data=data; }
    public int id(int x,int y,int z) { return blocks[index(x,y,z)] & 255; }
    public int meta(int x,int y,int z) { return data[index(x,y,z)] & 255; }
    private static int index(int x,int y,int z) {
        if (x<0 || x>=10 || y<0 || y>=8 || z<0 || z>=8) throw new IndexOutOfBoundsException();
        return (y*8+z)*10+x;
    }
    public static NoGlassSchematic load(InputStream source) throws Exception {
        if(source==null) throw new IOException("missing schematic");
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        try(InputStream in=source) {
            byte[] buf=new byte[1024]; int n;
            while((n=in.read(buf))!=-1) { if(out.size()+n>4096) throw new IOException("oversized schematic"); out.write(buf,0,n); }
        }
        byte[] raw=out.toByteArray(), hash=MessageDigest.getInstance("SHA-256").digest(raw);
        StringBuilder h=new StringBuilder(); for(byte b:hash) h.append(String.format("%02x",b&255));
        if(!SHA256.equals(h.toString())) throw new IOException("schematic digest mismatch");
        DataInputStream in=new DataInputStream(new GZIPInputStream(new ByteArrayInputStream(raw)));
        if(in.readUnsignedByte()!=10 || !"Schematic".equals(in.readUTF())) throw new IOException("bad root");
        byte[] blocks=null,data=null; int width=-1,height=-1,length=-1; boolean material=false,tiles=false,entities=false;
        for(;;) {
            int type=in.readUnsignedByte(); if(type==0) break; String name=in.readUTF();
            if(type==7) {
                int n=in.readInt(); if(n!=640) throw new IOException("bad array size");
                byte[] a=new byte[n]; in.readFully(a);
                if(name.equals("Blocks") && blocks==null) blocks=a;
                else if(name.equals("Data") && data==null) data=a;
                else throw new IOException("unexpected array");
            } else if(type==2) {
                int value=in.readUnsignedShort();
                if(name.equals("Width")) width=value; else if(name.equals("Height")) height=value;
                else if(name.equals("Length")) length=value; else throw new IOException("unexpected short");
            } else if(type==3) {
                if(!Arrays.asList("WEOffsetX","WEOffsetY","WEOffsetZ","WEOriginX","WEOriginY","WEOriginZ").contains(name)) throw new IOException("unexpected int");
                in.readInt(); // WorldEdit source coordinates are not a world-generation anchor.
            } else if(type==8) {
                if(!name.equals("Materials") || !in.readUTF().equals("Alpha")) throw new IOException("bad material"); material=true;
            } else if(type==9) {
                int element=in.readUnsignedByte(), count=in.readInt();
                if(element!=10 || count!=0) throw new IOException("entities forbidden");
                if(name.equals("TileEntities")) tiles=true; else if(name.equals("Entities")) entities=true; else throw new IOException("unexpected list");
            } else throw new IOException("unexpected NBT tag");
        }
        if(width!=10 || height!=8 || length!=8 || blocks==null || data==null || !material || !tiles || !entities || in.read()!=-1) throw new IOException("incomplete schematic");
        for(int i=0;i<640;i++) {
            int id=blocks[i]&255, meta=data[i]&255;
            if(!((id==0 && meta==0)||(id==1 && (meta==0||meta==1||meta==5||meta==6)) || ((id==2||id==3||id==13||id==15) && meta==0))) throw new IOException("unexpected ID/meta");
        }
        return new NoGlassSchematic(blocks,data);
    }
}
