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
    public void exactBalanceCanPayForExactlyOneOrMoreItems() {
        assertEquals(1, EmcWithdrawalCalculator.maxAffordable(10, 10, 64, 64));
        assertEquals(2, EmcWithdrawalCalculator.maxAffordable(20, 10, 64, 64));
        assertEquals(1, EmcWithdrawalCalculator.maxAffordable(19, 10, 64, 64));
    }

    @Test
    public void largeBalanceNeverExceedsRequestedOrStackLimit() {
        assertEquals(64, EmcWithdrawalCalculator.maxAffordable(Long.MAX_VALUE, 1, 128, 64));
        assertEquals(7, EmcWithdrawalCalculator.maxAffordable(Long.MAX_VALUE, 1, 7, 64));
    }

    @Test
    public void rejectsCostOverflow() {
        assertEquals(-1L, EmcWithdrawalCalculator.costFor(Long.MAX_VALUE, 2));
        assertEquals(30L, EmcWithdrawalCalculator.costFor(10L, 3));
        assertEquals(0L, EmcWithdrawalCalculator.costFor(0L, 3));
        assertEquals(0L, EmcWithdrawalCalculator.costFor(10L, 0));
        assertEquals(Long.MAX_VALUE, EmcWithdrawalCalculator.costFor(Long.MAX_VALUE, 1));
    }
}
