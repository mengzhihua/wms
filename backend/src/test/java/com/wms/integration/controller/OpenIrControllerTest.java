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
        boolean sku001 = false;
        for (JsonNode row : data.get("inventory")) {
            if ("SKU001".equals(row.path("sku").asText())
                    || "SKU001".equals(row.path("itemCode").asText())) {
                sku001 = true;
                break;
            }
        }
        assertTrue(sku001, "库存快照应含 SKU001");

        String status = stuck.path("status").asText();
        if ("NEW".equals(status) || "PART_ALLOCATED".equals(status)) {
            mockMvc.perform(post("/api/open/ir/actions")
                            .header("X-Api-Key", "test-open-key")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"type\":\"WMS_ALLOCATE\",\"targetKey\":\"SO-IR-STUCK\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.status").value("ALLOCATED"));
        }
    }
}
