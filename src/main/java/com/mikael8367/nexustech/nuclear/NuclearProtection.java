package com.mikael8367.nexustech.nuclear;
import com.mikael8367.nexustech.NexusTech;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
/** Player mining protection: fail closed if the bound controller's chunk is absent. */
@Mod.EventBusSubscriber(modid=NexusTech.MODID)
public final class NuclearProtection {
 @SubscribeEvent public static void onBreak(BlockEvent.BreakEvent event) {
    World w=event.getWorld(); if(w.isRemote)return;
    BlockPos p=event.getPos(); if(!w.isBlockLoaded(p))return;
    TileEntity t=w.getTileEntity(p); NuclearController c=null;
    if(t instanceof NuclearController)c=(NuclearController)t;
    else if(t instanceof NuclearPart) {
        BlockPos owner=((NuclearPart)t).owner;
        if(owner==null)return;
        if(!w.isBlockLoaded(owner)){event.setCanceled(true);return;}
        TileEntity o=w.getTileEntity(owner); if(o instanceof NuclearController)c=(NuclearController)o;
    }
    if(c!=null && (c.enabled || c.temperature>400))event.setCanceled(true);
 }
}
