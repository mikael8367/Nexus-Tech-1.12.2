package com.mikael8367.nexustech.techtools;

import com.mikael8367.nexustech.NexusTech;
import net.minecraft.item.Item;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Mod.EventBusSubscriber(modid = NexusTech.MODID)
public final class TechToolsRegistry {
    public static final Item PICKAXE = create("techtool_pickaxe", TechTool.Kind.PICKAXE);
    public static final Item AXE = create("techtool_axe", TechTool.Kind.AXE);
    public static final Item SHOVEL = create("techtool_shovel", TechTool.Kind.SHOVEL);
    public static final Item SWORD = new TechSword().setRegistryName(NexusTech.MODID, "techtool_sword").setUnlocalizedName("techtool_sword").setCreativeTab(NexusTech.CREATIVE_TAB);
    public static final Item HOE = new TechHoe().setRegistryName(NexusTech.MODID, "techtool_hoe").setUnlocalizedName("techtool_hoe").setCreativeTab(NexusTech.CREATIVE_TAB);
    public static final Item DRILL_3 = drill("hand_drill_3", 3);
    public static final Item DRILL_5 = drill("hand_drill_5", 5);
    public static final Item DRILL_10 = drill("hand_drill_10", 10);
    private static Item drill(String id, int size) {
        return new HandDrill(size).setRegistryName(NexusTech.MODID, id).setUnlocalizedName(id).setCreativeTab(NexusTech.CREATIVE_TAB);
    }
    private static Item create(String id, TechTool.Kind kind) {
        return new TechTool(kind).setRegistryName(NexusTech.MODID, id).setUnlocalizedName(id).setCreativeTab(NexusTech.CREATIVE_TAB);
    }
    @SubscribeEvent public static void register(RegistryEvent.Register<Item> event) {
        event.getRegistry().registerAll(PICKAXE, AXE, SHOVEL, SWORD, HOE, DRILL_3, DRILL_5, DRILL_10);
        TechToolsNetwork.init();
    }
    private TechToolsRegistry() { }
}
