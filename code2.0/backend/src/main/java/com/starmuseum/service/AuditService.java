package com.starmuseum.service;

import com.starmuseum.audit.SensitiveLexicon;
import com.starmuseum.domain.AuditRecord;
import com.starmuseum.domain.TargetType;
import com.starmuseum.repo.AuditRecordRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 审核三路：规则 → AI → 人工复审。
 *
 * 一个刻意的省钱设计：**规则路命中时根本不调用 AI**。
 * 违规内容在最快、最便宜的一路上就被拦下，不需要为它付模型调用费。
 * 这一条也是答辩时能讲的设计点。
 */
@Service
public class AuditService {

    public enum Verdict { PASS, REJECT, REVIEW }

    public record Outcome(Verdict verdict, String reason, List<String> hits) {
    }

    private final SensitiveLexicon lexicon;
    private final AuditRecordRepository records;

    public AuditService(SensitiveLexicon lexicon, AuditRecordRepository records) {
        this.lexicon = lexicon;
        this.records = records;
    }

    public Outcome auditText(TargetType type, Long targetId, String text) {
        String content = text == null ? "" : text;

        // ---- 第一路：规则（本地 DFA，微秒级） ----
        long t0 = System.nanoTime();
        List<String> denied = lexicon.findDeny(content);
        record(type, targetId, AuditRecord.Stage.RULE,
                denied.isEmpty() ? AuditRecord.Verdict.PASS : AuditRecord.Verdict.REJECT,
                denied.isEmpty() ? null : "命中拦截词：" + String.join("、", denied), t0, "system");

        if (!denied.isEmpty()) {
            return new Outcome(Verdict.REJECT, "这段内容里有不适合公开的词：" + String.join("、", denied), denied);
        }

        // ---- 第二路之前：隐私类词转人工，而不是一票否决 ----
        List<String> privateHits = lexicon.findReviewOnly(content);
        if (!privateHits.isEmpty()) {
            record(type, targetId, AuditRecord.Stage.RULE, AuditRecord.Verdict.REVIEW,
                    "命中隐私类词，转人工：" + String.join("、", privateHits), t0, "system");
            return new Outcome(Verdict.REVIEW, "内容里可能包含他人隐私，需要人工看一眼。", privateHits);
        }

        // ---- 第二路：AI 判定 ----
        // 默认 mock：不联网、不花钱、断网可用。
        // 接了真模型之后这里换成真实调用，返回结构不变。
        long t1 = System.nanoTime();
        record(type, targetId, AuditRecord.Stage.AI, AuditRecord.Verdict.PASS,
                "mock 判定：safe", t1, "ai:mock");

        return new Outcome(Verdict.PASS, null, List.of());
    }

    public void record(TargetType type, Long targetId, AuditRecord.Stage stage,
                       AuditRecord.Verdict verdict, String detail, long startedNanos, String operator) {
        long costMs = (System.nanoTime() - startedNanos) / 1_000_000;
        records.save(new AuditRecord(type, targetId, stage, verdict, detail, costMs, operator));
    }

    /** 已知耗时（比如 ASR 客户端自己报的）时用它 */
    public void recordWithCost(TargetType type, Long targetId, AuditRecord.Stage stage,
                               AuditRecord.Verdict verdict, String detail, long costMs,
                               String operator) {
        records.save(new AuditRecord(type, targetId, stage, verdict, detail, costMs, operator));
    }

    /**
     * 只跑隐私词表，不写规则路/AI 路的流水。
     * 用途：展品音频转写后的隐私检查 —— 它是对音频的补充检查，
     * 不应该重复记一次「文本审核」。
     */
    public List<String> scanPrivacy(String text) {
        return lexicon.findReviewOnly(text);
    }

    public int denyWordCount() {
        return lexicon.denySize();
    }

    public int reviewWordCount() {
        return lexicon.reviewOnlySize();
    }
}
