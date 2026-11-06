package br.com.mikael8367.nexustech;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

@Mod(modid = NexusTechMod.MOD_ID, name = NexusTechMod.MOD_NAME, version = NexusTechMod.VERSION,
        dependencies = "required-after:forge@[14.23.5.2860,)")
public final class NexusTechMod {
    public static final String MOD_ID = "nexustech";
    public static final String MOD_NAME = "Nexus Tech Machines";
    public static final String VERSION = "0.1.0";
    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(new TechRegistry());
        event.getModLog().info("Nexus Tech machines and Forge Energy network registered.");
    }
}
final class NexusTechCreativeTab extends CreativeTabs {
    static final NexusTechCreativeTab INSTANCE = new NexusTechCreativeTab();
    private NexusTechCreativeTab() { super("nexustech"); }
    @Override public ItemStack createIcon() {
        return TechRegistry.COAL_GENERATOR == null ? new ItemStack(Items.IRON_INGOT)
                : new ItemStack(TechRegistry.COAL_GENERATOR);
    }
}
