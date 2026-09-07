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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthApiTest {
    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;

    private String login(String user, String pwd) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + user + "\",\"password\":\"" + pwd + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        JsonNode body = json.readTree(r.getResponse().getContentAsString());
        return body.path("data").path("token").asText();
    }

    @Test
    void anonymousIsRejectedWith401() throws Exception {
        mvc.perform(get("/api/inventory/page"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
        mvc.perform(get("/api/inventory/page").header("Authorization", "Bearer bogus"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongPasswordIsBusinessError() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"nope\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void adminLoginAndPasswordNeverSerialized() throws Exception {
        String token = login("admin", "admin123");
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("admin"))
                .andExpect(jsonPath("$.data.role").value("ADMIN"))
                .andExpect(jsonPath("$.data.password").doesNotExist());
        mvc.perform(get("/api/system/user/page").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].password").doesNotExist());
    }

    @Test
    void viewerIsReadOnlyAndOperatorCannotTouchMasterData() throws Exception {
        String admin = login("admin", "admin123");
        mvc.perform(post("/api/system/user").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"v1\",\"password\":\"viewer123\",\"role\":\"VIEWER\"}"))
                .andExpect(jsonPath("$.code").value(0));
        mvc.perform(post("/api/system/user").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"op1\",\"password\":\"op123456\",\"role\":\"OPERATOR\"}"))
                .andExpect(jsonPath("$.code").value(0));
        mvc.perform(post("/api/system/user").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"weak\",\"password\":\"123\",\"role\":\"VIEWER\"}"))
                .andExpect(jsonPath("$.code").value(400));

        String viewer = login("v1", "viewer123");
        mvc.perform(get("/api/basic/warehouse/list").header("Authorization", "Bearer " + viewer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        mvc.perform(post("/api/inbound/asn").header("Authorization", "Bearer " + viewer)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));

        String op = login("op1", "op123456");
        mvc.perform(put("/api/basic/warehouse/1").header("Authorization", "Bearer " + op)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"WH01\",\"name\":\"x\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/system/user").header("Authorization", "Bearer " + op)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/password").header("Authorization", "Bearer " + op)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"op123456\",\"newPassword\":\"op654321\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        login("op1", "op654321");
    }

    @Test
    void lastAdminCannotBeDemotedOrDeleted() throws Exception {
        String admin = login("admin", "admin123");
        MvcResult me = mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + admin)).andReturn();
        long id = json.readTree(me.getResponse().getContentAsString()).path("data").path("id").asLong();
        mvc.perform(put("/api/system/user/" + id).header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"role\":\"VIEWER\"}"))
                .andExpect(jsonPath("$.code").value(400));
        mvc.perform(delete("/api/system/user/" + id).header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.code").value(400));
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.data.role").value("ADMIN"));
    }
}
