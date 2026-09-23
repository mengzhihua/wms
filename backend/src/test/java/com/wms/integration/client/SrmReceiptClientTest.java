package com.wms.integration.client;

import com.wms.inbound.entity.Asn;
import com.wms.inbound.entity.AsnLine;
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
}
