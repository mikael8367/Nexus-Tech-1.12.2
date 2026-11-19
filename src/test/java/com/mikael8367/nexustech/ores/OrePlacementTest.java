package com.mikael8367.nexustech.ores;

import java.util.*;

/** Executable with plain Java 8; no Minecraft runtime is needed. */
public final class OrePlacementTest {
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    private static List<String> sample(long seed, int cx, int cz) {
        List<String> results = new ArrayList<>();
        EnumMap<OreType, Integer> counts = new EnumMap<>(OreType.class);
        OrePlacement.generate(new Random(seed), cx, cz, (type, x, y, z) -> {
            check(Math.floorDiv(x, 16) == cx && Math.floorDiv(z, 16) == cz, "cross-chunk access");
            check(y >= type.minY && y <= type.maxY, "height for " + type);
            counts.put(type, counts.getOrDefault(type, 0) + 1);
            results.add(type + ":" + x + ":" + y + ":" + z);
        });
        for (OreType type : OreType.values())
            check(counts.get(type) == type.attempts * type.veinSize, "attempts for " + type);
        return results;
    }
    public static void main(String[] args) {
        check(OreType.values().length == 10, "ten ores");
        Set<String> ids = new HashSet<>(), keys = new HashSet<>();
        for (OreType type : OreType.values()) {
            check(type.minY >= 1 && type.maxY < 256 && type.minY <= type.maxY, "valid height");
            check(type.attempts > 0 && type.veinSize > 0, "positive generation rates");
            check(ids.add(type.oreId()) && ids.add(type.ingotId()), "unique ids");
            check(keys.add(type.oreKey()) && keys.add(type.ingotKey()), "unique dictionary keys");
            check(type.oreKey().equals("ore" + type.dictionarySuffix), "ore mapping");
            check(type.ingotKey().equals("ingot" + type.dictionarySuffix), "ingot mapping");
        }
        for (int seed = 0; seed < 1000; seed++) {
            int cx = seed % 55 - 27, cz = seed % 71 - 35;
            List<String> a = sample(seed, cx, cz), b = sample(seed, cx, cz);
            check(a.equals(b), "deterministic random stream");
        }
        check(!sample(0, 0, 0).equals(sample(1, 0, 0)), "seed influences placement");
        System.out.println("PASS: 1000 deterministic positive/negative chunks, bounds, rates and IDs");
    }
}
