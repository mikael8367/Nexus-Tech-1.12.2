package com.mikael8367.nexustech.nuclear;

import net.minecraft.block.BlockHorizontal;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

/** Independent coordinate oracle for the 3x5x3 solid reactor, not based on at(). */
public final class NuclearStructureGeometryTest {
 static void eq(int actual,int expected,String message){NuclearReactorTest.eq(actual,expected,message);}
 static BlockPos coordinate(BlockPos p,EnumFacing f,int x,int y,int depth){
  switch(f){
   case NORTH:return p.add(x,y,depth);
   case EAST:return p.add(-depth,y,x);
   case SOUTH:return p.add(-x,y,-depth);
   case WEST:return p.add(depth,y,-x);
   default:throw new AssertionError("horizontal direction required");
  }
 }
 static void check(EnumFacing face)throws Exception{
  NuclearReactorTest.TestWorld w=NuclearReactorTest.world();BlockPos p=NuclearReactorTest.P;
  NuclearController controller=new NuclearController();
  w.put(p,NuclearRegistry.CONTROLLER.getDefaultState().withProperty(BlockHorizontal.FACING,face),controller);
  int count=0;
  for(int y=0;y<5;y++)for(int d=0;d<3;d++)for(int x=-1;x<=1;x++){
   BlockPos q=coordinate(p,face,x,y,d);
   if(x==0&&y==0&&d==0)continue;
   boolean core=x==0&&y==2&&d==1;
   w.put(q,(core?NuclearRegistry.CORE:NuclearRegistry.CASING).getDefaultState(),new NuclearPart());count++;
  }
  eq(count,44,"44 non-controller blocks");
  eq(controller.validateStructure(),1,"independent filled fixture "+face);
  // Every cell, including all three interior cells, is essential and needs the correct tile.
  for(int y=0;y<5;y++)for(int d=0;d<3;d++)for(int x=-1;x<=1;x++){
   if(x==0&&y==0&&d==0)continue;
   BlockPos q=coordinate(p,face,x,y,d);
   net.minecraft.block.state.IBlockState old=w.states.get(q);TileEntity tile=w.tiles.get(q);
   w.states.put(q,Blocks.AIR.getDefaultState());
   eq(controller.validateStructure(),0,"missing "+face+" "+x+","+y+","+d);
   w.states.put(q,(x==0&&y==2&&d==1?NuclearRegistry.CASING:NuclearRegistry.CORE).getDefaultState());
   eq(controller.validateStructure(),0,"wrong block "+face+" "+x+","+y+","+d);
   w.states.put(q,old);w.tiles.remove(q);
   eq(controller.validateStructure(),0,"missing tile "+face+" "+x+","+y+","+d);
   w.tiles.put(q,tile);w.missing.add(q);
   eq(controller.validateStructure(),-1,"unloaded "+face+" "+x+","+y+","+d);
   w.missing.remove(q);
  }
  BlockPos outside=coordinate(p,face,0,0,-1);
  w.states.put(outside,Blocks.STONE.getDefaultState());
  eq(controller.validateStructure(),1,"outside blocks ignored "+face);
  // Facing must point outward: the opposite orientation demands a different volume.
  w.states.put(p,NuclearRegistry.CONTROLLER.getDefaultState().withProperty(BlockHorizontal.FACING,face.getOpposite()));
  eq(controller.validateStructure(),0,"controller faces inward "+face);
  w.states.put(p,NuclearRegistry.CONTROLLER.getDefaultState().withProperty(BlockHorizontal.FACING,face));
  controller.update();eq(controller.validateStructure(),1,"bound structure still valid "+face);
 }
 public static void main(String[] args)throws Exception{
  Bootstrap.register();
  for(EnumFacing face:EnumFacing.Plane.HORIZONTAL)check(face);
  System.out.println("NuclearStructureGeometryTest: PASS (4 facings; all 44 cells, wrong block, missing tile, unloaded)");
 }
}
