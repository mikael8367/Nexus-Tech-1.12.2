package com.mikael8367.nexustech.nuclear;
import com.mikael8367.nexustech.NexusTech;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
@SideOnly(Side.CLIENT)
@Mod.EventBusSubscriber(modid=NexusTech.MODID,value=Side.CLIENT)
public final class NuclearClient {
 @SubscribeEvent public static void models(ModelRegistryEvent event){for(Block b:NuclearRegistry.BLOCKS)ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(b),0,new ModelResourceLocation(b.getRegistryName(),"inventory"));ModelLoader.setCustomModelResourceLocation(NuclearRegistry.FUEL,0,new ModelResourceLocation(NuclearRegistry.FUEL.getRegistryName(),"inventory"));}
 public static Object gui(int id,EntityPlayer p,World w,int x,int y,int z){if(id!=NuclearGuiHooks.GUI_ID)return null;BlockPos pos=NuclearGuiHooks.origin(x,y,z);if(pos==null || !w.isBlockLoaded(pos))return null;TileEntity t=w.getTileEntity(pos);return t instanceof NuclearController?new NuclearGui(p.inventory,(NuclearController)t):null;}
}
