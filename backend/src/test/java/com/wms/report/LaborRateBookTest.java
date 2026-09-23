package com.wms.report;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LaborRateBookTest {
    @Test
    void warehouseOwnerOverrideBeatsDefault() {
        Map<String, BigDecimal> rates = new HashMap<String, BigDecimal>();
        rates.put(LaborRateBook.key("WH01", "OWN1", "PICK"), new BigDecimal("0.80"));
        assertEquals(0, LaborRateBook.resolve(rates, "WH01", "OWN1", "PICK").compareTo(new BigDecimal("0.80")));
        assertEquals(0, LaborRateBook.resolve(rates, "WH02", "OWN1", "PICK").compareTo(new BigDecimal("0.50")));
        assertEquals(0, LaborRateBook.pay(new BigDecimal("0.80"), new BigDecimal("2")).compareTo(new BigDecimal("1.60")));
    }

    @Test
    void missingWarehouseKeepsDefaultRate() {
        Map<String, BigDecimal> rates = new HashMap<String, BigDecimal>();
        rates.put(LaborRateBook.key("WH01", "OWN1", "SHIP"), new BigDecimal("9.00"));
        assertEquals(0, LaborRateBook.resolve(rates, " ", "OWN1", "SHIP").compareTo(new BigDecimal("0.40")));
    }
}
