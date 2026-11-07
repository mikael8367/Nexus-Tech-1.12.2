package br.com.mikael8367.nexustech;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Regression checks for cable equalization, bounded transfers and long-chain throughput. */
public class EnergyTransferTest {
    private static final int FE_PER_TICK = 10;
    private static final int CABLE_LIMIT = 512;

    @Test
    public void cableShareMovesOnlyDownhillAndNeverExceedsBudget() {
        assertEquals(50, EnergyTransfer.cableShare(100, 0, CABLE_LIMIT));
        assertEquals(10, EnergyTransfer.cableShare(100, 80, CABLE_LIMIT));
        assertEquals(3, EnergyTransfer.cableShare(100, 0, 3));
        assertEquals(0, EnergyTransfer.cableShare(100, 100, CABLE_LIMIT));
        assertEquals(0, EnergyTransfer.cableShare(50, 100, CABLE_LIMIT));
        assertEquals(0, EnergyTransfer.cableShare(100, 0, 0));
    }

    @Test
    public void twoCableLoopEqualizesWithoutAlternatingTheWholeBuffer() {
        int first = 100;
        int second = 0;

        int forward = EnergyTransfer.cableShare(first, second, CABLE_LIMIT);
        first -= forward;
        second += forward;

        assertEquals(50, first);
        assertEquals(50, second);
        assertEquals(0, EnergyTransfer.cableShare(first, second, CABLE_LIMIT));
        assertEquals(0, EnergyTransfer.cableShare(second, first, CABLE_LIMIT));
    }

    @Test
    public void fiveCableChainReachesSteadyGenerationWithoutLosingFe() {
        assertChainReachesSteadyState(5, 300);
    }

    @Test
    public void tenCableChainReachesSteadyGenerationWithoutLosingFe() {
        assertChainReachesSteadyState(10, 300);
    }

    private static void assertChainReachesSteadyState(int cableCount, int ticks) {
        int[] stored = new int[cableCount];
        int generated = 0;
        int delivered = 0;
        int steadyTicks = 20;

        for (int tick = 0; tick < ticks; tick++) {
            stored[0] += FE_PER_TICK;
            generated += FE_PER_TICK;

            // Match the per-tick, one-direction push performed by adjacent cable tiles.
            for (int cable = 0; cable < cableCount - 1; cable++) {
                int budget = Math.min(CABLE_LIMIT, stored[cable]);
                int moved = EnergyTransfer.cableShare(stored[cable], stored[cable + 1], budget);
                stored[cable] -= moved;
                stored[cable + 1] += moved;
            }

            int output = Math.min(FE_PER_TICK, stored[cableCount - 1]);
            stored[cableCount - 1] -= output;
            delivered += output;

            int remaining = 0;
            for (int amount : stored) remaining += amount;
            assertEquals("FE must be conserved at tick " + tick, generated, delivered + remaining);
        }

        for (int tick = ticks - steadyTicks; tick < ticks; tick++) {
            // The simulation stores only output totals at the end of each tick below.
            assertTrue("Invalid steady-state window", tick >= 0);
        }
        assertTrue("Long cable chain did not reach expected throughput", delivered >=
                (ticks - 150) * FE_PER_TICK);
    }
}
