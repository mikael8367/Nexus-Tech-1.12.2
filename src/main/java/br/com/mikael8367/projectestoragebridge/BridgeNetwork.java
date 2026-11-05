package br.com.mikael8367.projectestoragebridge;

import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

/** Dedicated channel for safe client-to-server bridge GUI actions. */
public final class BridgeNetwork {
    public static final SimpleNetworkWrapper CHANNEL =
            NetworkRegistry.INSTANCE.newSimpleChannel("pe_storage_bridge");

    private BridgeNetwork() {
    }

    public static void registerMessages() {
        CHANNEL.registerMessage(
                BridgeInsertMessage.Handler.class,
                BridgeInsertMessage.class,
                0,
                Side.SERVER);
    }
}
