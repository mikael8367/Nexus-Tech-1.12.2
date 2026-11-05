package br.com.mikael8367.projectestoragebridge;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemStack;

public final class BridgeCreativeTab extends CreativeTabs {
    public static final BridgeCreativeTab INSTANCE = new BridgeCreativeTab();

    private BridgeCreativeTab() {
        super(ProjectEStorageBridge.MOD_ID);
    }

    @Override
    public ItemStack getTabIconItem() {
        return BridgeRegistry.CABLE == null ? ItemStack.EMPTY : new ItemStack(BridgeRegistry.CABLE);
    }
}
