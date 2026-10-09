package com.yourname.museum.service;

import com.yourname.museum.client.AiResult;
import com.yourname.museum.common.SensitiveWordEngine;
import com.yourname.museum.common.SensitiveWordLexicon;
import com.yourname.museum.entity.AuditRecord;
import com.yourname.museum.repository.AuditRecordRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 三路审核：规则路（快、覆盖弱）→ AI 路（慢、覆盖强）→ 人工路（兜底）。
 *
 * <p>判定策略（答辩时可以直接讲这个「或」逻辑）：
 * <ul>
 *   <li>规则路命中 → REJECT，直接结束，省下一次 AI 调用；</li>
 *   <li>规则路没命中但 AI 判 safe=false 且 level=HIGH → REJECT；</li>
 *   <li>AI 判 MEDIUM，或只命中「隐私提醒类」词 → REVIEW，进人工队列；</li>
 *   <li>其余 → PASS。</li>
 * </ul>
 */
@Service
public class AuditService {

    private final SensitiveWordEngine engine;
    private final SensitiveWordLexicon lexicon;
    private final AuditRecordRepository auditRecordRepository;

    public AuditService(SensitiveWordEngine engine,
                        SensitiveWordLexicon lexicon,
                        AuditRecordRepository auditRecordRepository) {
        this.engine = engine;
        this.lexicon = lexicon;
        this.auditRecordRepository = auditRecordRepository;
    }

    public record Decision(String status, String reason) {
    }

    /** 规则路。返回命中的词；无命中返回空列表，同时落一条流水。 */
    public List<String> ruleStage(String text, Long bottleId) {
        long start = System.currentTimeMillis();
        List<String> hits = engine.match(text);
        String result = classifyRule(hits);
        save(bottleId, "RULE", result, String.join(",", hits), null, null,
                (int) (System.currentTimeMillis() - start));
        return hits;
    }

    /**
     * 规则路的处置结论（不涉及 AI）。
     *
     * <p>抽成独立方法有两个好处：上传流程与测试用同一份判断，
     * 不会出现「测试通过但线上不一致」；也让阶段 4 验收可以直接对着它断言。
     *
     * @return PASS（无命中）/ REVIEW（仅命中隐私提醒类）/ REJECT（命中拦截词）
     */
    public String classifyRule(List<String> hits) {
        if (hits == null || hits.isEmpty()) {
            return "PASS";
        }
        return hits.stream().allMatch(lexicon::isReviewOnly) ? "REVIEW" : "REJECT";
    }

    /**
     * 命中词里是否存在「必须拒绝」的词。
     *
     * <p>规则路命中后是否还要调用 AI，取决于这个判断：只有隐私提醒类命中时才继续调用，
     * 其余直接拒绝 —— 这是最直接的成本控制点。
     * 放在这里统一判断，避免调用方各自维护一份隐私词清单。
     */
    public boolean hasRejectableHit(List<String> hits) {
        return hits != null && hits.stream().anyMatch(h -> !lexicon.isReviewOnly(h));
    }

    /** AI 路。记录耗时、等级与理由。 */
    public void aiStage(AiResult aiResult, Long bottleId) {
        String result;
        if (!aiResult.safe()) {
            result = "HIGH".equalsIgnoreCase(aiResult.level()) ? "REJECT" : "REVIEW";
        } else {
            result = "MEDIUM".equalsIgnoreCase(aiResult.level()) ? "REVIEW" : "PASS";
        }
        save(bottleId, "AI", result, null, aiResult.level(), aiResult.reason(), aiResult.costMs());
    }

    /** 串起规则路与 AI 路，给出最终状态。 */
    public Decision decide(List<String> ruleHits, AiResult aiResult) {
        if (!ruleHits.isEmpty()) {
            boolean onlyPrivacy = ruleHits.stream().allMatch(lexicon::isReviewOnly);
            if (onlyPrivacy) {
                return new Decision("REVIEW",
                        "疑似包含个人信息（" + String.join(",", ruleHits) + "），转人工复审");
            }
            return new Decision("REJECT", "命中敏感词：" + String.join(",", ruleHits));
        }
        if (aiResult == null) {
            return new Decision("PENDING", "AI 不可用，等待重试");
        }
        if (aiResult.fallback()) {
            return new Decision("PASS", "AI 不可用，已降级为原文入库");
        }
        String level = aiResult.level() == null ? "LOW" : aiResult.level().toUpperCase();
        if (!aiResult.safe()) {
            return new Decision("HIGH".equals(level) ? "REJECT" : "REVIEW", "AI 判定：" + aiResult.reason());
        }
        if ("MEDIUM".equals(level)) {
            return new Decision("REVIEW", "AI 判定为中等风险：" + aiResult.reason());
        }
        return new Decision("PASS", "审核通过");
    }

    /** 人工复审。 */
    public void manualStage(Long bottleId, String result, String operator, String reason) {
        save(bottleId, "MANUAL", result, null, null, reason, 0,
                (operator == null || operator.isBlank()) ? "admin" : operator);
    }

    private void save(Long bottleId, String stage, String result,
                      String hitWords, String aiLevel, String aiReason, Integer costMs) {
        save(bottleId, stage, result, hitWords, aiLevel, aiReason, costMs, "system");
    }

    private void save(Long bottleId, String stage, String result,
                      String hitWords, String aiLevel, String aiReason, Integer costMs,
                      String operator) {
        AuditRecord record = new AuditRecord();
        record.setTargetType("VOICE_BOTTLE");
        record.setTargetId(bottleId);
        record.setStage(stage);
        record.setResult(result);
        record.setHitWords(hitWords);
        record.setAiLevel(aiLevel);
        record.setAiReason(aiReason);
        record.setCostMs(costMs);
        record.setOperator(operator);
        record.setCreatedAt(LocalDateTime.now());
        auditRecordRepository.save(record);
    }

    public int wordCount() {
        return engine.wordCount();
    }

    public String engineName() {
        return engine.name();
    }
}
