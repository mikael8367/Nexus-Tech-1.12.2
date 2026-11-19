package com.mikael8367.nexustech;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;

@Mod(modid = NexusTech.MODID, name = NexusTech.NAME, version = NexusTech.VERSION, acceptedMinecraftVersions = "[1.12.2]")
public class NexusTech {
    public static final String MODID = "nexustech", NAME = "Nexus Tech", VERSION = "1.0";
    @Mod.Instance(MODID) public static NexusTech INSTANCE;
    @SidedProxy(clientSide = "com.mikael8367.nexustech.ClientProxy", serverSide = "com.mikael8367.nexustech.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler public void preInit(FMLPreInitializationEvent event) {
        ModRegistry.registerTiles();
        NetworkRegistry.INSTANCE.registerGuiHandler(this, new MachineGuiHandler());
    }
}
