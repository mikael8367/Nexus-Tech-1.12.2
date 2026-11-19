package com.mikael8367.nexustech.digitalmine;

import com.mikael8367.nexustech.NexusTech;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;

public final class DigitalMineCasing extends Block {
    public DigitalMineCasing() {
        super(Material.IRON);
        setRegistryName(NexusTech.MODID, "digital_mine_casing");
        setUnlocalizedName(NexusTech.MODID + ".digital_mine_casing");
        setCreativeTab(NexusTech.CREATIVE_TAB);
        setHardness(4.0F); setResistance(15.0F);
    }
}
