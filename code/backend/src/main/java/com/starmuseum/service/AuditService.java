package com.starmuseum.service;

import com.starmuseum.audit.SensitiveLexicon;
import com.starmuseum.domain.AuditRecord;
import com.starmuseum.domain.TargetType;
import com.starmuseum.repo.AuditRecordRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 审核：**本地规则 + 人工复审**。
 *
 * 这里刻意没有「AI 路」。以前这里有一条所谓 AI 判定，实际是直接写一行
 * "mock 判定：safe" 就放行 —— 那不是审核结果，只是给流水凑了一行。
 * 本项目本轮不接真实大模型内容审核，所以如实只保留两道：
 *
 *   1）规则路：本地 DFA 词表（拦截词 → 驳回，隐私类词 → 转人工），微秒级、可离线；
 *   2）人工路：规则路拿不准的进复审队列，由管理员听、看、决定。
 *
 * 规则路命中时不再做任何"上层判定"—— 违规内容在最便宜的一路上就被拦下。
 */
@Service
public class AuditService {

    public enum Verdict { PASS, REJECT, REVIEW }

    public record Outcome(Verdict verdict, String reason, List<String> hits) {
    }

    /**
     * 一个字段的审核结果 + 它是哪个字段。
     * 展品的标题、故事、地点要**分别**过规则，不能只查全文。
     */
    public record FieldSpec(String label, String text) {
    }

    private final SensitiveLexicon lexicon;
    private final AuditRecordRepository records;

    public AuditService(SensitiveLexicon lexicon, AuditRecordRepository records) {
        this.lexicon = lexicon;
        this.records = records;
    }

    /** 单段文字（回音、转写文本）走一遍规则路 */
    public Outcome auditText(TargetType type, Long targetId, String text) {
        return auditFields(type, targetId,
                List.of(new FieldSpec("内容", text == null ? "" : text)));
    }

    /**
     * 多字段一起过规则路，结论按 REJECT &gt; REVIEW &gt; PASS 合并。
     *
     * 一次调用只写一行规则流水，detail 里写清是哪个字段出的问题 ——
     * 既省流水又不丢信息。合并顺序是刻意的：只要有字段被拦，
     * 整件东西就不该因为别的字段干净而放行。
     */
    public Outcome auditFields(TargetType type, Long targetId, List<FieldSpec> fields) {
        long t0 = System.nanoTime();

        List<String> denyBits = new ArrayList<>();
        List<String> reviewBits = new ArrayList<>();
        List<String> hitWords = new ArrayList<>();

        for (FieldSpec field : fields) {
            String content = field.text() == null ? "" : field.text();

            List<String> denied = lexicon.findDeny(content);
            if (!denied.isEmpty()) {
                denyBits.add(field.label() + " 命中拦截词：" + String.join("、", denied));
                hitWords.addAll(denied);
                // 同一个字段不必再查隐私词：它已经直接被拦了
                continue;
            }

            List<String> privateHits = lexicon.findReviewOnly(content);
            if (!privateHits.isEmpty()) {
                reviewBits.add(field.label() + " 命中隐私类词，转人工：" + String.join("、", privateHits));
                hitWords.addAll(privateHits);
            }
        }

        if (!denyBits.isEmpty()) {
            String detail = String.join("；", denyBits);
            record(type, targetId, AuditRecord.Stage.RULE, AuditRecord.Verdict.REJECT, detail, t0,
                    "system");
            return new Outcome(Verdict.REJECT, "这段内容里有不适合公开的词（" + detail + "）", hitWords);
        }

        if (!reviewBits.isEmpty()) {
            String detail = String.join("；", reviewBits);
            record(type, targetId, AuditRecord.Stage.RULE, AuditRecord.Verdict.REVIEW, detail, t0,
                    "system");
            return new Outcome(Verdict.REVIEW, "内容里可能包含他人隐私，需要人工看一眼。", hitWords);
        }

        record(type, targetId, AuditRecord.Stage.RULE, AuditRecord.Verdict.PASS,
                fields.stream().map(FieldSpec::label).reduce((a, b) -> a + "、" + b).orElse("内容")
                        + " 未命中拦截词",
                t0, "system");
        return new Outcome(Verdict.PASS, null, List.of());
    }

    /**
     * 展品：标题、故事、地点分别审，再把结论合并。
     * 顺序固定，流水里读起来稳定。
     */
    public Outcome auditExhibit(Long targetId, String title, String fullText, String place) {
        List<FieldSpec> fields = new ArrayList<>(3);
        fields.add(new FieldSpec("标题", title));
        fields.add(new FieldSpec("故事", fullText));
        fields.add(new FieldSpec("地点", place));
        return auditFields(TargetType.EXHIBIT, targetId, fields);
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
     * 只跑隐私词表，不写流水。
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

    /** 只给文档/接口展示用：当前审核能力的诚实说明 */
    public Map<String, String> capability() {
        Map<String, String> out = new LinkedHashMap<>();
        out.put("ruleEngine", "本地 DFA 词表 + 隐私词转人工");
        out.put("aiAudit", "未接入（不把 mock 判定当作审核结果）");
        out.put("manualReview", "管理端 /admin 人工复审，决定写入审核流水");
        out.put("asr", "语音回音需要真实 ASR；未配置时一律转人工");
        return out;
    }
}
