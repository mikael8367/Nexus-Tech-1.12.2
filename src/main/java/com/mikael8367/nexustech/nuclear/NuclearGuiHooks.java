package com.mikael8367.nexustech.nuclear;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
/** Call these from the existing single GUI handler / client proxy; do not register a second handler. */
public final class NuclearGuiHooks {
 public static final int GUI_ID=8367;
 private NuclearGuiHooks(){}
 public static Object server(int id,EntityPlayer player,World w,int x,int y,int z){
  if(id!=GUI_ID)return null;BlockPos p=new BlockPos(x,y,z);if(!w.isBlockLoaded(p))return null;TileEntity t=w.getTileEntity(p);if(!(t instanceof NuclearController))return null;NuclearContainer c=new NuclearContainer(player.inventory,(NuclearController)t);return c.canInteractWith(player)?c:null;
 }
}
