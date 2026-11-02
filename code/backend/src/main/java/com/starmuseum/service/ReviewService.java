package com.starmuseum.service;

import com.starmuseum.common.BizException;
import com.starmuseum.domain.AuditRecord;
import com.starmuseum.domain.Echo;
import com.starmuseum.domain.Exhibit;
import com.starmuseum.domain.TargetType;
import com.starmuseum.repo.AuditRecordRepository;
import com.starmuseum.repo.EchoRepository;
import com.starmuseum.repo.ExhibitRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 人工复审。
 *
 * 这个服务是**唯一**能改审核状态的地方 —— 控制器不再自己拿仓储改状态。
 * 把决定放进来，是为了让三件事在同一个事务里发生：
 *   1）状态变更；
 *   2）审核流水；
 *   3）给作者/发送人的消息。
 * 三件里少写一件，收件箱和流水就会对不上。
 */
@Service
public class ReviewService {

    /** 驳回必须写原因，且有长度上限：一句人话，不是一篇作文 */
    private static final int MAX_REASON = 100;

    private final ExhibitRepository exhibits;
    private final EchoRepository echoes;
    private final AuditRecordRepository auditRecords;
    private final AuditService audit;
    private final NotificationService notifications;

    public ReviewService(ExhibitRepository exhibits, EchoRepository echoes,
                         AuditRecordRepository auditRecords, AuditService audit,
                         NotificationService notifications) {
        this.exhibits = exhibits;
        this.echoes = echoes;
        this.auditRecords = auditRecords;
        this.audit = audit;
        this.notifications = notifications;
    }

    @Transactional
    public Exhibit reviewExhibit(Long id, boolean pass, String reason) {
        Exhibit e = exhibits.findById(id)
                .orElseThrow(() -> new BizException("没有这条展品"));
        requirePending(e.getStatus() == Exhibit.AuditStatus.REVIEW);

        String note = requireReason(pass, reason);
        if (pass) {
            e.clearAudioReview();
            e.approve();
        } else {
            e.reject(note);
        }
        exhibits.save(e);

        record(TargetType.EXHIBIT, id, pass, note);
        notifications.exhibitReviewResult(e, pass, note);
        return e;
    }

    @Transactional
    public Echo reviewEcho(Long id, boolean pass, String reason) {
        Echo e = echoes.findById(id)
                .orElseThrow(() -> new BizException("没有这条回音"));
        requirePending(e.getStatus() == Echo.AuditStatus.REVIEW);

        String note = requireReason(pass, reason);
        if (pass) {
            e.approve();
        } else {
            e.reject(note);
        }
        echoes.save(e);

        record(TargetType.ECHO, id, pass, note);

        Exhibit exhibit = exhibits.findById(e.getExhibitId()).orElse(null);
        if (exhibit != null) {
            // 发送人总要知道结果
            notifications.echoReviewResult(exhibit, e, pass, note);
            if (pass) {
                // 通过才算真正送达展品作者。转人工、驳回都不通知作者 ——
                // 否则作者会收到一条点进去看不到的回音。
                notifications.echoReceived(exhibit, e);
            }
        }
        return e;
    }

    /**
     * 只有「还在待复审」的记录才能被决定。
     * 管理员页面会轮询，队列里可能是几秒前的快照 ——
     * 别人已经决定过的东西，这里必须挡住，并提示刷新，而不是覆盖之前的结论。
     */
    private static void requirePending(boolean isPending) {
        if (!isPending) {
            throw new BizException("这条已经不在待复审状态了，刷新一下队列再看");
        }
    }

    private static String requireReason(boolean pass, String reason) {
        String clean = reason == null ? "" : reason.strip();
        if (pass) {
            return clean;
        }
        if (clean.isEmpty()) {
            throw new BizException("驳回要写一句原因，作者才知道该怎么改");
        }
        if (clean.length() > MAX_REASON) {
            throw new BizException("原因请控制在 " + MAX_REASON + " 个字以内");
        }
        return clean;
    }

    private void record(TargetType type, Long id, boolean pass, String reason) {
        // 判定理由一定要写清楚，哪怕是通过。
        // 这张表同时是论文里审核对比实验的数据源 ——
        // 事后翻流水时，「谁在什么时候放行了什么」必须一眼看得见。
        String detail = pass
                ? (reason.isBlank() ? "人工复审：通过" : "人工复审：通过 · " + reason)
                : "人工复审：驳回 · " + reason;
        auditRecords.save(new AuditRecord(type, id, AuditRecord.Stage.MANUAL,
                pass ? AuditRecord.Verdict.PASS : AuditRecord.Verdict.REJECT,
                detail, 0L, "admin"));
    }
}
