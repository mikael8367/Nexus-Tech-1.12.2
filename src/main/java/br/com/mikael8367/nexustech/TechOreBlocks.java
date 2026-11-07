package br.com.mikael8367.nexustech;

import java.util.Random;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.Item;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

/** Simple overworld ores. Drops are material items, not the ore block itself. */
final class BlockTechOre extends Block {
    private final Item drop;

    BlockTechOre(String name, Item drop) {
        super(Material.ROCK);
        this.drop = drop;
        setRegistryName(NexusTechMod.MOD_ID, name);
        setTranslationKey(NexusTechMod.MOD_ID + "." + name);
        setCreativeTab(NexusTechCreativeTab.INSTANCE);
        setHardness(3.0F);
        setResistance(5.0F);
        setHarvestLevel("pickaxe", 2);
    }

    @Override public Item getItemDropped(IBlockState state, Random random, int fortune) { return drop; }

    @Override public int quantityDropped(Random random) { return 1; }

    @Override public int quantityDroppedWithBonus(int fortune, Random random) {
        if (fortune <= 0) return 1;
        return 1 + random.nextInt(Math.min(3, fortune + 1));
    }

    @Override public int getExpDrop(IBlockState state, net.minecraft.world.IBlockAccess world,
            net.minecraft.util.math.BlockPos pos, int fortune) {
        return MathHelper.getInt(RANDOM, 2, 5);
    }

    private static final Random RANDOM = new Random();
}
