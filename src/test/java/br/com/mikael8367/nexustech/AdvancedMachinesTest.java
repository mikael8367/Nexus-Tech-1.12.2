package br.com.mikael8367.nexustech;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import org.junit.BeforeClass;
import org.junit.Test;

/** Regression checks for advanced machine energy limits, inventory validation and persistence. */
public class AdvancedMachinesTest {
    /** Vanilla item/block registries must be bootstrapped before ItemStack tests run. */
    @BeforeClass
    public static void bootstrapMinecraftRegistries() {
        Bootstrap.register();
    }

    @Test public void reactorUsesRequestedCapacityGenerationAndExtractionLimits() {
        TileNuclearReactorCore reactor = new TileNuclearReactorCore();
        assertEquals(50000000, reactor.getEnergyCapacity());
        assertEquals(4096, TileNuclearReactorCore.FE_PER_TICK);
        reactor.energy.addInternal(50000);
        assertEquals(32768, reactor.energy.extractEnergy(50000, true));
    }

    @Test public void reactorClampsMalformedFuelAndHeatNbt() {
        TileNuclearReactorCore reactor = new TileNuclearReactorCore();
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("FuelTicks", Integer.MAX_VALUE);
        tag.setInteger("Heat", Integer.MAX_VALUE);
        reactor.readFromNBT(tag);
        // A burn timer without a matching physical fuel rod is invalid and must not run.
        assertEquals(0, reactor.getFuelTicks());
        assertEquals(TileNuclearReactorCore.MAX_HEAT, reactor.getHeat());
    }

    @Test public void reactorExposesTwoSlotsWithSeparateInputAndOutputLimits() {
        TileNuclearReactorCore reactor = new TileNuclearReactorCore();
        IItemHandler handler = reactor.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
        assertEquals(2, handler.getSlots());
        assertEquals(1, handler.getSlotLimit(0));
        assertEquals(16, handler.getSlotLimit(1));
        assertTrue(handler.getStackInSlot(0).isEmpty());
        assertTrue(handler.getStackInSlot(1).isEmpty());
        assertTrue(handler.extractItem(0, 1, true).isEmpty());
    }

    @Test public void reactorRestoresHeatRedstoneAndPendingSpentFuelStateFromNbt() {
        TileNuclearReactorCore reactor = new TileNuclearReactorCore();
        NBTTagCompound saved = new NBTTagCompound();
        saved.setInteger("FuelTicks", 0);
        saved.setInteger("Heat", 12345);
        saved.setBoolean("IsRedstonePowered", true);
        saved.setBoolean("PendingDepletedRod", true);
        reactor.readFromNBT(saved);

        assertEquals(12345, reactor.getHeat());
        assertTrue(reactor.isRedstonePowered());
        assertTrue(reactor.hasPendingDepletedRod());
    }

    @Test public void digitalMinerUsesRequestedEnergyLimitsAndCanToggle() {
        TileDigitalMiner miner = new TileDigitalMiner();
        assertEquals(10000000, miner.getEnergyCapacity());
        assertEquals(2048, TileDigitalMiner.FE_PER_BLOCK);
        assertEquals(16384, miner.energy.receiveEnergy(20000, true));
        assertEquals(5, miner.getRadius());
        assertFalse(miner.isRunning());
        miner.setRunning(true);
        assertTrue(miner.isRunning());
        miner.setRunning(false);
        assertFalse(miner.isRunning());
    }

    @Test public void digitalMinerRadiusIsCircularAndDepthIsExactlySixteenLayers() {
        assertTrue(TileDigitalMiner.isWithinRadius(1, 0, 1));
        assertTrue(TileDigitalMiner.isWithinRadius(0, -1, 1));
        assertFalse(TileDigitalMiner.isWithinRadius(1, 1, 1));
        assertTrue(TileDigitalMiner.isWithinRadius(16, 0, 16));
        assertFalse(TileDigitalMiner.isWithinRadius(16, 1, 16));
        assertEquals(62, TileDigitalMiner.getFirstMiningY(64));
        assertEquals(47, TileDigitalMiner.getLastMiningY(64));
    }

    @Test public void digitalMinerAutomationInventoryRejectsExternalInsertion() {
        TileDigitalMiner miner = new TileDigitalMiner();
        IItemHandler handler = miner.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
        ItemStack offered = new ItemStack(Items.COAL, 1);
        ItemStack remainder = handler.insertItem(0, offered, false);

        assertEquals(1, remainder.getCount());
        assertTrue(handler.getStackInSlot(0).isEmpty());
    }

    @Test public void digitalMinerAutomationCanExtractStoredDrops() {
        ItemStackHandler savedInventory = new ItemStackHandler(27);
        savedInventory.setStackInSlot(0, new ItemStack(Items.COAL, 3));
        NBTTagCompound saved = new NBTTagCompound();
        saved.setTag("Inventory", savedInventory.serializeNBT());

        TileDigitalMiner miner = new TileDigitalMiner();
        miner.readFromNBT(saved);
        IItemHandler handler = miner.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
        ItemStack extracted = handler.extractItem(0, 3, false);

        assertEquals(3, extracted.getCount());
        assertTrue(handler.getStackInSlot(0).isEmpty());
    }

    @Test public void digitalMinerClampsRadiusAndRestoresValidatedCursorFromNbt() {
        TileDigitalMiner miner = new TileDigitalMiner();
        miner.setRadius(Integer.MAX_VALUE);
        assertEquals(16, miner.getRadius());
        miner.setRadius(Integer.MIN_VALUE);
        assertEquals(1, miner.getRadius());
        miner.setRadius(5);
        miner.setRunning(true);
        net.minecraft.util.math.BlockPos cursor = miner.getCurrentPos();

        NBTTagCompound saved = new NBTTagCompound();
        saved.setBoolean("IsRunning", true);
        saved.setInteger("Radius", 5);
        saved.setBoolean("HasCurrentPos", true);
        saved.setInteger("CurrentPosX", cursor.getX());
        saved.setInteger("CurrentPosY", cursor.getY());
        saved.setInteger("CurrentPosZ", cursor.getZ());

        TileDigitalMiner restored = new TileDigitalMiner();
        restored.readFromNBT(saved);
        assertEquals(5, restored.getRadius());
        assertTrue(restored.isRunning());
        assertEquals(cursor, restored.getCurrentPos());
    }
    @Test public void fuelRodImplementsPersistentBurnTimeMethodsUsedByReactor() {
        ItemFuelRod rod = new ItemFuelRod();
        ItemStack stack = new ItemStack(rod);

        assertEquals(ItemFuelRod.MAX_BURN_TIME, rod.getBurnTimeRemaining(stack));
        assertEquals(1, rod.consumeBurnTime(stack, 1));
        assertEquals(ItemFuelRod.MAX_BURN_TIME - 1, rod.getBurnTimeRemaining(stack));
        assertEquals(3, rod.consumeBurnTime(stack, 3));
        assertEquals(ItemFuelRod.MAX_BURN_TIME - 4, rod.getBurnTimeRemaining(stack));
    }

}
