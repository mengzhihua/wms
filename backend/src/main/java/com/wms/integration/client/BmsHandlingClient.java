package com.wms.integration.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.common.BizException;
import com.wms.outbound.entity.ShipOrder;
import com.wms.outbound.entity.ShipOrderLine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Map;

/** 出库单创建后把操作费推到 BMS。默认 mock。 */
@Component
public class BmsHandlingClient {
    private final RestTemplate rest = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${wms.bms.mode:mock}")
    private String mode;
    @Value("${wms.bms.base-url:http://localhost:8084}")
    private String baseUrl;
    @Value("${wms.bms.api-key:bms-open-key}")
    private String apiKey;

    public void push(ShipOrder order) {
        if (order == null || !"http".equalsIgnoreCase(mode)) {
            return;
        }
        BigDecimal qty = BigDecimal.ZERO;
        if (order.getLines() != null) {
            for (ShipOrderLine line : order.getLines()) {
                if (line.getOrderQty() != null) {
                    qty = qty.add(line.getOrderQty());
                }
            }
        }
        Map<String, Object> doc = HandlingFee.doc(order.getExternalNo(), order.getWarehouseCode(), qty);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Api-Key", apiKey);
        try {
            String body = rest.postForObject(baseUrl + "/api/open/wms/docs",
                    new HttpEntity<Object>(Collections.singletonList(doc), headers), String.class);
            JsonNode root = objectMapper.readTree(body == null ? "{}" : body);
            if (root.path("code").asInt(-1) != 0) {
                throw new BizException("BMS 拒绝操作费: " + body);
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("推送 BMS 操作费失败: " + e.getMessage());
        }
    }
}
