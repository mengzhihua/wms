package com.wms.integration.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
public class OpenIrControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    public void snapshotsIncludeStuckOutboundThenAllocate() throws Exception {
        String snapshots = mockMvc.perform(get("/api/open/ir/snapshots")
                        .header("X-Api-Key", "test-open-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.system").value("WMS"))
                .andReturn().getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(snapshots).get("data");
        JsonNode stuck = null;
        for (JsonNode row : data.get("outbound")) {
            if ("SO-IR-STUCK".equals(row.path("code").asText())
                    || "IR-SO-STUCK".equals(row.path("externalNo").asText())) {
                stuck = row;
                break;
            }
        }
        assertNotNull(stuck, "应包含 IR 出库卡单");
        assertTrue("SKU001".equals(stuck.path("sku").asText())
                || "SKU001".equals(stuck.path("itemCode").asText()), "出库快照应带行 SKU");
        boolean sku001 = false;
        boolean safety = false;
        for (JsonNode row : data.get("inventory")) {
            if ("SKU001".equals(row.path("sku").asText())
                    || "SKU001".equals(row.path("itemCode").asText())) {
                sku001 = true;
                if (row.path("safetyQty").asDouble() > 0) {
                    safety = true;
                }
            }
        }
        assertTrue(sku001, "库存快照应含 SKU001");
        assertTrue(safety, "库存快照 safetyQty 应取物料 min_stock");

        String status = stuck.path("status").asText();
        if ("NEW".equals(status) || "PART_ALLOCATED".equals(status)) {
            String alloc = "{\"type\":\"WMS_ALLOCATE\",\"targetKey\":\"SO-IR-STUCK\","
                    + "\"idempotencyKey\":\"WMS-ALLOC-1\"}";
            mockMvc.perform(post("/api/open/ir/actions")
                            .header("X-Api-Key", "test-open-key")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(alloc))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.status").value("ALLOCATED"));
            mockMvc.perform(post("/api/open/ir/actions")
                            .header("X-Api-Key", "test-open-key")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(alloc))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.status").value("ALLOCATED"));
            mockMvc.perform(post("/api/open/ir/allocate")
                            .header("X-Api-Key", "test-open-key")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"orderCode\":\"SO-IR-STUCK\",\"idempotencyKey\":\"WMS-ALLOC-1\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.status").value("ALLOCATED"));
        }

        mockMvc.perform(post("/api/open/ir/actions")
                        .header("X-Api-Key", "test-open-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"WMS_REPLENISH\",\"targetKey\":\"WH01\","
                                + "\"params\":{\"warehouseCode\":\"WH01\",\"sku\":\"SKU001\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(post("/api/open/ir/actions")
                        .header("X-Api-Key", "test-open-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"WMS_REPLENISH\",\"targetKey\":\"WH-BJ\","
                                + "\"params\":{\"warehouseCode\":\"WH-BJ\",\"fromWarehouseCode\":\"WH-SH\","
                                + "\"sku\":\"SKU001\",\"qty\":5}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].status").value("DONE"))
                .andExpect(jsonPath("$.data[0].itemCode").value("SKU001"));
    }
}
