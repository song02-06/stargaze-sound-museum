package com.starmuseum.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 管理口的门禁规则。
 *
 * 最要紧的一条是「安全默认」：
 * **没配口令 = 关闭管理口**，而不是 = 不设防。
 * 一个忘了配令牌的人，代价应该是「进不去后台」，而不是「后台敞着门」。
 */
class AdminAuthFilterTest {

    private static MockHttpServletRequest adminRequest(String token) {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/admin/queue");
        req.setRequestURI("/api/admin/queue");
        if (token != null) {
            req.addHeader("X-Admin-Token", token);
        }
        return req;
    }

    private static MockHttpServletResponse run(AdminAuthFilter filter, MockHttpServletRequest req)
            throws Exception {
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, new MockFilterChain());
        return res;
    }

    @Test
    @DisplayName("没配口令 → 关闭管理口（503），而不是放行")
    void blankTokenDisablesAdminInsteadOfOpeningIt() throws Exception {
        AdminAuthFilter filter = new AdminAuthFilter("");
        MockHttpServletResponse res = run(filter, adminRequest(""));

        assertThat(res.getStatus()).isEqualTo(503);
        assertThat(res.getContentAsString()).contains("没有启用");
    }

    @Test
    @DisplayName("口令不对 → 401")
    void wrongTokenRejected() throws Exception {
        AdminAuthFilter filter = new AdminAuthFilter("correct-horse-battery");

        assertThat(run(filter, adminRequest(null)).getStatus()).isEqualTo(401);
        assertThat(run(filter, adminRequest("")).getStatus()).isEqualTo(401);
        assertThat(run(filter, adminRequest("correct-horse-batter")).getStatus()).isEqualTo(401);
        assertThat(run(filter, adminRequest("correct-horse-battery ")).getStatus())
                .as("前后空格应该被容忍，但不该因此放宽比较")
                .isEqualTo(200);
    }

    @Test
    @DisplayName("口令正确 → 放行")
    void correctTokenPasses() throws Exception {
        AdminAuthFilter filter = new AdminAuthFilter("correct-horse-battery");
        assertThat(run(filter, adminRequest("correct-horse-battery")).getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("不碰业务接口：/api/exhibits 不走这道门")
    void onlyGuardsAdminPaths() {
        AdminAuthFilter filter = new AdminAuthFilter("");
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/exhibits");
        req.setRequestURI("/api/exhibits");
        assertThat(filter.shouldNotFilter(req)).isTrue();

        MockHttpServletRequest admin = new MockHttpServletRequest("GET", "/api/admin/queue");
        admin.setRequestURI("/api/admin/queue");
        assertThat(filter.shouldNotFilter(admin)).isFalse();
    }
}
