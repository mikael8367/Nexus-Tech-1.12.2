package br.com.mikael8367.nexustech;
import org.junit.Test;
import static org.junit.Assert.*;

public class EnergyBufferTest {
    @Test public void receiveRespectsRateCapacityAndSimulation() {
        EnergyBuffer buffer = new EnergyBuffer(100, 20, 10);
        assertEquals(20, buffer.receiveEnergy(80, false));
        assertEquals(20, buffer.getEnergyStored());
        assertEquals(20, buffer.receiveEnergy(80, true));
        assertEquals(20, buffer.getEnergyStored());
        buffer.setEnergyStored(95);
        assertEquals(5, buffer.receiveEnergy(80, false));
        assertEquals(100, buffer.getEnergyStored());
        assertFalse(buffer.canReceive());
    }
    @Test public void extractRespectsStoredAmountAndRate() {
        EnergyBuffer buffer = new EnergyBuffer(100, 10, 15);
        buffer.addInternal(80);
        assertEquals(15, buffer.extractEnergy(50, false));
        assertEquals(65, buffer.getEnergyStored());
    }
    @Test public void generatorBufferRejectsExternalInput() {
        EnergyBuffer buffer = new EnergyBuffer(100, 0, 10);
        assertEquals(0, buffer.receiveEnergy(20, false));
        assertEquals(10, buffer.addInternal(10));
        assertFalse(buffer.canReceive());
        assertTrue(buffer.canExtract());
    }
    @Test public void invalidAmountsDoNotChangeEnergy() {
        EnergyBuffer buffer = new EnergyBuffer(100, 10, 10);
        assertEquals(0, buffer.receiveEnergy(-1, false));
        assertEquals(0, buffer.extractEnergy(-1, false));
        assertEquals(0, buffer.addInternal(-10));
        assertEquals(0, buffer.getEnergyStored());
    }

    @Test public void consumersCanSpendTheirOwnEnergyDespiteZeroExternalExtract() {
        // Electric furnace / anvil buffers expose 0 FE/t extraction to neighbours.
        EnergyBuffer buffer = new EnergyBuffer(100, 50, 0);
        buffer.receiveEnergy(40, false);
        assertEquals(0, buffer.extractEnergy(10, false));
        assertTrue(buffer.consumeInternal(10));
        assertEquals(30, buffer.getEnergyStored());
        assertFalse(buffer.consumeInternal(31));
        assertEquals(30, buffer.getEnergyStored());
        assertFalse(buffer.consumeInternal(0));
        assertFalse(buffer.consumeInternal(-5));
    }
    @Test public void cablesOnlyShareDownhillAndConverge() {
        assertEquals(0, EnergyTransfer.cableShare(100, 100, 512));
        assertEquals(0, EnergyTransfer.cableShare(100, 101, 512));
        assertEquals(0, EnergyTransfer.cableShare(101, 100, 512));
        assertEquals(50, EnergyTransfer.cableShare(100, 0, 512));
        assertEquals(512, EnergyTransfer.cableShare(4000, 0, 512));
        assertEquals(0, EnergyTransfer.cableShare(4000, 0, 0));
        // Simulate two cables exchanging every tick: totals are conserved and they never oscillate.
        int a = 4000, b = 0;
        for (int tick = 0; tick < 50; tick++) {
            int ab = EnergyTransfer.cableShare(a, b, 512); a -= ab; b += ab;
            int ba = EnergyTransfer.cableShare(b, a, 512); b -= ba; a += ba;
            assertEquals(4000, a + b);
            assertTrue(Math.abs(a - b) <= 1 || ba == 0);
        }
        assertTrue(Math.abs(a - b) <= 1);
    }
}
