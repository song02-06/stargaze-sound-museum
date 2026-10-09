package com.starmuseum.service;

import com.starmuseum.domain.AuditRecord;
import com.starmuseum.domain.TargetType;
import com.starmuseum.sign.MockSignCompressor;
import com.starmuseum.sign.RemoteSignCompressor;
import com.starmuseum.sign.SignCompressor;
import com.starmuseum.sign.SignGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 把用户全文压成展签。
 *
 * 输出**必须**过 SignGuard。这是产品定义里那条红线的执行点：
 * 「展签上的每一个字都能在作者原话里找到出处」。
 * 模型输出不合规 → 退回本地压缩器；本地也不合规 → 退回原文前缀。
 * 三级兜底，永远不可能产出一个造假的展签。
 */
@Service
public class SignService {

    private static final Logger log = LoggerFactory.getLogger(SignService.class);

    public record Result(String sign, String provider, boolean guarded,
                         List<String> violations, boolean fellBack) {
    }

    private final MockSignCompressor mock = new MockSignCompressor();
    private final SignCompressor primary;
    private final AuditService audit;
    private final int maxChars;

    public SignService(@Value("${museum.sign.provider:mock}") String provider,
                       @Value("${museum.sign.base-url:}") String baseUrl,
                       @Value("${museum.sign.api-key:}") String apiKey,
                       @Value("${museum.sign.model:}") String model,
                       @Value("${museum.sign.timeout-ms:12000}") int timeoutMs,
                       @Value("${museum.limits.sign-max-chars:60}") int maxChars,
                       AuditService audit) {
        this.audit = audit;
        this.maxChars = maxChars;
        this.primary = "remote".equalsIgnoreCase(provider) && !apiKey.isBlank() && !baseUrl.isBlank()
                ? new RemoteSignCompressor(baseUrl, apiKey, model, timeoutMs)
                : mock;
    }

    public String provider() {
        return primary.provider();
    }

    public Result compress(String fullText, String title, Long targetId) {
        List<String> violations = new ArrayList<>();

        String candidate = primary.compress(fullText, title, maxChars);
        SignGuard.Result check = SignGuard.check(fullText, candidate);
        boolean fellBack = false;

        if (!check.ok()) {
            violations.addAll(check.violations());
            log.warn("展签没过守卫（{}），改用本地压缩器。违规：{}",
                    primary.provider(), check.joined());
            candidate = mock.compress(fullText, title, maxChars);
            check = SignGuard.check(fullText, candidate);
            fellBack = true;
        }

        if (!check.ok()) {
            // 最后一层：原文前缀。前缀永远是子序列，物理上不可能造假。
            violations.addAll(check.violations());
            candidate = fullText.length() <= maxChars ? fullText.strip()
                    : fullText.strip().substring(0, maxChars);
            log.error("连本地压缩器都没过守卫，退回原文前缀。违规：{}", check.joined());
        }

        if (targetId != null) {
            audit.record(TargetType.EXHIBIT, targetId, AuditRecord.Stage.SIGN,
                    check.ok() ? AuditRecord.Verdict.PASS : AuditRecord.Verdict.REVIEW,
                    "provider=" + primary.provider()
                            + (fellBack ? "，已退回本地" : "")
                            + (violations.isEmpty() ? "" : "；违规：" + String.join("；", violations)),
                    0L, "sign");
        }

        return new Result(candidate, primary.provider(), check.ok(), List.copyOf(violations), fellBack);
    }
}
