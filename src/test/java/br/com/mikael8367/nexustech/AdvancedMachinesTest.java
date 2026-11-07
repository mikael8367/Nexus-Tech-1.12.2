package br.com.mikael8367.nexustech;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;

/** Lightweight regression checks for advanced machine limits and persisted state validation. */
public class AdvancedMachinesTest {
    @Test
    public void reactorUsesConfiguredCapacityAndExtractionRate() {
        TileNuclearReactorCore reactor = new TileNuclearReactorCore();
        assertEquals(50000000, reactor.getEnergyCapacity());
        assertEquals(32768, TileNuclearReactorCore.FE_PER_TICK);
        reactor.energy.addInternal(50000);
        assertEquals(32768, reactor.energy.extractEnergy(50000, true));
    }

    @Test
    public void reactorClampsMalformedHeatNbt() {
        TileNuclearReactorCore reactor = new TileNuclearReactorCore();
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("Heat", TileNuclearReactorCore.MAX_HEAT + 5000);
        reactor.readFromNBT(tag);
        assertEquals(TileNuclearReactorCore.MAX_HEAT, reactor.getHeat());
    }

    @Test
    public void digitalMinerUsesConfiguredCapacityAndCanToggle() {
        TileDigitalMiner miner = new TileDigitalMiner();
        assertEquals(10000000, miner.getEnergyCapacity());
        assertEquals(2048, TileDigitalMiner.ENERGY_PER_BLOCK);
        assertEquals(8192, miner.energy.receiveEnergy(10000, true));
        assertFalse(miner.isRunning());
        miner.setRunning(true);
        assertTrue(miner.isRunning());
        miner.setRunning(false);
        assertFalse(miner.isRunning());
    }
}
