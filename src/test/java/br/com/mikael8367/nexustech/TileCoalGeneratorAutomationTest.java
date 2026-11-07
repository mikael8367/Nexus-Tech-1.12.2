package br.com.mikael8367.nexustech;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import org.junit.BeforeClass;
import org.junit.Test;

/** Regression coverage for hopper-compatible coal generator input. */
public class TileCoalGeneratorAutomationTest {
    /** Vanilla item/block registries must be bootstrapped before ItemStack tests run. */
    @BeforeClass
    public static void bootstrapMinecraftRegistries() {
        Bootstrap.register();
    }

    @Test
    public void exposesOneValidatedInputSlotAndRejectsExtraction() {
        TileCoalGenerator generator = new TileCoalGenerator();
        IItemHandler handler = generator.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);

        assertTrue(generator.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null));
        assertEquals(1, handler.getSlots());
        assertTrue(handler.insertItem(0, new ItemStack(Items.COAL, 3), false).isEmpty());
        assertEquals(3, handler.getStackInSlot(0).getCount());

        assertTrue(handler.extractItem(0, 1, false).isEmpty());
        assertEquals(3, handler.getStackInSlot(0).getCount());
    }

    @Test
    public void rejectsNonFuelThroughAutomation() {
        TileCoalGenerator generator = new TileCoalGenerator();
        IItemHandler handler = generator.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
        ItemStack invalid = new ItemStack(Items.IRON_INGOT, 1);

        ItemStack remainder = handler.insertItem(0, invalid, false);
        assertFalse(remainder.isEmpty());
        assertEquals(1, remainder.getCount());
        assertTrue(handler.getStackInSlot(0).isEmpty());
    }
}
