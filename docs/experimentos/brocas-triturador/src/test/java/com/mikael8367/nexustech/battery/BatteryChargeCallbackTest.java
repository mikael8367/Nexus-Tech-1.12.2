package com.mikael8367.nexustech.battery;

import java.lang.reflect.Field;
import java.util.Collections;
import com.mikael8367.nexustech.techtools.TechToolsRegistry;
import net.minecraft.init.Bootstrap;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityDispatcher;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;

/** Test-only reflective capability injection; not a real server or transaction. */
public final class BatteryChargeCallbackTest {
    static void check(boolean ok, String msg) { if (!ok) throw new AssertionError(msg); }
    static void inject(final ItemStack stack, final int mode) throws Exception {
        ICapabilitySerializable<NBTTagCompound> evil = new ICapabilitySerializable<NBTTagCompound>() {
            public boolean hasCapability(Capability<?> c, EnumFacing side) { return false; }
            public <T> T getCapability(Capability<T> c, EnumFacing side) { return null; }
            public NBTTagCompound serializeNBT() {
                if (mode == 0) stack.getTagCompound().setInteger("NexusToolEnergy", 99);
                if (mode == 1) stack.setCount(2);
                if (mode == 2) { stack.getTagCompound().setString("other", "changed"); throw new IllegalStateException("after mutation"); }
                return new NBTTagCompound();
            }
            public void deserializeNBT(NBTTagCompound nbt) { }
        };
        Field field = ItemStack.class.getDeclaredField("capabilities");
        field.setAccessible(true);
        field.set(stack, new CapabilityDispatcher(Collections.<ResourceLocation, ICapabilityProvider>singletonMap(new ResourceLocation("test", "evil"), evil)));
    }
    public static void main(String[] args) throws Exception {
        Bootstrap.register();
        for (Item tool : new Item[]{ TechToolsRegistry.DRILL_3, TechToolsRegistry.DRILL_5, TechToolsRegistry.DRILL_10 }) {
            for (int mode = 0; mode < 3; mode++) {
                ItemStack stack = new ItemStack(tool);
                stack.setTagCompound(new NBTTagCompound());
                inject(stack, mode);
                try {
                    BatteryChargePlan result = BatteryChargePlan.prepare(stack, 100);
                    check(mode != 2 && result == null, "changed source must invalidate plan");
                } catch (IllegalStateException expected) {
                    check(mode == 2 && "changed".equals(stack.getTagCompound().getString("other")), "callback exception is propagated and mutation not rolled back");
                }
            }
        }
        Bootstrap.printToSYSOUT("PASS actual serialization callbacks against detached plans; no transaction tested");
    }
}
