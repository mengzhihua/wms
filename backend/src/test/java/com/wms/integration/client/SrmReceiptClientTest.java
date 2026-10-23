package com.wms.integration.client;

import com.wms.inbound.entity.Asn;
import com.wms.inbound.entity.AsnLine;
import com.wms.outbound.entity.ShipOrder;
import com.wms.outbound.entity.ShipOrderLine;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SrmReceiptClientTest {
    @Test
    void receivedPurchaseSendsCumulativeQty() {
        Asn asn = new Asn();
        asn.setCode("ASN-W1");
        asn.setExternalNo("ASN-SRM-1");
        asn.setStatus("RECEIVED");
        asn.setType("PURCHASE");
        AsnLine line = new AsnLine();
        line.setItemCode("SKU001");
        line.setLotNo("LOT-1");
        line.setReceivedQty(new BigDecimal("10"));
        asn.setLines(Collections.singletonList(line));

        Map<String, Object> body = SrmReceiptClient.payload(asn);
        assertEquals("ASN-SRM-1", body.get("externalNo"));
        assertEquals(Boolean.TRUE, body.get("cumulative"));
        @SuppressWarnings("unchecked")
        Map<String, Object> item = ((java.util.List<Map<String, Object>>) body.get("lines")).get(0);
        assertEquals("SKU001", item.get("itemCode"));
        assertEquals(new BigDecimal("10"), item.get("receivedQty"));
    }

    @Test
    void unfinishedOrReturnIsNotPushed() {
        Asn receiving = new Asn();
        receiving.setExternalNo("ASN-SRM-1");
        receiving.setStatus("RECEIVING");
        receiving.setType("PURCHASE");
        assertNull(SrmReceiptClient.payload(receiving));

        Asn returned = new Asn();
        returned.setExternalNo("RT-1");
        returned.setStatus("RECEIVED");
        returned.setType("RETURN");
        assertNull(SrmReceiptClient.payload(returned));

        Asn local = new Asn();
        local.setStatus("RECEIVED");
        local.setType("PURCHASE");
        assertNull(SrmReceiptClient.payload(local));
    }

    @Test
    void suggestAndRtvPayloadsStayOutOfPurchaseReceipt() {
        Asn suggest = new Asn();
        suggest.setCode("ASN-V1");
        suggest.setType("VMI_SUGGEST");
        suggest.setSupplierCode("SUP01");
        suggest.setWarehouseCode("WH-VMI");
        AsnLine line = new AsnLine();
        line.setItemCode("IT-VMI");
        line.setExpectedQty(new BigDecimal("17"));
        suggest.setLines(Collections.singletonList(line));
        Map<String, Object> suggested = SrmReceiptClient.suggestPayload(suggest);
        assertEquals("SUP01", suggested.get("supplierCode"));
        assertNull(SrmReceiptClient.payload(suggest));

        ShipOrder rtv = new ShipOrder();
        rtv.setCode("SO-R1");
        rtv.setType("RTV");
        rtv.setStatus("SHIPPED");
        rtv.setWarehouseCode("WH-VMI");
        ShipOrderLine shipped = new ShipOrderLine();
        shipped.setItemCode("IT-VMI");
        shipped.setShippedQty(new BigDecimal("2"));
        rtv.setLines(Collections.singletonList(shipped));
        assertEquals("SO-R1", SrmReceiptClient.rtvPayload(rtv).get("wmsOrderCode"));

        ShipOrder sales = new ShipOrder();
        sales.setType("SALES");
        sales.setStatus("SHIPPED");
        assertNull(SrmReceiptClient.rtvPayload(sales));
    }
}
