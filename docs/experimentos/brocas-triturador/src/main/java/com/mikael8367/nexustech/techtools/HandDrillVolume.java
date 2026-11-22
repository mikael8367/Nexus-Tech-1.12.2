package com.mikael8367.nexustech.techtools;

/** Stable xyz ordering; even cubes have one extra coordinate on the negative side. */
final class HandDrillVolume {
    static int count(int size) {
        if (size != 3 && size != 5 && size != 10) throw new IllegalArgumentException("size");
        return size * size * size;
    }
    static int offset(int size, int index, int axis) {
        int count = count(size);
        if (index < 0 || index >= count || axis < 0 || axis > 2) throw new IllegalArgumentException("index/axis");
        int divisor = axis == 0 ? 1 : axis == 1 ? size : size * size;
        return -(size / 2) + (index / divisor) % size;
    }
    private HandDrillVolume() { }
}
