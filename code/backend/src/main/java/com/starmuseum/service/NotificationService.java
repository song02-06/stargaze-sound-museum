package com.starmuseum.service;

import com.starmuseum.common.BizException;
import com.starmuseum.common.TraceCodec;
import com.starmuseum.domain.Echo;
import com.starmuseum.domain.Exhibit;
import com.starmuseum.domain.Notification;
import com.starmuseum.domain.TargetType;
import com.starmuseum.repo.EchoRepository;
import com.starmuseum.repo.ExhibitRepository;
import com.starmuseum.repo.NotificationRepository;
import com.starmuseum.web.dto.Dtos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 消息与收件箱。
 *
 * 两件不同的事，别混在一起：
 *   1）**收件箱** = 我的展品收到的公开回音（内容本身是公开的，这里只是汇总）；
 *   2）**消息** = 状态变化的回执（审核结果、新回音到达），带未读标记。
 *
 * 所有写消息的入口都在业务事务里调用，这样「状态变了但消息没生成」
 * 这种半截状态不会出现。
 */
@Service
public class NotificationService {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final NotificationRepository notifications;
    private final ExhibitRepository exhibits;
    private final EchoRepository echoes;

    public NotificationService(NotificationRepository notifications, ExhibitRepository exhibits,
                               EchoRepository echoes) {
        this.notifications = notifications;
        this.exhibits = exhibits;
        this.echoes = echoes;
    }

    /* ---------------- 写 ---------------- */

    /**
     * 我的展品收到一条**已公开**的回音。
     * 只在这里被调用意味着：待审、被驳回的回音不会冒充「已送达」。
     */
    public void echoReceived(Exhibit exhibit, Echo echo) {
        Long keeper = exhibit.getKeeperId();
        if (keeper == null) {
            return;   // 演示数据没有主人，不产生消息
        }
        if (keeper.equals(echo.getAuthorId())) {
            return;   // 自己给自己的展品回音，不必再通知自己一次
        }
        String what = "voice".equalsIgnoreCase(echo.getKind().name()) ? "语音回音" : "文字回音";
        save(keeper, Notification.Kind.ECHO_RECEIVED, TargetType.EXHIBIT,
                exhibit.getId(), exhibit.getNo(),
                "《" + exhibit.getTitle() + "》收到一条" + what + "，来自 " + echo.getAuthorName());
    }

    public void exhibitReviewResult(Exhibit exhibit, boolean pass, String reason) {
        if (exhibit.getKeeperId() == null) {
            return;
        }
        String summary = pass
                ? "《" + exhibit.getTitle() + "》通过人工复审，已经入馆"
                : "《" + exhibit.getTitle() + "》未通过：" + safeReason(reason);
        save(exhibit.getKeeperId(), Notification.Kind.EXHIBIT_REVIEW_RESULT, TargetType.EXHIBIT,
                exhibit.getId(), exhibit.getNo(), summary);
    }

    public void echoReviewResult(Exhibit exhibit, Echo echo, boolean pass, String reason) {
        if (echo.getAuthorId() == null) {
            return;
        }
        String summary = pass
                ? "你发给《" + exhibit.getTitle() + "》的回音已通过人工复审"
                : "你发给《" + exhibit.getTitle() + "》的回音未通过：" + safeReason(reason);
        save(echo.getAuthorId(), Notification.Kind.ECHO_REVIEW_RESULT, TargetType.EXHIBIT,
                exhibit.getId(), exhibit.getNo(), summary);
    }

    private void save(Long recipient, Notification.Kind kind, TargetType targetType,
                      Long targetId, String targetNo, String summary) {
        notifications.save(new Notification(recipient, kind, targetType, targetId, targetNo,
                truncate(summary, 200)));
    }

    /**
     * 目标对象被删了：指向它的消息全部失效。
     * 不这么做的话，收件箱里会留一个必然点不开的入口。
     */
    @Transactional
    public void invalidateTarget(TargetType targetType, Long targetId) {
        for (Notification n : notifications.findByTargetTypeAndTargetId(targetType, targetId)) {
            n.markTargetGone();
        }
    }

    /* ---------------- 读 ---------------- */

    public Dtos.Notifications list(Long sessionId) {
        List<Dtos.NotificationView> items = notifications
                .findByRecipientSessionIdAndTargetGoneFalseOrderByCreatedAtDesc(sessionId)
                .stream().map(this::toView).toList();
        return new Dtos.Notifications(items, unread(sessionId));
    }

    public long unread(Long sessionId) {
        return notifications.countByRecipientSessionIdAndReadAtIsNullAndTargetGoneFalse(sessionId);
    }

    /** 标记已读。只允许接收人操作 —— 查询条件里带着 sessionId */
    @Transactional
    public Dtos.NotificationView markRead(Long sessionId, Long id) {
        Notification n = notifications.findByIdAndRecipientSessionId(id, sessionId)
                .orElseThrow(() -> new BizException("这不是给你的消息"));
        n.markRead();
        return toView(n);
    }

    /**
     * 收件箱：我名下的、**已经公开的**展品收到的 PASS 回音。
     * 未通过的展品还没有「公开收到的回音」这回事，不该出现在这里。
     */
    public List<Dtos.InboxEcho> inbox(Long sessionId) {
        List<Exhibit> passed = exhibits.findByKeeperIdOrderByCreatedAtDesc(sessionId).stream()
                .filter(e -> e.getStatus() == Exhibit.AuditStatus.PASS)
                .toList();

        List<Dtos.InboxEcho> out = new ArrayList<>();
        for (Exhibit exhibit : passed) {
            for (Echo echo : echoes.findByExhibitIdAndStatusOrderByCreatedAtDesc(
                    exhibit.getId(), Echo.AuditStatus.PASS)) {
                out.add(new Dtos.InboxEcho(
                        echo.getId(), exhibit.getNo(), exhibit.getTitle(), echo.getAuthorName(),
                        echo.getAuthorId(), echo.getKind().name().toLowerCase(), echo.getBody(),
                        echo.getAudioPath() == null ? null : "/audio/" + echo.getAudioPath(),
                        echo.getSeconds(), TraceCodec.decode(echo.getTrace()),
                        echo.getCreatedAt() == null ? null : echo.getCreatedAt().format(ISO)));
            }
        }
        out.sort(Comparator.comparing(Dtos.InboxEcho::createdAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return out;
    }

    private Dtos.NotificationView toView(Notification n) {
        return new Dtos.NotificationView(
                n.getId(), n.getKind().name(),
                n.getTargetType() == null ? null : n.getTargetType().name(),
                n.getTargetId() == null ? null : String.valueOf(n.getTargetId()),
                n.getTargetNo(), n.getSummary(),
                n.getCreatedAt() == null ? null : n.getCreatedAt().format(ISO),
                n.isRead(),
                n.getReadAt() == null ? null : n.getReadAt().format(ISO));
    }

    private static String safeReason(String reason) {
        return reason == null || reason.isBlank() ? "（未填写原因）" : reason.strip();
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }
}
