package br.com.mikael8367.projectestoragebridge;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.Logger;

@Mod(
    modid = ProjectEStorageBridge.MOD_ID,
    name = ProjectEStorageBridge.MOD_NAME,
    version = ProjectEStorageBridge.VERSION,
    dependencies = "required-after:forge@[14.23.5.2864,);required-after:projecte;required-after:storagenetwork"
)
public final class ProjectEStorageBridge {
    public static final String MOD_ID = "projectestoragebridge";
    public static final String MOD_NAME = "ProjectE Storage Bridge";
    public static final String VERSION = "0.1.0";

    private static Logger logger;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        logger = event.getModLog();
        logger.info("ProjectE Storage Bridge bootstrap loaded. Integration is not implemented yet.");
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        logger.info("Waiting for verified ProjectE and Simple Storage Network integration hooks.");
    }
}
