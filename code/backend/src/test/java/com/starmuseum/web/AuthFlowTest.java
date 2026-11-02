package com.starmuseum.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.starmuseum.repo.SessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.http.MediaType;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthFlowTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired SessionRepository sessions;

    @Test void registerLoginRestoreLogoutAndProtectAccount() throws Exception {
        String key = UUID.randomUUID().toString();
        String username = "u_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String guest = mvc.perform(post("/api/session").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("deviceKey", key))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long guestId = json.readTree(guest).path("data").path("id").asLong();
        var registration = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", username.toUpperCase(), "password", "star-pass-2026", "deviceKey", key))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(guestId))
                .andExpect(jsonPath("$.data.username").value(username)).andReturn();
        MockHttpSession cookie = (MockHttpSession) registration.getRequest().getSession(false);
        assertThat(cookie).isNotNull();
        var account = sessions.findById(guestId).orElseThrow();
        assertThat(account.getPasswordHash()).doesNotContain("star-pass-2026");
        assertThat(account.getDeviceKey()).isNotEqualTo(key);
        mvc.perform(get("/api/auth/me").session(cookie)).andExpect(jsonPath("$.data.id").value(guestId));
        // Cookie 身份优先于任意前端 ID。
        mvc.perform(get("/api/mine").session(cookie).header("X-Session-Id", "999999"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.session.id").value(guestId));
        mvc.perform(post("/api/auth/logout").session(cookie)).andExpect(status().isOk());
        mvc.perform(get("/api/mine").header("X-Session-Id", guestId)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me")).andExpect(status().isOk()).andExpect(jsonPath("$.data").doesNotExist());
        // 旧游客设备不能取回注册后的账号。
        mvc.perform(post("/api/session").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("deviceKey", key))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(org.hamcrest.Matchers.not((int) guestId)));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", username, "password", "wrong-password"))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.msg").value("用户名或密码不正确"));
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", username, "password", "star-pass-2026", "deviceKey", key))))
                .andExpect(status().isBadRequest());
        var login = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", username, "password", "star-pass-2026"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(guestId)).andReturn();
        MockHttpSession loggedIn = (MockHttpSession) login.getRequest().getSession(false);
        mvc.perform(get("/api/auth/me").session(loggedIn)).andExpect(jsonPath("$.data.username").value(username));
        mvc.perform(get("/api/exhibits")).andExpect(status().isOk());
    }

    @Test void guestsCanBrowseButCannotWriteOrOpenPersonalData() throws Exception {
        mvc.perform(get("/api/exhibits")).andExpect(status().isOk());
        mvc.perform(get("/api/mine")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/exhibits")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/exhibits/0249/echoes")).andExpect(status().isUnauthorized());
    }

    @Test void invalidRegistrationDoesNotCreateAccount() throws Exception {
        for (var values : new String[][] {{"ab", "12345678"}, {"valid_name", "short"}}) {
            mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(Map.of("username", values[0], "password", values[1], "deviceKey", UUID.randomUUID().toString()))))
                    .andExpect(status().isBadRequest());
        }
        assertThat(sessions.findByUsername("valid_name")).isEmpty();
    }
}
