package br.com.mikael8367.projectestoragebridge;

import java.util.Collections;

import io.netty.buffer.ByteBuf;
import mrriegel.storagenetwork.block.master.TileMaster;
import mrriegel.storagenetwork.gui.IStorageContainer;
import mrriegel.storagenetwork.network.StackRefreshClientMessage;
import mrriegel.storagenetwork.network.StackResponseClientMessage;
import mrriegel.storagenetwork.registry.PacketRegistry;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IThreadListener;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Deposits the cursor stack into the currently open SSN network.
 *
 * The server computes the accepted amount from TileMaster's remainder result,
 * then shrinks the cursor stack by exactly that amount. No client-provided
 * item count or network position is trusted.
 */
public final class BridgeInsertMessage implements IMessage {
    private int mouseButton;

    public BridgeInsertMessage() {
    }

    public BridgeInsertMessage(int mouseButton) {
        this.mouseButton = mouseButton;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        mouseButton = buffer.readInt();
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeInt(mouseButton);
    }

    public static final class Handler implements IMessageHandler<BridgeInsertMessage, IMessage> {
        @Override
        public IMessage onMessage(final BridgeInsertMessage message, final MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().player;
            if (!(player.world instanceof WorldServer)) {
                return null;
            }

            IThreadListener thread = (WorldServer) player.world;
            thread.addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    deposit(player, message.mouseButton);
                }
            });
            return null;
        }

        private static void deposit(EntityPlayerMP player, int mouseButton) {
            if (player == null || !player.isEntityAlive()
                    || (mouseButton != 0 && mouseButton != 1)
                    || !(player.openContainer instanceof IStorageContainer)) {
                return;
            }

            IStorageContainer storageContainer = (IStorageContainer) player.openContainer;
            TileMaster master = storageContainer.getTileMaster();
            if (master == null || master.isInvalid()
                    || master.getWorld() != player.world
                    || !player.openContainer.canInteractWith(player)) {
                return;
            }

            ItemStack cursor = player.inventory.getItemStack();
            if (cursor == null || cursor.isEmpty()) {
                return;
            }

            ItemStack offered = cursor.copy();
            if (mouseButton == 1) {
                offered.setCount(1);
            }
            int offeredCount = offered.getCount();

            // TileMaster returns the exact number of items it could not insert.
            int remainder = master.insertStack(offered, false);
            if (remainder < 0 || remainder > offeredCount) {
                return;
            }

            int accepted = offeredCount - remainder;
            if (accepted > 0) {
                cursor.shrink(accepted);
                if (cursor.isEmpty()) {
                    player.inventory.setItemStack(ItemStack.EMPTY);
                }
            }

            ItemStack carried = player.inventory.getItemStack();
            PacketRegistry.INSTANCE.sendTo(new StackResponseClientMessage(carried), player);
            PacketRegistry.INSTANCE.sendTo(
                    new StackRefreshClientMessage(master.getStacks(), Collections.<ItemStack>emptyList()),
                    player);
            player.openContainer.detectAndSendChanges();
        }
    }
}
