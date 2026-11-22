package com.mikael8367.nexustech.experiment.tenmachines;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.registries.IForgeRegistry;

/** Deliberately NOT an event subscriber. Integration must explicitly call the methods. */
public final class ExperimentalRegistry {
 private static final Map<MachineKind,ProcessingBlock> BLOCKS=new EnumMap<>(MachineKind.class);
 static {for(MachineKind k:MachineKind.values())BLOCKS.put(k,new ProcessingBlock(k));}
 private ExperimentalRegistry(){}
 public static Map<MachineKind,ProcessingBlock> blocks(){return Collections.unmodifiableMap(BLOCKS);}
 public static TileEntity createTile(MachineKind k){switch(k){
  case STONE_CUTTER:return new ProcessingTile.StoneCutter(); case MOSS_ASSEMBLER:return new ProcessingTile.MossAssembler();
  case GLASS_SHAPER:return new ProcessingTile.GlassShaper(); case FIBER_SPINNER:return new ProcessingTile.FiberSpinner();
  case PAPER_PRESS:return new ProcessingTile.PaperPress(); case SUGAR_MILL:return new ProcessingTile.SugarMill();
  case BREAD_BAKER:return new ProcessingTile.BreadBaker(); case SEED_EXTRACTOR:return new ProcessingTile.SeedExtractor();
  case DYE_MIXER:return new ProcessingTile.DyeMixer(); case BONE_MILL:return new ProcessingTile.BoneMill();default:throw new IllegalArgumentException();
 }}
 private static Class<? extends TileEntity> tileClass(MachineKind k){return createTile(k).getClass();}
 public static void registerBlocks(IForgeRegistry<Block> registry){for(ProcessingBlock b:BLOCKS.values())registry.register(b);}
 public static void registerItems(IForgeRegistry<Item> registry){for(ProcessingBlock b:BLOCKS.values())registry.register(new ItemBlock(b).setRegistryName(b.getRegistryName()));}
 public static void registerTiles(){for(MachineKind k:MachineKind.values())GameRegistry.registerTileEntity(tileClass(k),new ResourceLocation("nexustech",k.id));}
}
