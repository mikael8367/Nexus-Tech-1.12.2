package com.mikael8367.nexustech.nuclear;

import net.minecraft.nbt.NBTTagCompound;

/** Experimental, opt-in tick controller. Heat is in the existing integer units. */
final class AutoThermalControl {
    static final int DEFAULT_TARGET = 400, MAX_TARGET = 900;
    private boolean enabled;
    private boolean invalid;
    private int target = DEFAULT_TARGET;

    boolean enabled() { return enabled; }
    boolean invalid() { return invalid; }
    int target() { return target; }
    boolean setTarget(int value) {
        if (value < 0 || value > MAX_TARGET) return false;
        target = value;
        invalid = false;
        return true;
    }
    void setEnabled(boolean value) { enabled = value; invalid = false; }

    /** Valve decision only: never writes temperature and never supplies water. */
    boolean applyWater(int temperature) { return !enabled || temperature >= target; }
    void write(NBTTagCompound n) {
        n.setBoolean("AutoThermal", enabled);
        n.setInteger("AutoTarget", target);
        n.setBoolean("AutoInvalid", invalid);
    }
    void read(NBTTagCompound n) {
        enabled = false; invalid = false; target = DEFAULT_TARGET;
        boolean hasEnable = n.hasKey("AutoThermal"), hasTarget = n.hasKey("AutoTarget"), hasInvalid = n.hasKey("AutoInvalid");
        if (!hasEnable && !hasTarget && !hasInvalid) return; // legacy
        if (!n.hasKey("AutoThermal", 1) || !n.hasKey("AutoTarget", 3)
                || (hasInvalid && !n.hasKey("AutoInvalid", 1))) { invalid = true; return; }
        byte e = n.getByte("AutoThermal"), i = hasInvalid ? n.getByte("AutoInvalid") : 0;
        int t = n.getInteger("AutoTarget");
        if ((e != 0 && e != 1) || (i != 0 && i != 1) || t < 0 || t > MAX_TARGET || (i == 1 && e == 1)) {
            invalid = true; return;
        }
        target = t; invalid = i == 1; enabled = e == 1 && !invalid;
    }
}
