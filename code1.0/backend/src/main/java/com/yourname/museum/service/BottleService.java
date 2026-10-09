package com.yourname.museum.service;

import com.yourname.museum.client.AiClient;
import com.yourname.museum.client.AiResult;
import com.yourname.museum.client.AsrClient;
import com.yourname.museum.client.AsrResult;
import com.yourname.museum.common.BizException;
import com.yourname.museum.dto.BottleDetail;
import com.yourname.museum.entity.AiTaskLog;
import com.yourname.museum.entity.VoiceBottle;
import com.yourname.museum.repository.AiTaskLogRepository;
import com.yourname.museum.repository.VoiceBottleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 上传主流程：落盘 → ASR → 规则路 → AI 润色 → 定状态入库。
 *
 * <p>设计原则：ASR 失败不阻塞上传（标记 PENDING 等待重试），
 * AI 失败降级为原文入库 —— 保证「永远有一条能演示的链路」。
 */
@Service
public class BottleService {

    private static final Logger log = LoggerFactory.getLogger(BottleService.class);

    private final VoiceBottleRepository bottleRepository;
    private final AiTaskLogRepository aiTaskLogRepository;
    private final FileStorageService fileStorageService;
    private final AuditService auditService;
    private final AsrClient asrClient;
    private final AiClient aiClient;

    @Value("${museum.max-duration-ms}")
    private int maxDurationMs;

    @Value("${museum.min-duration-ms}")
    private int minDurationMs;

    public BottleService(VoiceBottleRepository bottleRepository,
                         AiTaskLogRepository aiTaskLogRepository,
                         FileStorageService fileStorageService,
                         AuditService auditService,
                         AsrClient asrClient,
                         AiClient aiClient) {
        this.bottleRepository = bottleRepository;
        this.aiTaskLogRepository = aiTaskLogRepository;
        this.fileStorageService = fileStorageService;
        this.auditService = auditService;
        this.asrClient = asrClient;
        this.aiClient = aiClient;
    }

    @Transactional
    public BottleDetail upload(MultipartFile file, String note, Integer durationMs) {
        validate(file, durationMs);

        String audioPath = fileStorageService.save(file, "bottle");

        VoiceBottle bottle = new VoiceBottle();
        bottle.setAudioPath(audioPath);
        bottle.setDurationMs(durationMs);
        bottle.setOriginalNote(note);
        bottle.setSource("UPLOAD");
        bottle.setAuditStatus("PENDING");
        bottle.setCreatedAt(LocalDateTime.now());
        bottle = bottleRepository.save(bottle);

        // ---- 1. 语音转写 ----
        AsrResult asr = asrClient.transcribe(fileStorageService.resolve(audioPath));
        if (!asr.success()) {
            bottle.setAuditReason("转写失败：" + asr.error());
            bottleRepository.save(bottle);
            log.warn("ASR failed for bottle {}: {}", bottle.getId(), asr.error());
            return BottleDetail.of(bottle);
        }
        bottle.setRawTranscript(asr.text());

        // ---- 2. 规则路审核 ----
        List<String> hits = auditService.ruleStage(asr.text() + " " + (note == null ? "" : note), bottle.getId());

        // ---- 3. AI 润色（规则路已 REJECT 时省下这次调用）----
        // 判定逻辑收敛在 AuditService 里，避免这里再维护一份隐私词清单
        boolean alreadyRejected = auditService.hasRejectableHit(hits);

        AiResult ai = null;
        if (!alreadyRejected) {
            long start = System.currentTimeMillis();
            ai = aiClient.polish(asr.text(), note);
            auditService.aiStage(ai, bottle.getId());
            saveAiLog(bottle.getId(), ai, asr.text(), start);
        }

        // ---- 4. 定状态 ----
        AuditService.Decision decision = alreadyRejected
                ? auditService.decide(hits, null)
                : auditService.decide(hits, ai);

        bottle.setAuditStatus(decision.status());
        bottle.setAuditReason(decision.reason());
        if (ai != null) {
            bottle.setTitle(ai.title());
            bottle.setPolishedText(ai.story());
            bottle.setTags(ai.tags());
            bottle.setEmotion(ai.emotion());
        } else {
            bottle.setTitle(asr.text().substring(0, Math.min(10, asr.text().length())));
        }
        bottleRepository.save(bottle);
        return BottleDetail.of(bottle);
    }

    private void saveAiLog(Long bottleId, AiResult ai, String prompt, long start) {
        AiTaskLog logRow = new AiTaskLog();
        logRow.setBottleId(bottleId);
        logRow.setProvider(aiClient.providerName());
        logRow.setModel(aiClient.modelName());
        logRow.setPromptChars(prompt == null ? 0 : prompt.length());
        logRow.setOutputChars(ai.story() == null ? 0 : ai.story().length());
        logRow.setCostMs(ai.costMs() > 0 ? ai.costMs() : (int) (System.currentTimeMillis() - start));
        logRow.setSuccess(!ai.fallback());
        logRow.setFallback(ai.fallback());
        logRow.setRawResponse(ai.rawResponse());
        logRow.setCreatedAt(LocalDateTime.now());
        aiTaskLogRepository.save(logRow);
    }

    private void validate(MultipartFile file, Integer durationMs) {
        if (file == null || file.isEmpty()) {
            throw new BizException(4003, "音频文件为空");
        }
        if (durationMs == null || durationMs < minDurationMs) {
            throw new BizException(4004, "录音太短了，至少 " + (minDurationMs / 1000) + " 秒");
        }
        if (durationMs > maxDurationMs) {
            throw new BizException(4005, "录音太长了，最多 " + (maxDurationMs / 1000) + " 秒");
        }
    }

    public BottleDetail get(Long id) {
        return bottleRepository.findById(id)
                .map(BottleDetail::of)
                .orElseThrow(() -> new BizException(4006, "这段声音不存在"));
    }

    public List<BottleDetail> recent() {
        return bottleRepository.findTop20ByAuditStatusOrderByCreatedAtDesc("PASS")
                .stream().map(BottleDetail::of).toList();
    }

    /** 人工复审。 */
    @Transactional
    public BottleDetail review(Long id, boolean pass, String reason) {
        VoiceBottle bottle = bottleRepository.findById(id)
                .orElseThrow(() -> new BizException(4006, "这段声音不存在"));
        String status = pass ? "PASS" : "REJECT";
        bottle.setAuditStatus(status);
        bottle.setAuditReason(reason == null || reason.isBlank()
                ? ("人工复审：" + (pass ? "通过" : "驳回"))
                : reason);
        bottleRepository.save(bottle);
        auditService.manualStage(id, pass ? "PASS" : "REJECT", "admin", reason);
        return BottleDetail.of(bottle);
    }

    public List<BottleDetail> reviewQueue() {
        return bottleRepository.findByAuditStatus("REVIEW")
                .stream().map(BottleDetail::of).toList();
    }

    public List<BottleDetail> byStatus(String status) {
        return bottleRepository.findByAuditStatus(status)
                .stream().map(BottleDetail::of).toList();
    }
}
