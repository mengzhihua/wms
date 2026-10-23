package com.wms.integration.client;

import com.wms.inventory.entity.Inventory;
import com.wms.outbound.entity.ShipOrder;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TmsShipmentTest {
    @Test
    void installWindowIsAShipmentAndPlainShipIsNot() {
        ShipOrder bulky = new ShipOrder();
        bulky.setCode("SO-B1");
        bulky.setWarehouseCode("WH-BULK");
        bulky.setDeliveryWindow("明天 14:00-16:00");
        bulky.setInstallRequired(true);
        bulky.setAddress("上海市青浦区");
        Map<String, Object> body = TmsShipment.payload(bulky);
        assertEquals("SO-B1", body.get("wmsOrderCode"));
        assertEquals(Boolean.TRUE, body.get("installRequired"));
        assertEquals("明天 14:00-16:00", body.get("deliveryWindow"));

        ShipOrder plain = new ShipOrder();
        plain.setCode("SO-P1");
        assertNull(TmsShipment.payload(plain));
    }

    @Test
    void liabilityCountsOnlyVendorOwnedDays() {
        Inventory vendor = new Inventory();
        vendor.setWarehouseCode("WH-VMI");
        vendor.setOwnerCode("OWN01");
        vendor.setItemCode("IT-VMI");
        vendor.setQty(new BigDecimal("3"));
        vendor.setOwnership("VENDOR");
        vendor.setReceiveDate(LocalDate.of(2026, 10, 20));
        Inventory customer = new Inventory();
        customer.setQty(new BigDecimal("9"));
        customer.setOwnership("CUSTOMER");
        customer.setItemCode("IT-OWN");
        customer.setReceiveDate(LocalDate.of(2026, 1, 1));
        Map<String, Object> row = ConsignmentLiability.rows(
                java.util.Arrays.asList(vendor, customer), LocalDate.of(2026, 10, 23)).get(0);
        assertEquals(3L, ((Number) row.get("ageDays")).longValue());
        assertEquals(new BigDecimal("9"), row.get("statedAmount"));
        assertEquals(0, ConsignmentLiability.rows(Collections.singletonList(customer), LocalDate.of(2026, 10, 23)).size());
    }
}
