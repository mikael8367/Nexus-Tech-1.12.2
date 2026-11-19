package com.mikael8367.nexustech;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;

@Mod(modid = NexusTech.MODID, name = NexusTech.NAME, version = NexusTech.VERSION, acceptedMinecraftVersions = "[1.12.2]")
public class NexusTech {
    public static final String MODID = "nexustech", NAME = "Nexus Tech", VERSION = "1.0";
    public static final CreativeTabs CREATIVE_TAB = new CreativeTabs(MODID) {
        @Override public ItemStack getTabIconItem() {
            // Resolve the registered block item only when the creative inventory requests it.
            return new ItemStack(ModRegistry.SOLAR);
        }
    };
    @Mod.Instance(MODID) public static NexusTech INSTANCE;
    @SidedProxy(clientSide = "com.mikael8367.nexustech.ClientProxy", serverSide = "com.mikael8367.nexustech.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler public void preInit(FMLPreInitializationEvent event) {
        ModRegistry.registerTiles();
        com.mikael8367.nexustech.digitalmine.DigitalMineRegistry.registerTiles();
        com.mikael8367.nexustech.ores.OreRegistry.registerWorldGenerator();
        NetworkRegistry.INSTANCE.registerGuiHandler(this, new MachineGuiHandler());
    }

    @Mod.EventHandler public void init(FMLInitializationEvent event) {
        com.mikael8367.nexustech.ores.OreRegistry.init();
    }
}
