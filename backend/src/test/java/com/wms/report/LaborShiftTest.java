package com.wms.report;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LaborShiftTest {
    @Test
    void splitsTheDayIntoThreeShifts() {
        assertEquals("NIGHT", LaborShift.code(0));
        assertEquals("NIGHT", LaborShift.code(7));
        assertEquals("MORNING", LaborShift.code(8));
        assertEquals("MORNING", LaborShift.code(15));
        assertEquals("AFTERNOON", LaborShift.code(16));
        assertEquals("AFTERNOON", LaborShift.code(23));
        assertEquals("早班", LaborShift.label("MORNING"));
        assertEquals("中班", LaborShift.label("AFTERNOON"));
        assertEquals("晚班", LaborShift.label("NIGHT"));
    }
}
