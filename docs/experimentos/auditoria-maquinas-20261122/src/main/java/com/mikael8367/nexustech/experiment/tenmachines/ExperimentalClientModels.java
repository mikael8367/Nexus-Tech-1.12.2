package com.mikael8367.nexustech.experiment.tenmachines;

import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Only call explicitly on the client during ModelRegistryEvent, after item registration. */
@SideOnly(Side.CLIENT)
public final class ExperimentalClientModels {
 private ExperimentalClientModels(){}
 public static void registerModels(){
  for(ProcessingBlock b:ExperimentalRegistry.blocks().values()){
   Item item=Item.getItemFromBlock(b);
   ModelLoader.setCustomModelResourceLocation(item,0,new ModelResourceLocation(b.getRegistryName(),"inventory"));
  }
 }
}
