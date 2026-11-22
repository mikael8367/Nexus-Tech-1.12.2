package com.mikael8367.nexustech.techtools;

import java.util.HashSet;
import java.util.Set;

/** Standalone Java 8 test; no Minecraft runtime required. */
public final class HandDrillVolumeTest {
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    public static void main(String[] args) {
        for (int n : new int[]{3, 5, 10}) {
            int count = HandDrillVolume.count(n), min = -(n / 2), max = min + n - 1, origin = 0;
            Set<String> points = new HashSet<String>();
            for (int i = 0; i < count; i++) {
                int x = HandDrillVolume.offset(n, i, 0), y = HandDrillVolume.offset(n, i, 1), z = HandDrillVolume.offset(n, i, 2);
                check(x >= min && x <= max && y >= min && y <= max && z >= min && z <= max, "bounds");
                check(points.add(x + "," + y + "," + z), "duplicate");
                if (x == 0 && y == 0 && z == 0) origin++;
            }
            check(points.size() == n*n*n && origin == 1, "count/origin");
            check(points.contains(min + "," + min + "," + min), "lower corner");
            check(points.contains(max + "," + max + "," + max), "upper corner");
        }
        try { HandDrillVolume.count(4); throw new AssertionError("invalid size accepted"); } catch (IllegalArgumentException expected) { }
        try { HandDrillVolume.offset(10, 1000, 0); throw new AssertionError("invalid index accepted"); } catch (IllegalArgumentException expected) { }
        try { HandDrillVolume.offset(3, 0, 3); throw new AssertionError("invalid axis accepted"); } catch (IllegalArgumentException expected) { }
        System.out.println("HandDrillVolumeTest PASS: 27, 125, 1000 coordinates and invalid inputs");
    }
}
