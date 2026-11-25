package net.minecraft.nbt;
import java.util.HashMap;
import java.util.Map;
/** Minimal test stub; not Minecraft's actual implementation. */
public class NBTTagCompound {
 private final Map<String,Object> data=new HashMap<>();
 public void setBoolean(String k,boolean v){data.put(k,Byte.valueOf((byte)(v?1:0)));}
 public void setByte(String k,byte v){data.put(k,Byte.valueOf(v));}
 public void setInteger(String k,int v){data.put(k,Integer.valueOf(v));}
 public void setString(String k,String v){data.put(k,v);}
 public boolean hasKey(String k){return data.containsKey(k);}
 public boolean hasKey(String k,int type){Object v=data.get(k);return type==1?v instanceof Byte:type==3?v instanceof Integer:false;}
 public byte getByte(String k){Object v=data.get(k);return v instanceof Number?((Number)v).byteValue():0;}
 public int getInteger(String k){Object v=data.get(k);return v instanceof Number?((Number)v).intValue():0;}
}
