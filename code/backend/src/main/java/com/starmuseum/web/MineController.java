package com.starmuseum.web;

import com.starmuseum.common.ApiResponse;
import com.starmuseum.domain.Echo;
import com.starmuseum.domain.Exhibit;
import com.starmuseum.domain.Session;
import com.starmuseum.repo.ExhibitRepository;
import com.starmuseum.service.EchoService;
import com.starmuseum.service.ExhibitService;
import com.starmuseum.service.NotificationService;
import com.starmuseum.service.SessionService;
import com.starmuseum.web.dto.Dtos;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 我的声音。
 *
 * 四块东西，别混：
 *   1）uploads  —— 我埋下的声音（任何状态，可编辑、可删除）；
 *   2）kept     —— 我回应过的展品（注意：**不是收藏**）；
 *   3）echoes   —— 我发出去的回音，带审核状态和原因；
 *   4）inbox    —— 我的展品收到的公开回音；
 * 外加 notifications —— 状态变化的回执与未读数量。
 */
@RestController
@RequestMapping("/api/mine")
public class MineController {

    public record MineView(Dtos.SessionView session, List<Dtos.ExhibitCard> kept, int echoCount,
                           List<Dtos.MyUpload> uploads, List<Dtos.MyEcho> echoes,
                           List<Dtos.InboxEcho> inbox, List<Dtos.NotificationView> notifications,
                           long unread) {
    }

    private final SessionService sessions;
    private final EchoService echoService;
    private final ExhibitService exhibitService;
    private final NotificationService notifications;
    private final ExhibitRepository exhibits;

    public MineController(SessionService sessions, EchoService echoService,
                          ExhibitService exhibitService, NotificationService notifications,
                          ExhibitRepository exhibits) {
        this.sessions = sessions;
        this.echoService = echoService;
        this.exhibitService = exhibitService;
        this.notifications = notifications;
        this.exhibits = exhibits;
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

        Dtos.Notifications box = notifications.list(session.getId());
        return ApiResponse.ok(new MineView(
                new Dtos.SessionView(session.getId(), session.getNickname()),
                List.copyOf(unique.values()),
                mine.size(),
                exhibitService.myUploads(session.getId()),
                echoService.mine(session.getId()),
                notifications.inbox(session.getId()),
                box.items(),
                box.unread()));
    }

    /**
     * 编辑页的回填数据。
     * 只给原作者，且允许待审/驳回的内容 —— 公共详情接口不公开未通过的东西。
     */
    @GetMapping("/uploads/{no}")
    public ApiResponse<Dtos.UploadSource> uploadSource(
            @RequestHeader("X-Session-Id") Long sessionId,
            @PathVariable String no) {
        Session keeper = sessions.require(sessionId);
        return ApiResponse.ok(exhibitService.sourceFor(keeper, no));
    }

    /** 我发出的回音（含待审、驳回与原因） */
    @GetMapping("/echoes")
    public ApiResponse<List<Dtos.MyEcho>> myEchoes(@RequestHeader("X-Session-Id") Long sessionId) {
        Session session = sessions.require(sessionId);
        return ApiResponse.ok(echoService.mine(session.getId()));
    }

    /** 收件箱：我的展品收到的公开回音 */
    @GetMapping("/inbox")
    public ApiResponse<List<Dtos.InboxEcho>> inbox(@RequestHeader("X-Session-Id") Long sessionId) {
        Session session = sessions.require(sessionId);
        return ApiResponse.ok(notifications.inbox(session.getId()));
    }

    /** 消息列表 + 未读数。顶栏轮询的是它下面那个更轻的 count */
    @GetMapping("/notifications")
    public ApiResponse<Dtos.Notifications> notifications(
            @RequestHeader("X-Session-Id") Long sessionId) {
        Session session = sessions.require(sessionId);
        return ApiResponse.ok(notifications.list(session.getId()));
    }

    /**
     * 只取未读数：顶栏每 5 秒轮询用。
     * 单独开一个接口，是因为顶栏不该为了一个数字把整个消息列表拉下来。
     */
    @GetMapping("/notifications/count")
    public ApiResponse<Dtos.UnreadCount> unread(@RequestHeader("X-Session-Id") Long sessionId) {
        Session session = sessions.require(sessionId);
        return ApiResponse.ok(new Dtos.UnreadCount(notifications.unread(session.getId())));
    }

    /** 标记本人消息已读。别人的消息标不动 —— 查询条件里带着接收人 */
    @PatchMapping("/notifications/{id}/read")
    public ApiResponse<Dtos.NotificationView> markRead(
            @RequestHeader("X-Session-Id") Long sessionId,
            @PathVariable Long id) {
        Session session = sessions.require(sessionId);
        return ApiResponse.ok(notifications.markRead(session.getId(), id));
    }
}
