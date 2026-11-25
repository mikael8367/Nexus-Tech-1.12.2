package com.mikael8367.nexustech.experiment.tenmachines;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/** Independent recipe specification; exact vanilla item identity and metadata only. */
public enum MachineKind {
 STONE_CUTTER("nexus_stone_cutter"), MOSS_ASSEMBLER("nexus_moss_assembler"), GLASS_SHAPER("nexus_glass_shaper"), FIBER_SPINNER("nexus_fiber_spinner"), PAPER_PRESS("nexus_paper_press"), SUGAR_MILL("nexus_sugar_mill"), BREAD_BAKER("nexus_bread_baker"), SEED_EXTRACTOR("nexus_seed_extractor"), DYE_MIXER("nexus_dye_mixer"), BONE_MILL("nexus_bone_mill");
 public final String id;
 MachineKind(String id) { this.id = id; }
 private static boolean item(ItemStack s, Item i, int meta) { return !s.isEmpty() && s.getItem() == i && (meta < 0 ? s.getMetadata() >= 0 && s.getMetadata() <= 15 : s.getMetadata() == meta); }
 private static boolean block(ItemStack s, net.minecraft.block.Block b, int meta) { return item(s, Item.getItemFromBlock(b), meta); }
 public boolean accepts(int slot, ItemStack s) {
  if (s == null || s.isEmpty() || s.hasTagCompound() || s.getItem().hasContainerItem(s)) return false;
  if (slot == 1) return this == MOSS_ASSEMBLER ? block(s, Blocks.VINE, 0) : this == DYE_MIXER && item(s, Items.DYE, 2);
  if (slot != 0) return false;
  switch (this) {
   case STONE_CUTTER: return block(s, Blocks.STONE, 0);
   case MOSS_ASSEMBLER: return block(s, Blocks.STONEBRICK, 0);
   case GLASS_SHAPER: return block(s, Blocks.GLASS, 0);
   case FIBER_SPINNER: return block(s, Blocks.WOOL, -1);
   case PAPER_PRESS: case SUGAR_MILL: return item(s, Items.REEDS, 0);
   case BREAD_BAKER: return item(s, Items.WHEAT, 0);
   case SEED_EXTRACTOR: return block(s, Blocks.PUMPKIN, 0) || item(s, Items.MELON, 0);
   case DYE_MIXER: return item(s, Items.DYE, 4);
   case BONE_MILL: return item(s, Items.BONE, 0);
   default: return false;
  }
 }
 public int required(int slot) {
  if (slot == 1) return this == MOSS_ASSEMBLER || this == DYE_MIXER ? 1 : 0;
  if (slot != 0) return 0;
  switch(this) { case STONE_CUTTER: return 4; case GLASS_SHAPER: case PAPER_PRESS: case BREAD_BAKER: return 3; default: return 1; }
 }
 public ItemStack result(ItemStack a, ItemStack b) {
  if (!accepts(0,a) || a.getCount() < required(0) || (required(1)>0 && (!accepts(1,b) || b.getCount() < required(1)))) return ItemStack.EMPTY;
  switch(this) {
   case STONE_CUTTER: return new ItemStack(Blocks.STONEBRICK,4,0);
   case MOSS_ASSEMBLER: return new ItemStack(Blocks.STONEBRICK,1,1);
   case GLASS_SHAPER: return new ItemStack(Blocks.GLASS_PANE,8,0);
   case FIBER_SPINNER: return new ItemStack(Items.STRING,4,0);
   case PAPER_PRESS: return new ItemStack(Items.PAPER,3,0);
   case SUGAR_MILL: return new ItemStack(Items.SUGAR,1,0);
   case BREAD_BAKER: return new ItemStack(Items.BREAD,1,0);
   case SEED_EXTRACTOR: return block(a,Blocks.PUMPKIN,0) ? new ItemStack(Items.PUMPKIN_SEEDS,4,0) : new ItemStack(Items.MELON_SEEDS,1,0);
   case DYE_MIXER: return new ItemStack(Items.DYE,2,6);
   case BONE_MILL: return new ItemStack(Items.DYE,3,15);
   default: return ItemStack.EMPTY;
  }
 }
}
