package com.mikael8367.nexustech.ores;

import java.util.Random;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraftforge.fml.common.IWorldGenerator;

/** Replaces natural stone only, in the already-loaded chunk, and only in dimension 0. */
public final class OreWorldGenerator implements IWorldGenerator {
    @Override public void generate(Random random, int chunkX, int chunkZ, World world,
            IChunkGenerator generator, IChunkProvider provider) {
        if (world.isRemote || world.provider.getDimension() != 0) return;
        OrePlacement.generate(random, chunkX, chunkZ, (type, x, y, z) -> {
            BlockPos pos = new BlockPos(x, y, z);
            if (!world.isBlockLoaded(pos)) return;
            if (world.getBlockState(pos).getBlock() == Blocks.STONE)
                world.setBlockState(pos, OreRegistry.ORES.get(type).getDefaultState(), 2);
        });
    }
}
