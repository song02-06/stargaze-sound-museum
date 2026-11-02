package com.starmuseum.web;

import com.starmuseum.common.ApiResponse;
import com.starmuseum.domain.Session;
import com.starmuseum.service.DrawService;
import com.starmuseum.service.SessionService;
import com.starmuseum.web.dto.Dtos;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/draw")
public class DrawController {

    private final DrawService draw;
    private final SessionService sessions;

    public DrawController(DrawService draw, SessionService sessions) {
        this.draw = draw;
        this.sessions = sessions;
    }

    // 打捞一段。
    // 彩蛋保底和「本轮不重复」都在服务端 —— 这两条依赖历史记录，
    // 放在客户端等于把规则交给了请求方。
    @GetMapping
    public ApiResponse<Dtos.DrawResult> draw(@RequestHeader("X-Session-Id") Long sessionId) {
        Session session = sessions.require(sessionId);
        return ApiResponse.ok(draw.draw(session));
    }
}
