package com.wms.integration.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.common.BizException;
import com.wms.outbound.entity.ShipOrder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/** 发运后把送装窗口推给 TMS。默认 mock，不外呼。 */
@Component
public class TmsShipmentClient {
    private final RestTemplate rest = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${wms.tms.mode:mock}")
    private String mode;
    @Value("${wms.tms.base-url:http://localhost:8082}")
    private String baseUrl;
    @Value("${wms.tms.api-key:tms-wms-key}")
    private String apiKey;

    public void push(ShipOrder order) {
        Map<String, Object> body = TmsShipment.payload(order);
        if (body == null || !"http".equalsIgnoreCase(mode)) {
            return;
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Api-Key", apiKey);
        try {
            String response = rest.postForObject(baseUrl + "/api/open/wms/install",
                    new HttpEntity<Object>(body, headers), String.class);
            JsonNode root = objectMapper.readTree(response == null ? "{}" : response);
            if (root.path("code").asInt(-1) != 0) {
                throw new BizException("TMS 拒绝送装预约: " + response);
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("推送 TMS 送装预约失败: " + e.getMessage());
        }
    }
}
