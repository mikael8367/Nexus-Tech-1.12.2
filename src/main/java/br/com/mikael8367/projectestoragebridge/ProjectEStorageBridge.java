package br.com.mikael8367.projectestoragebridge;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
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
    public static final String VERSION = "0.4.0";

    @SidedProxy(
            clientSide = "br.com.mikael8367.projectestoragebridge.ClientProxy",
            serverSide = "br.com.mikael8367.projectestoragebridge.CommonProxy")
    public static CommonProxy proxy;

    private static Logger logger;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        logger = event.getModLog();
        BridgeConfig.load(event.getSuggestedConfigurationFile());
        MinecraftForge.EVENT_BUS.register(new BridgeRegistry());
        proxy.preInit(event);
        logger.info("ProjectE Storage Bridge registering its Simple Storage Network cable.");
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
        logger.info("ProjectE Storage Bridge initialized. The cable requires a neighboring ProjectE Transmutation Table and an SSN network.");
    }

    public static Logger logger() {
        return logger;
    }
}
