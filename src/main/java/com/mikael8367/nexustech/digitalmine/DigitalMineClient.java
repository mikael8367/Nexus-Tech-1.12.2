package com.mikael8367.nexustech.digitalmine;

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
@Mod.EventBusSubscriber(modid = NexusTech.MODID, value = Side.CLIENT)
public final class DigitalMineClient {
    private DigitalMineClient() {}
    @SubscribeEvent public static void models(ModelRegistryEvent event) {
        for (Block block : DigitalMineRegistry.BLOCKS) ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(block), 0, new ModelResourceLocation(block.getRegistryName(), "inventory"));
    }
    /** Delegate GUI ID 71 from ClientProxy.clientGui. */
    public static Object gui(int id, EntityPlayer player, World world, int x, int y, int z) {
        BlockPos p = new BlockPos(x,y,z);
        if (id != DigitalMineRegistry.GUI_ID || !world.isBlockLoaded(p)) return null;
        TileEntity te = world.getTileEntity(p);
        return te instanceof DigitalMineTile ? new DigitalMineGui(player.inventory, (DigitalMineTile)te) : null;
    }
}
