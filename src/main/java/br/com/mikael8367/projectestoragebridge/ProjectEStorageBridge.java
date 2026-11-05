package br.com.mikael8367.projectestoragebridge;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.Logger;

@Mod(
    modid = ProjectEStorageBridge.MOD_ID,
    name = ProjectEStorageBridge.MOD_NAME,
    version = ProjectEStorageBridge.VERSION,
    dependencies = "required-after:forge@[14.23.5.2860,);required-after:projecte;required-after:storagenetwork"
)
public final class ProjectEStorageBridge {
    public static final String MOD_ID = "projectestoragebridge";
    public static final String MOD_NAME = "ProjectE Storage Bridge";
    public static final String VERSION = "0.3.0";

    private static Logger logger;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        logger = event.getModLog();
        BridgeConfig.load(event.getSuggestedConfigurationFile());
        MinecraftForge.EVENT_BUS.register(new BridgeRegistry());
        logger.info("ProjectE Storage Bridge registering its Simple Storage Network cable.");
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        logger.info("ProjectE Storage Bridge initialized. EMC withdrawals are server-side and fail closed if player context is ambiguous.");
    }

    public static Logger logger() {
        return logger;
    }
}
