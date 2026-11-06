package br.com.mikael8367.nexustech;
import org.junit.Test;
import static org.junit.Assert.*;
public class RepairMathTest {
    @Test public void repairDoesNotOverRepairOrOverdraw() {
        assertEquals(4, RepairMath.points(20, 100, 4, 25));
        assertEquals(2, RepairMath.points(2, 1000, 4, 25));
        assertEquals(0, RepairMath.points(0, 1000, 4, 25));
        assertEquals(0, RepairMath.points(10, 24, 4, 25));
        assertEquals(100, RepairMath.energyCost(4, 25));
    }
}
