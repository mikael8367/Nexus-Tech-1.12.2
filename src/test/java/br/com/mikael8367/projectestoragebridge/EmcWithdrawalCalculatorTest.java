package br.com.mikael8367.projectestoragebridge;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class EmcWithdrawalCalculatorTest {

    @Test
    public void rejectsInvalidBalancesAndRequests() {
        assertEquals(0, EmcWithdrawalCalculator.maxAffordable(0, 10, 64, 64));
        assertEquals(0, EmcWithdrawalCalculator.maxAffordable(100, 0, 64, 64));
        assertEquals(0, EmcWithdrawalCalculator.maxAffordable(100, 10, 0, 64));
        assertEquals(0, EmcWithdrawalCalculator.maxAffordable(100, 10, 64, 0));
    }

    @Test
    public void neverExceedsAffordableEmc() {
        assertEquals(3, EmcWithdrawalCalculator.maxAffordable(35, 10, 64, 64));
        assertEquals(0, EmcWithdrawalCalculator.maxAffordable(9, 10, 64, 64));
    }

    @Test
    public void respectsRequestAndStackLimits() {
        assertEquals(8, EmcWithdrawalCalculator.maxAffordable(1000, 1, 8, 64));
        assertEquals(16, EmcWithdrawalCalculator.maxAffordable(1000, 1, 64, 16));
    }

    @Test
    public void guardsCostOverflow() {
        assertEquals(Long.MAX_VALUE, EmcWithdrawalCalculator.costFor(Long.MAX_VALUE, 2));
        assertEquals(30L, EmcWithdrawalCalculator.costFor(10L, 3));
    }
}
