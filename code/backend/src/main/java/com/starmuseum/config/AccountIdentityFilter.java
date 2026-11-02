package com.starmuseum.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.starmuseum.common.ApiResponse;
import com.starmuseum.repo.SessionRepository;
import com.starmuseum.web.AuthController;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

/** 登录身份由服务端 Cookie 会话确定，不能通过前端提交账号 ID 冒用。 */
@Component
public class AccountIdentityFilter extends OncePerRequestFilter {
    private final SessionRepository sessions;
    private final ObjectMapper mapper = new ObjectMapper();
    public AccountIdentityFilter(SessionRepository sessions) { this.sessions = sessions; }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/") || request.getRequestURI().startsWith("/api/auth/");
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                            FilterChain chain) throws IOException, ServletException {
        Long accountId = AuthController.accountId(request);
        if (accountId != null) {
            chain.doFilter(new HttpServletRequestWrapper(request) {
                @Override public java.util.Enumeration<String> getHeaders(String name) {
                    return "X-Session-Id".equalsIgnoreCase(name)
                            ? java.util.Collections.enumeration(java.util.List.of(accountId.toString()))
                            : super.getHeaders(name);
                }
                @Override public java.util.Enumeration<String> getHeaderNames() {
                    java.util.List<String> names = java.util.Collections.list(super.getHeaderNames());
                    if (names.stream().noneMatch(n -> "X-Session-Id".equalsIgnoreCase(n))) names.add("X-Session-Id");
                    return java.util.Collections.enumeration(names);
                }
                @Override public String getHeader(String name) {
                    return "X-Session-Id".equalsIgnoreCase(name) ? accountId.toString() : super.getHeader(name);
                }
            }, response);
            return;
        }
        String path = request.getRequestURI();
        boolean personal = path.startsWith("/api/mine") || path.equals("/api/session/nickname");
        boolean writing = !request.getMethod().equals("GET") && path.startsWith("/api/exhibits");
        if (personal || writing) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            mapper.writeValue(response.getWriter(), ApiResponse.fail(401, "请登录后继续"));
            return;
        }
        String claimed = request.getHeader("X-Session-Id");
        if (claimed != null && claimed.matches("[0-9]{1,18}")) {
            boolean account = sessions.findById(Long.valueOf(claimed))
                    .map(s -> s.getUsername() != null).orElse(false);
            if (account) {
                response.setStatus(401);
                response.setContentType("application/json;charset=UTF-8");
                mapper.writeValue(response.getWriter(), ApiResponse.fail(401, "登录已过期，请重新登录"));
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
