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

    /** Max 3 heat. Wet fuel always cools by 4 + passive 1: no positive hold is possible. */
    int requestedHeat(int temperature, int water) {
        if (!enabled) return 3;
        if (temperature >= 1000) return 0;
        if (water > 0) return temperature == 0 && target == 0 ? 3 : 0;
        long need = (long) target - temperature + 1; // compensate passive -1
        return (int) Math.max(0L, Math.min(3L, need));
    }
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
