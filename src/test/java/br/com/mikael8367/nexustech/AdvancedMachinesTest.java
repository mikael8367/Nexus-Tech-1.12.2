package br.com.mikael8367.nexustech;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import org.junit.Test;

/** Regression checks for advanced machine energy limits, inventory validation and persistence. */
public class AdvancedMachinesTest {
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
        assertEquals(TileNuclearReactorCore.FUEL_TICKS, reactor.getFuelTicks());
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
}
