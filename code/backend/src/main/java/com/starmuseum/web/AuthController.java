package com.starmuseum.web;

import com.starmuseum.common.ApiResponse;
import com.starmuseum.common.BizException;
import com.starmuseum.domain.Session;
import com.starmuseum.service.AccountService;
import com.starmuseum.service.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    public static final String ACCOUNT_ID = "museum.accountId";
    public record Credentials(String username, String password, String deviceKey) {}
    public record AccountView(Long id, String nickname, String username) {}
    private final AccountService accounts;
    private final SessionService sessions;
    public AuthController(AccountService accounts, SessionService sessions) {
        this.accounts = accounts;
        this.sessions = sessions;
    }
    @PostMapping("/register")
    public ApiResponse<AccountView> register(@RequestBody Credentials body, HttpServletRequest request) {
        if (accountId(request) != null) throw new BizException("请先退出当前账号");
        return signIn(accounts.register(body.username(), body.password(), body.deviceKey()), request);
    }
    @PostMapping("/login")
    public ApiResponse<AccountView> login(@RequestBody Credentials body, HttpServletRequest request) {
        return signIn(accounts.login(body.username(), body.password()), request);
    }
    @GetMapping("/me")
    public ApiResponse<AccountView> me(HttpServletRequest request) {
        Long id = accountId(request);
        return ApiResponse.ok(id == null ? null : view(sessions.require(id)));
    }
    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
        return ApiResponse.ok(null);
    }
    public static Long accountId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session == null ? null : (Long) session.getAttribute(ACCOUNT_ID);
    }
    private ApiResponse<AccountView> signIn(Session account, HttpServletRequest request) {
        HttpSession old = request.getSession(false);
        if (old != null) old.invalidate();
        HttpSession session = request.getSession(true);
        session.setMaxInactiveInterval(7 * 24 * 60 * 60);
        session.setAttribute(ACCOUNT_ID, account.getId());
        return ApiResponse.ok(view(account));
    }
    private static AccountView view(Session session) {
        return new AccountView(session.getId(), session.getNickname(), session.getUsername());
    }
}
