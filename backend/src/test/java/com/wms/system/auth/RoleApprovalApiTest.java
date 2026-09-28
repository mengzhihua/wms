package com.wms.system.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 自定义角色权限码鉴权 + 仓库开启审核后入库单/出库单审核门禁。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RoleApprovalApiTest {
    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;

    private JsonNode call(String token, String method, String path, String body) throws Exception {
        MvcResult r = mvc.perform(request(org.springframework.http.HttpMethod.valueOf(method), path)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body == null ? "" : body))
                .andReturn();
        return json.readTree(r.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }

    private String login(String user, String pwd) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + user + "\",\"password\":\"" + pwd + "\"}"))
                .andExpect(status().isOk()).andReturn();
        JsonNode body = json.readTree(r.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        assertEquals(0, body.path("code").asInt(), body.toString());
        return body.path("data").path("token").asText();
    }

    @Test
    void loginReturnsPermsAndBuiltinRolesAreSeeded() throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
                .andExpect(jsonPath("$.data.perms[0]").value("*")).andReturn();
        String admin = json.readTree(r.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).path("data").path("token").asText();
        JsonNode roles = call(admin, "GET", "/api/system/role/list", null).path("data");
        assertTrue(roles.size() >= 3);
        JsonNode perms = call(admin, "GET", "/api/system/role/perms", null).path("data");
        assertTrue(perms.has(Permission.INBOUND_APPROVE));
    }

    @Test
    void customRoleIsEnforcedAndBuiltinProtected() throws Exception {
        String admin = login("admin", "admin123");
        JsonNode created = call(admin, "POST", "/api/system/role",
                "{\"code\":\"approver\",\"name\":\"审核员\",\"perms\":\"inbound:approve,outbound:approve\"}");
        assertEquals(0, created.path("code").asInt(), created.toString());
        long roleId = created.path("data").path("id").asLong();
        assertEquals("APPROVER", created.path("data").path("code").asText());

        assertEquals(400, call(admin, "POST", "/api/system/role",
                "{\"code\":\"bad\",\"name\":\"x\",\"perms\":\"nope:write\"}").path("code").asInt());
        assertEquals(400, call(admin, "POST", "/api/system/user",
                "{\"username\":\"u0\",\"password\":\"pass1234\",\"role\":\"NOPE\"}").path("code").asInt());

        assertEquals(0, call(admin, "POST", "/api/system/user",
                "{\"username\":\"appr\",\"password\":\"appr1234\",\"role\":\"APPROVER\"}").path("code").asInt());
        String appr = login("appr", "appr1234");
        mvc.perform(post("/api/inbound/asn").header("Authorization", "Bearer " + appr)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/inbound/asn/999999/approve").header("Authorization", "Bearer " + appr)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(400));

        // 角色仍被使用, 不可删除; 内置角色不可删除
        assertEquals(400, call(admin, "DELETE", "/api/system/role/" + roleId, null).path("code").asInt());
        JsonNode roles = call(admin, "GET", "/api/system/role/list", null).path("data");
        long adminRoleId = -1;
        for (JsonNode n : roles) {
            if ("ADMIN".equals(n.path("code").asText())) {
                adminRoleId = n.path("id").asLong();
            }
        }
        assertEquals(400, call(admin, "DELETE", "/api/system/role/" + adminRoleId, null).path("code").asInt());
        // ADMIN 权限固定为 *
        JsonNode upd = call(admin, "PUT", "/api/system/role/" + adminRoleId,
                "{\"code\":\"ADMIN\",\"name\":\"管理员\",\"perms\":\"basic:write\"}");
        assertEquals("*", upd.path("data").path("perms").asText());

        // 修改自定义角色权限后立即生效(缓存失效): 追加 inbound:write 后可建单
        call(admin, "PUT", "/api/system/role/" + roleId,
                "{\"code\":\"APPROVER\",\"name\":\"审核员\",\"perms\":\"inbound:approve,inbound:write\"}");
        mvc.perform(post("/api/inbound/asn").header("Authorization", "Bearer " + appr)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    void approvalGateOnAsnAndShipOrder() throws Exception {
        String admin = login("admin", "admin123");
        JsonNode whs = call(admin, "GET", "/api/basic/warehouse/list", null).path("data");
        JsonNode wh = null;
        for (JsonNode n : whs) {
            if ("WH01".equals(n.path("code").asText())) {
                wh = n;
            }
        }
        assertNotNull(wh);
        long whId = wh.path("id").asLong();
        JsonNode item = call(admin, "POST", "/api/basic/item",
                "{\"ownerCode\":\"OWN01\",\"code\":\"SKU-APPR\",\"name\":\"审核测试物料\",\"unit\":\"EA\","
                        + "\"lotControl\":false,\"qcRequired\":false,\"snControl\":false,\"status\":1}");
        assertEquals(0, item.path("code").asInt(), item.toString());
        String whJson = "{\"code\":\"WH01\",\"name\":\"" + wh.path("name").asText() + "\",\"status\":1,\"approvalRequired\":%s}";
        assertEquals(0, call(admin, "PUT", "/api/basic/warehouse/" + whId, String.format(whJson, "true")).path("code").asInt());
        try {
            JsonNode asn = call(admin, "POST", "/api/inbound/asn",
                    "{\"warehouseCode\":\"WH01\",\"ownerCode\":\"OWN01\",\"supplierCode\":\"SUP01\",\"type\":\"PURCHASE\","
                            + "\"lines\":[{\"itemCode\":\"SKU-APPR\",\"expectedQty\":5}]}");
            assertEquals(0, asn.path("code").asInt(), asn.toString());
            long asnId = asn.path("data").path("id").asLong();
            long lineId = asn.path("data").path("lines").get(0).path("id").asLong();
            assertEquals("PENDING", asn.path("data").path("approvalStatus").asText());

            String receipt = "[{\"lineId\":" + lineId + ",\"qty\":5,\"lotNo\":\"L1\"}]";
            JsonNode blocked = call(admin, "POST", "/api/inbound/asn/" + asnId + "/receive", receipt);
            assertEquals(400, blocked.path("code").asInt());
            assertTrue(blocked.path("msg").asText().contains("尚未审核"), blocked.toString());

            JsonNode rejected = call(admin, "POST", "/api/inbound/asn/" + asnId + "/reject", "{\"remark\":\"数量有误\"}");
            assertEquals("REJECTED", rejected.path("data").path("approvalStatus").asText());
            assertEquals("admin", rejected.path("data").path("approvedBy").asText());
            assertEquals(400, call(admin, "POST", "/api/inbound/asn/" + asnId + "/approve", "{}").path("code").asInt());

            // 修改后重新进入待审核, 审核通过后可收货
            JsonNode resub = call(admin, "PUT", "/api/inbound/asn/" + asnId,
                    "{\"warehouseCode\":\"WH01\",\"ownerCode\":\"OWN01\",\"supplierCode\":\"SUP01\",\"type\":\"PURCHASE\","
                            + "\"lines\":[{\"itemCode\":\"SKU-APPR\",\"expectedQty\":5}]}");
            assertEquals("PENDING", resub.path("data").path("approvalStatus").asText());
            lineId = resub.path("data").path("lines").get(0).path("id").asLong();
            JsonNode approved = call(admin, "POST", "/api/inbound/asn/" + asnId + "/approve", "{\"remark\":\"ok\"}");
            assertEquals("APPROVED", approved.path("data").path("approvalStatus").asText());
            JsonNode received = call(admin, "POST", "/api/inbound/asn/" + asnId + "/receive",
                    "[{\"lineId\":" + lineId + ",\"qty\":5,\"lotNo\":\"L1\"}]");
            assertEquals(0, received.path("code").asInt(), received.toString());

            JsonNode so = call(admin, "POST", "/api/outbound/order",
                    "{\"warehouseCode\":\"WH01\",\"ownerCode\":\"OWN01\",\"customerCode\":\"CUS01\",\"type\":\"SALES\","
                            + "\"lines\":[{\"itemCode\":\"SKU-APPR\",\"orderQty\":1}]}");
            assertEquals(0, so.path("code").asInt(), so.toString());
            long soId = so.path("data").path("id").asLong();
            assertEquals("PENDING", so.path("data").path("approvalStatus").asText());
            JsonNode soBlocked = call(admin, "POST", "/api/outbound/order/" + soId + "/allocate", null);
            assertEquals(400, soBlocked.path("code").asInt());
            assertTrue(soBlocked.path("msg").asText().contains("尚未审核"));
            assertEquals("APPROVED", call(admin, "POST", "/api/outbound/order/" + soId + "/approve", "{}")
                    .path("data").path("approvalStatus").asText());
            JsonNode alloc = call(admin, "POST", "/api/outbound/order/" + soId + "/allocate", null);
            assertFalse(alloc.path("msg").asText().contains("尚未审核"), alloc.toString());
        } finally {
            call(admin, "PUT", "/api/basic/warehouse/" + whId, String.format(whJson, "false"));
        }
        // 关闭审核后新单无需审核
        JsonNode asn2 = call(admin, "POST", "/api/inbound/asn",
                "{\"warehouseCode\":\"WH01\",\"ownerCode\":\"OWN01\",\"supplierCode\":\"SUP01\",\"type\":\"PURCHASE\","
                        + "\"lines\":[{\"itemCode\":\"SKU-APPR\",\"expectedQty\":1}]}");
        assertEquals("NONE", asn2.path("data").path("approvalStatus").asText());
    }
}
