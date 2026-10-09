package com.starmuseum.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.starmuseum.common.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 管理口的口令校验。
 *
 * 之前 /api/admin/** 是完全敞开的 —— 谁都能替你做复审决定。
 * 这里补上，并且采用「安全默认」：
 * **没有配 ADMIN_TOKEN 就等于关闭管理口**，而不是等于不设防。
 * 一个忘了配令牌的人，代价应该是「进不去后台」，而不是「后台裸奔」。
 *
 * 口令比较用 MessageDigest.isEqual（定长比较），避免用 == 泄露前缀信息。
 */
@Component
public class AdminAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AdminAuthFilter.class);
    private static final String HEADER = "X-Admin-Token";

    private final String expectedToken;
    private final ObjectMapper mapper = new ObjectMapper();

    public AdminAuthFilter(@Value("${museum.admin.token:}") String token) {
        this.expectedToken = token == null ? "" : token.strip();
        if (this.expectedToken.isBlank()) {
            log.warn("没有配 museum.admin.token —— 管理后台已关闭。"
                    + "要用就设环境变量 ADMIN_TOKEN，或在 application-local.yml 里配上。");
        } else if (this.expectedToken.length() < 8) {
            log.warn("管理口令太短了（{} 位），建议至少 12 位随机字符", this.expectedToken.length());
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/admin");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (expectedToken.isBlank()) {
            reject(response, 503, "管理后台没有启用：请先设置 ADMIN_TOKEN 再重启服务");
            return;
        }

        String provided = request.getHeader(HEADER);
        if (provided == null
                || !MessageDigest.isEqual(expectedToken.getBytes(StandardCharsets.UTF_8),
                                          provided.strip().getBytes(StandardCharsets.UTF_8))) {
            reject(response, 401, "管理口令不对");
            return;
        }

        chain.doFilter(request, response);
    }

    /** 用和业务一致的返回体，前端那层剥信封的逻辑不用为它写特例 */
    private void reject(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(
                mapper.writeValueAsString(ApiResponse.fail(status, message)));
    }
}
