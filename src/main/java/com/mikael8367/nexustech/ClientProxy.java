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
        ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(ModRegistry.BRIDGE), 0, new ModelResourceLocation(ModRegistry.BRIDGE.getRegistryName(), "inventory"));
    }
    @Override public Object clientGui(int id, EntityPlayer player, World world, int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        if (!world.isBlockLoaded(pos)) return null;
        if (id == 1) {
            TileEntity bridge = world.getTileEntity(pos);
            return bridge instanceof BridgeTile ? new BridgeGui(player, (BridgeTile) bridge) : null;
        }
        if (id != 0) return null;
        TileEntity tile = world.getTileEntity(pos);
        return tile instanceof MachineTile && ((MachineTile)tile).kind != MachineTile.Kind.CABLE ? new MachineGui(player.inventory, (MachineTile)tile) : null;
    }
}
