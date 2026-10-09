package com.starmuseum.web;

import com.starmuseum.common.ApiResponse;
import com.starmuseum.domain.Session;
import com.starmuseum.service.SessionService;
import com.starmuseum.web.dto.Dtos;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/session")
public class SessionController {

    public record CreateRequest(@NotBlank String deviceKey, String nickname) {
    }

    public record RenameRequest(@NotBlank String nickname) {
    }

    private final SessionService sessions;

    public SessionController(SessionService sessions) {
        this.sessions = sessions;
    }

    /**
     * 客户端生成 deviceKey 并长期保管，服务端据此认人。
     * 没有注册、没有密码 —— 这是产品定义里的承诺，不是省事。
     */
    @PostMapping
    public ApiResponse<Dtos.SessionView> create(@RequestBody CreateRequest request) {
        Session session = sessions.resolve(request.deviceKey(), request.nickname());
        return ApiResponse.ok(new Dtos.SessionView(session.getId(), session.getNickname()));
    }

    @PatchMapping("/nickname")
    public ApiResponse<Dtos.SessionView> rename(@RequestHeader("X-Session-Id") Long sessionId,
                                                @RequestBody RenameRequest request) {
        Session session = sessions.rename(sessionId, request.nickname());
        return ApiResponse.ok(new Dtos.SessionView(session.getId(), session.getNickname()));
    }
}
