package com.mikael8367.nexustech.ores;

import java.util.Random;

/** Pure chunk-local random walks: no world or neighbor chunk access. */
public final class OrePlacement {
    public interface Sink { void place(OreType type, int x, int y, int z); }
    private OrePlacement() { }
    public static void generate(Random random, int chunkX, int chunkZ, Sink sink) {
        int baseX = chunkX << 4, baseZ = chunkZ << 4;
        for (OreType type : OreType.values()) {
            for (int attempt = 0; attempt < type.attempts; ++attempt) {
                int x = random.nextInt(16), z = random.nextInt(16);
                int y = type.minY + random.nextInt(type.maxY - type.minY + 1);
                for (int i = 0; i < type.veinSize; ++i) {
                    sink.place(type, baseX + x, y, baseZ + z);
                    switch (random.nextInt(6)) {
                    case 0: x = Math.max(0, x - 1); break;
                    case 1: x = Math.min(15, x + 1); break;
                    case 2: z = Math.max(0, z - 1); break;
                    case 3: z = Math.min(15, z + 1); break;
                    case 4: y = Math.max(type.minY, y - 1); break;
                    default: y = Math.min(type.maxY, y + 1);
                    }
                }
            }
        }
    }
}
