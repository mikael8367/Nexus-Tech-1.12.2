package com.mikael8367.nexustech.worldgen;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/** Offline inspection only; deliberately has no World, generator registration or placement API. */
public final class NovaSchematic {
    public static final String RESOURCE = "/assets/nexustech/structures/nova_structure_sem_vidro.schematic.b64";
    public static final String SHA256 = "cf585cc66defdf94eb60c3fb17a313061937f5833da5dbc7703cd2dc4bfa1576";
    public enum Resource {
        LEGACY_6X5X7(RESOURCE,SHA256,6,5,7,true),
        ANEXO_11X14X11("/assets/nexustech/structures/nova_anexo_11x14x11_sem_vidro.schematic",
                "2dc25713290206fc8adb090492ccaf13701d7af859f53a3c5af9ee1931b17d8d",11,14,11,false);
        final String path, sha; final int width,height,length; final boolean base64;
        Resource(String path,String sha,int width,int height,int length,boolean base64) {
            this.path=path; this.sha=sha; this.width=width; this.height=height; this.length=length; this.base64=base64;
        }
    }
    private final int width, height, length;
    private final byte[] blocks, data, add;
    private final Map<Integer, NBTTagCompound> tiles;
    private final int offsetX, offsetY, offsetZ;

    private NovaSchematic(NBTTagCompound n,Resource type) {
        require(n.hasKey("Materials", 8) && "Alpha".equals(n.getString("Materials")), "not Alpha");
        require(n.hasKey("Width", 2) && n.hasKey("Height", 2) && n.hasKey("Length", 2), "dimensions type");
        width=n.getShort("Width"); height=n.getShort("Height"); length=n.getShort("Length");
        require(width==type.width && height==type.height && length==type.length, "unexpected dimensions");
        int volume=width*height*length;
        require(n.hasKey("Blocks",7) && n.hasKey("Data",7), "array type");
        blocks=n.getByteArray("Blocks").clone(); data=n.getByteArray("Data").clone();
        require(blocks.length==volume && data.length==volume,"array length");
        if(n.hasKey("AddBlocks")) {
            require(n.hasKey("AddBlocks",7), "AddBlocks type");
            add=n.getByteArray("AddBlocks").clone();
            require(add.length==(volume+1)/2, "AddBlocks length");
        } else add=new byte[(volume+1)/2];
        for(int i=0;i<volume;i++) {
            int id=rawId(i); int meta=data[i]&255;
            require(meta<=15,"metadata > 15");
            require(id!=20 && id!=95 && id!=102 && id!=160,"glass numeric ID");
        }
        require(n.hasKey("Entities",9),"entity list missing");
        NBTTagList entities=(NBTTagList)n.getTag("Entities");
        require(entities.tagCount()==0, "entities unsupported");
        require(n.hasKey("TileEntities",9),"tile list missing");
        NBTTagList list=n.getTagList("TileEntities",10);
        // getTagList silently returns empty for mismatched element type; inspect the original.
        NBTTagList raw=(NBTTagList)n.getTag("TileEntities");
        require(raw.tagCount()==0 || raw.getTagType()==10, "tile list type");
        if(type==Resource.ANEXO_11X14X11) require(raw.tagCount()==0,"anexo tiles unsupported");
        Map<Integer,NBTTagCompound> found=new HashMap<>();
        for(int i=0;i<list.tagCount();i++) {
            NBTTagCompound t=list.getCompoundTagAt(i);
            require(t.hasKey("x",3)&&t.hasKey("y",3)&&t.hasKey("z",3)&&t.hasKey("id",8)&&!t.getString("id").isEmpty(),"tile fields");
            int x=t.getInteger("x"),y=t.getInteger("y"),z=t.getInteger("z");
            require(x>=0&&x<width&&y>=0&&y<height&&z>=0&&z<length,"tile bounds");
            int index=index(x,y,z); require(rawId(index)!=0,"tile on numeric air");
            require(found.put(index,t.copy())==null,"duplicate tile");
        }
        tiles=Collections.unmodifiableMap(found);
        String[] offsets={"WEOffsetX","WEOffsetY","WEOffsetZ"};
        int count=0; for(String s:offsets) { if(n.hasKey(s)) { require(n.hasKey(s,3),"offset type"); count++; } }
        require(count==0 || count==3,"partial offsets");
        offsetX=n.getInteger(offsets[0]); offsetY=n.getInteger(offsets[1]); offsetZ=n.getInteger(offsets[2]);
    }
    private static void require(boolean value,String message) { if(!value) throw new IllegalArgumentException(message); }
    private int rawId(int i) { int nibble=((add[i/2]&255) >>> ((i&1)*4))&15; return (blocks[i]&255)|(nibble<<8); }
    private int index(int x,int y,int z) { return (y*length+z)*width+x; }
    /** Limited checks on an already parsed compound; not a validator for hostile NBT, DataVersion, or registry mappings. */
    public static NovaSchematic inspect(NBTTagCompound n) { return inspect(n,Resource.LEGACY_6X5X7); }
    public static NovaSchematic inspect(NBTTagCompound n,Resource type) {
        if(n==null || type==null) throw new IllegalArgumentException("null NBT or type");
        return new NovaSchematic(n,type);
    }
    /** Decode only the exact trusted resource; no arbitrary compressed input is accepted. */
    public static NovaSchematic loadResource() throws IOException { return loadResource(Resource.LEGACY_6X5X7); }
    public static NovaSchematic loadResource(Resource type) throws IOException {
        if(type==null) throw new IllegalArgumentException("null type");
        byte[] encoded; try(InputStream in=NovaSchematic.class.getResourceAsStream(type.path)) {
            if(in==null) throw new IOException("missing resource");
            ByteArrayOutputStream out=new ByteArrayOutputStream(); byte[] buf=new byte[1024]; int r,total=0;
            while((r=in.read(buf))!=-1) { total+=r; if(total>4096) throw new IOException("resource too large"); out.write(buf,0,r); }
            encoded=out.toByteArray();
        }
        final byte[] bytes;
        try { bytes=type.base64?Base64.getMimeDecoder().decode(encoded):encoded; } catch(IllegalArgumentException e) { throw new IOException("invalid base64",e); }
        try {
            byte[] digest=MessageDigest.getInstance("SHA-256").digest(bytes); StringBuilder hex=new StringBuilder();
            for(byte b:digest) hex.append(String.format("%02x",b&255));
            if(!type.sha.equals(hex.toString())) throw new IOException("resource checksum mismatch");
        } catch(NoSuchAlgorithmException e) { throw new IOException(e); }
        NovaSchematic result=inspect(CompressedStreamTools.readCompressed(new ByteArrayInputStream(bytes)),type);
        if(type==Resource.LEGACY_6X5X7 && result.tiles.size()!=4) throw new IOException("legacy tile count mismatch");
        return result;
    }
    /** Snapshot in schematic local coordinates, including air. Identity orientation only. */
    public List<Cell> snapshot() {
        List<Cell> out=new ArrayList<>(width*height*length);
        for(int y=0;y<height;y++) for(int z=0;z<length;z++) for(int x=0;x<width;x++) {
            int i=index(x,y,z); out.add(new Cell(x,y,z,rawId(i),data[i]&255,tiles.get(i)));
        }
        return Collections.unmodifiableList(out);
    }
    public int width() { return width; }
    public int height() { return height; }
    public int length() { return length; }
    public int volume() { return width*height*length; }
    public int[] worldEditOffsets() { return new int[]{offsetX,offsetY,offsetZ}; }
    public static final class Cell {
        public final int x,y,z,id,metadata;
        private final NBTTagCompound tile;
        private Cell(int x,int y,int z,int id,int metadata,NBTTagCompound tile) {
            this.x=x; this.y=y; this.z=z; this.id=id; this.metadata=metadata; this.tile=tile==null?null:tile.copy();
        }
        public NBTTagCompound tileCopy() { return tile==null?null:tile.copy(); }
    }
}
