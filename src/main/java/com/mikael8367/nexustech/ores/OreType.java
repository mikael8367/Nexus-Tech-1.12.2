package com.mikael8367.nexustech.ores;

/** Immutable ore specifications; heights are inclusive. Rates are attempts per chunk. */
public enum OreType {
    COPPER("copper", "Copper", 40, 96, 12, 8, 1),
    TIN("tin", "Tin", 32, 80, 9, 7, 1),
    ALUMINUM("aluminum", "Aluminum", 32, 96, 10, 8, 1),
    ZINC("zinc", "Zinc", 24, 72, 8, 7, 1),
    NICKEL("nickel", "Nickel", 8, 48, 6, 6, 2),
    SILVER("silver", "Silver", 5, 40, 5, 5, 2),
    LEAD("lead", "Lead", 8, 48, 7, 6, 2),
    URANIUM("uranium", "Uranium", 4, 28, 3, 4, 2),
    TITANIUM("titanium", "Titanium", 4, 24, 3, 5, 2),
    TUNGSTEN("tungsten", "Tungsten", 4, 20, 2, 4, 3);

    public final String name, dictionarySuffix;
    public final int minY, maxY, attempts, veinSize, harvestLevel;
    OreType(String name, String suffix, int minY, int maxY, int attempts, int veinSize, int level) {
        this.name = name; this.dictionarySuffix = suffix; this.minY = minY; this.maxY = maxY;
        this.attempts = attempts; this.veinSize = veinSize; this.harvestLevel = level;
    }
    public String oreId() { return name + "_ore"; }
    public String ingotId() { return name + "_ingot"; }
    public String oreKey() { return "ore" + dictionarySuffix; }
    public String ingotKey() { return "ingot" + dictionarySuffix; }
}
