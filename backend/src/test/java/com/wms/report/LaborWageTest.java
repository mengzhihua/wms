package com.wms.report;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class LaborWageTest {
    @Test
    void pickTenPiecesPaysFiveYuan() {
        assertEquals(0, LaborWage.pay("PICK", new BigDecimal("10")).compareTo(new BigDecimal("5.00")));
        assertEquals(0, LaborWage.pay("RECEIVE", new BigDecimal("3")).compareTo(new BigDecimal("0.60")));
    }

    @Test
    void unknownNodeOrEmptyQtyPaysNothing() {
        assertEquals(0, LaborWage.pay("COUNT", new BigDecimal("8")).compareTo(new BigDecimal("0.00")));
        assertEquals(0, LaborWage.pay("SHIP", null).compareTo(new BigDecimal("0.00")));
    }
}
