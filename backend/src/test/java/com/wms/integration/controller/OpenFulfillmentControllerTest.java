package com.wms.integration.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
public class OpenFulfillmentControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    public void mapsShanghaiWarehouseAndReplaysSameOrder() throws Exception {
        assertEquals("WH01", OpenFulfillmentController.warehouse("WH-SH"));
        assertEquals("WH01", com.wms.integration.client.HandlingFee.doc(
                "OMS-SO-1", "WH01", new java.math.BigDecimal("2")).get("warehouseCode"));
        String body = "{\"orderNo\":\"OMS-SO-1\",\"warehouseCode\":\"WH-SH\",\"carrierCode\":\"SF\","
                + "\"address\":\"上海\",\"items\":[{\"sku\":\"SKU001\",\"qty\":2}]}";
        mockMvc.perform(post("/api/open/outbound")
                        .header("X-Api-Key", "test-open-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.externalNo").value("OMS-SO-1"))
                .andExpect(jsonPath("$.data.warehouseCode").value("WH01"))
                .andExpect(jsonPath("$.data.status").value("NEW"));
        mockMvc.perform(post("/api/open/outbound")
                        .header("X-Api-Key", "test-open-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.externalNo").value("OMS-SO-1"));
    }

    @Test
    public void returnCreatesAsn() throws Exception {
        String body = "{\"returnNo\":\"RT-1\",\"orderNo\":\"OMS-SO-1\",\"warehouseCode\":\"WH01\","
                + "\"items\":[{\"sku\":\"SKU001\",\"qty\":1}]}";
        mockMvc.perform(post("/api/open/return")
                        .header("X-Api-Key", "test-open-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.type").value("RETURN"))
                .andExpect(jsonPath("$.data.externalNo").value("RT-1"));
    }
}
