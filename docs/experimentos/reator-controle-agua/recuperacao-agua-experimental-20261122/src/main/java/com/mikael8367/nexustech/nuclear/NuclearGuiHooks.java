package com.mikael8367.nexustech.nuclear;
import net.minecraft.block.BlockHorizontal;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** One GUI handler. Coordinates identify the clicked part and its nearby controller. */
public final class NuclearGuiHooks {
 public static final int GUI_ID=8367;
 private NuclearGuiHooks(){}
 static boolean accessible(EntityPlayer player,World w,BlockPos p){
  return player!=null && w!=null && player.world==w && w.isBlockLoaded(p)
      && player.getDistanceSq(p.getX()+.5,p.getY()+.5,p.getZ()+.5)<=64;
 }
 /** 1.12 world height is 0..255; encode the controller offset in unused Y bits. */
 static int encodeY(BlockPos clicked,BlockPos controller){
  int dx=controller.getX()-clicked.getX(),dz=controller.getZ()-clicked.getZ(),dy=clicked.getY()-controller.getY();
  if(clicked.getY()<0 || clicked.getY()>255 || dx< -2 || dx>2 || dz< -2 || dz>2 || dy<0 || dy>4)return -1;
  return clicked.getY()+((((dx+2)*5+(dz+2))*5+dy+1)<<8);
 }
 static BlockPos clicked(int x,int y,int z){return new BlockPos(x,y&255,z);}
 static BlockPos origin(int x,int y,int z){
  if(y<0)return null;
  int code=y>>>8;if(code==0)return new BlockPos(x,y,z); // legacy controller-only call
  if(code>125)return null;
  code--;int dy=code%5;code/=5;int dz=code%5-2,dx=code/5-2;
  return new BlockPos(x+dx,(y&255)-dy,z+dz);
 }
 /** Search only the small set of possible controller positions; never load chunks. */
 static NuclearController resolveServer(World w,BlockPos clicked){
  if(w==null || w.isRemote || !w.isBlockLoaded(clicked))return null;
  TileEntity part=w.getTileEntity(clicked);
  if(part instanceof NuclearController){
   NuclearController c=(NuclearController)part;
   return c.prepareForGui() && w.getTileEntity(clicked)==part?c:null;
  }
  if(!(part instanceof NuclearPart) || part.isInvalid())return null;
  NuclearController existing=((NuclearPart)part).controller();
  if(existing!=null && existing.prepareForGui() && existing.ownsLivePart(clicked,(NuclearPart)part))return existing;
  for(EnumFacing front:EnumFacing.Plane.HORIZONTAL)for(int x=-1;x<=1;x++)for(int y=0;y<5;y++)for(int d=0;d<3;d++){
   if(x==0 && y==0 && d==0)continue;
   BlockPos delta=NuclearController.at(BlockPos.ORIGIN,front,x,y,d);
   BlockPos p=clicked.subtract(delta);
   if(!w.isBlockLoaded(p) || w.getBlockState(p).getBlock()!=NuclearRegistry.CONTROLLER
       || w.getBlockState(p).getValue(BlockHorizontal.FACING)!=front)continue;
   TileEntity t=w.getTileEntity(p);
   if(t instanceof NuclearController){
    NuclearController c=(NuclearController)t;
    if(c.prepareForGui() && c.ownsLivePart(clicked,(NuclearPart)part))return c;
   }
  }
  return null;
 }
 public static Object server(int id,EntityPlayer player,World w,int x,int y,int z){
  if(id!=GUI_ID || y<0)return null;
  BlockPos p=clicked(x,y,z),controller=origin(x,y,z);
  if(controller==null || !accessible(player,w,p) || !w.isBlockLoaded(controller))return null;
  TileEntity clickedTile=w.getTileEntity(p),t=w.getTileEntity(controller);
  if(!(t instanceof NuclearController) || clickedTile==null)return null;
  NuclearContainer c=new NuclearContainer(player.inventory,(NuclearController)t,p,clickedTile);
  return c.canInteractWith(player)?c:null;
 }
}
