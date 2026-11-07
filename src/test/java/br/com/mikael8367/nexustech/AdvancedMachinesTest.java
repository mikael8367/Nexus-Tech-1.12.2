package br.com.mikael8367.nexustech;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;

/** Regression checks for machine energy limits, radius validation and persisted state. */
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

        // TileEntity.writeToNBT requires a world in this Forge version; exercise the persisted
        // fields directly here, while the in-game save/load path uses the same NBT keys.
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
