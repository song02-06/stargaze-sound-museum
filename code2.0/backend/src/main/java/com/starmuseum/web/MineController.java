package com.starmuseum.web;

import com.starmuseum.common.ApiResponse;
import com.starmuseum.domain.Echo;
import com.starmuseum.domain.Exhibit;
import com.starmuseum.domain.Session;
import com.starmuseum.repo.ExhibitRepository;
import com.starmuseum.repo.EchoRepository;
import com.starmuseum.service.EchoService;
import com.starmuseum.service.ExhibitService;
import com.starmuseum.service.SessionService;
import com.starmuseum.web.dto.Dtos;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// 我的声音：我回应过的展品 + 我埋下的声音。
//
// 这两半缺一不可：只列「回应过的」，一个人埋下的那一段转人工复审之后
// 就再也找不到了 —— 投稿的人有权知道自己那一段现在在哪。
@RestController
@RequestMapping("/api/mine")
public class MineController {

    public record MineView(Dtos.SessionView session, List<Dtos.ExhibitCard> kept, int echoCount,
                           List<Dtos.MyUpload> uploads) {
    }

    private final SessionService sessions;
    private final EchoService echoService;
    private final ExhibitService exhibitService;
    private final ExhibitRepository exhibits;
    private final EchoRepository echoes;

    public MineController(SessionService sessions, EchoService echoService,
                          ExhibitService exhibitService, ExhibitRepository exhibits,
                          EchoRepository echoes) {
        this.sessions = sessions;
        this.echoService = echoService;
        this.exhibitService = exhibitService;
        this.exhibits = exhibits;
        this.echoes = echoes;
    }

    @GetMapping
    public ApiResponse<MineView> mine(@RequestHeader("X-Session-Id") Long sessionId) {
        Session session = sessions.require(sessionId);
        List<Echo> mine = echoService.byAuthor(session.getId());

        Map<Long, Dtos.ExhibitCard> unique = new LinkedHashMap<>();
        for (Echo echo : mine) {
            if (unique.containsKey(echo.getExhibitId())) {
                continue;
            }
            exhibits.findById(echo.getExhibitId())
                    .filter(e -> e.getStatus() == Exhibit.AuditStatus.PASS)
                    .ifPresent(e -> unique.put(e.getId(), exhibitService.toCard(e)));
        }

        return ApiResponse.ok(new MineView(
                new Dtos.SessionView(session.getId(), session.getNickname()),
                List.copyOf(unique.values()),
                mine.size(),
                uploadsOf(session.getId())));
    }

    /** 我埋下的声音，任何状态都列出来 —— 包括没通过的那几段 */
    private List<Dtos.MyUpload> uploadsOf(Long sessionId) {
        return exhibits.findByKeeperIdOrderByCreatedAtDesc(sessionId).stream()
                .map(e -> new Dtos.MyUpload(
                        e.getNo(), e.getTitle(), e.getStatus().name(), e.getAuditReason(),
                        e.getRecordedOn().toString(), e.getSeconds(),
                        "/audio/" + e.getAudioPath(),
                        e.getCreatedAt() == null ? null : e.getCreatedAt().toString(),
                        // 删之前要说清楚后果：这件下面挂着几条别人的回音
                        echoes.countByExhibitIdAndStatus(e.getId(), Echo.AuditStatus.PASS)))
                .toList();
    }
}
