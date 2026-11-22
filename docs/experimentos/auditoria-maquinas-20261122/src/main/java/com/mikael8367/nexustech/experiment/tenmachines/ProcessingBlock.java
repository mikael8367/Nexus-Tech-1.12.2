package com.mikael8367.nexustech.experiment.tenmachines;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;

public final class ProcessingBlock extends Block {
 private final MachineKind kind;
 ProcessingBlock(MachineKind kind){
  super(Material.IRON);this.kind=kind;
  setRegistryName("nexustech",kind.id);setUnlocalizedName("nexustech."+kind.id);
  setHardness(3);setResistance(10);setHarvestLevel("pickaxe",1);
 }
 @Override public boolean hasTileEntity(IBlockState state){return true;}
 @Override public TileEntity createTileEntity(World world,IBlockState state){return ExperimentalRegistry.createTile(kind);}
 @Override public boolean onBlockActivated(World w,BlockPos p,IBlockState state,EntityPlayer player,EnumHand hand,EnumFacing side,float x,float y,float z){
  if(!w.isRemote && hand==EnumHand.MAIN_HAND){TileEntity tile=w.getTileEntity(p);if(tile instanceof ProcessingTile){ProcessingTile t=(ProcessingTile)tile;IEnergyStorage e=t.getCapability(CapabilityEnergy.ENERGY,side);if(e!=null)player.sendStatusMessage(new TextComponentString(kind.id+": "+e.getEnergyStored()+"/20000 FE | "+t.progress()+"/100"),true);}}
  return true;
 }
 @Override public void breakBlock(World w,BlockPos p,IBlockState state){
  TileEntity tile=w.getTileEntity(p); if(!w.isRemote && tile instanceof ProcessingTile)((ProcessingTile)tile).requestDrops(w,p);super.breakBlock(w,p,state);
 }
}
