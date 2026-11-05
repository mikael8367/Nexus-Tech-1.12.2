package br.com.mikael8367.projectestoragebridge;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

/**
 * Local server configuration. The single-player payer safety check is deliberately
 * not configurable because SSN 1.8.3 does not identify the requesting player.
 */
public final class BridgeConfig {
    private static Configuration configuration;

    public static boolean enableVirtualStorage = true;
    public static int maxListedItemTypes = 256;

    private BridgeConfig() {
    }

    public static void load(File file) {
        configuration = new Configuration(file);
        sync();
    }

    public static void sync() {
        if (configuration == null) {
            return;
        }

        String category = "virtual_storage";
        enableVirtualStorage = configuration.getBoolean(
                "enableVirtualStorage", category, true,
                "Enables or disables the ProjectE virtual inventory in Simple Storage Network.");

        maxListedItemTypes = configuration.getInt(
                "maxListedItemTypes", category, 256, 1, 4096,
                "Maximum different learned item variants shown to the SSN network.");

        if (configuration.hasChanged()) {
            configuration.save();
        }
    }
}
