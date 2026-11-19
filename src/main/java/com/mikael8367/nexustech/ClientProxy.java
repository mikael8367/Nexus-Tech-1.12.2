package com.mikael8367.nexustech;

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
public class ClientProxy extends CommonProxy {
    @SubscribeEvent public static void models(ModelRegistryEvent event) {
        for (Block block : ModRegistry.BLOCKS) ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(block), 0, new ModelResourceLocation(block.getRegistryName(), "inventory"));
    }
    @Override public Object clientGui(int id, EntityPlayer player, World world, int x, int y, int z) {
        if (id == com.mikael8367.nexustech.digitalmine.DigitalMineRegistry.GUI_ID)
            return com.mikael8367.nexustech.digitalmine.DigitalMineClient.gui(id, player, world, x, y, z);
        if (id == com.mikael8367.nexustech.nuclear.NuclearGuiHooks.GUI_ID)
            return com.mikael8367.nexustech.nuclear.NuclearClient.gui(id, player, world, x, y, z);
        BlockPos pos = new BlockPos(x, y, z);
        if (id != 0 || !world.isBlockLoaded(pos)) return null;
        TileEntity tile = world.getTileEntity(pos);
        return tile instanceof MachineTile && ((MachineTile)tile).kind != MachineTile.Kind.CABLE ? new MachineGui(player.inventory, (MachineTile)tile) : null;
    }
}
