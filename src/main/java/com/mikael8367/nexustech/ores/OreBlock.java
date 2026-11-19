package com.mikael8367.nexustech.ores;

import com.mikael8367.nexustech.NexusTech;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;

/** Ordinary 1.12 metal ore: drops itself when mined with a suitable pickaxe. */
public final class OreBlock extends Block {
    public OreBlock(OreType type) {
        super(Material.ROCK);
        setRegistryName(NexusTech.MODID, type.oreId());
        setUnlocalizedName(type.oreId());
        setCreativeTab(NexusTech.CREATIVE_TAB);
        setHardness(3.0F);
        setResistance(5.0F);
        setSoundType(SoundType.STONE);
        setHarvestLevel("pickaxe", type.harvestLevel);
    }
}
