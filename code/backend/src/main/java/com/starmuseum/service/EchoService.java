package com.starmuseum.service;

import com.starmuseum.common.BizException;
import com.starmuseum.common.TraceCodec;
import com.starmuseum.asr.AsrClient;
import com.starmuseum.asr.AsrResult;
import com.starmuseum.domain.AuditRecord;
import com.starmuseum.domain.Echo;
import com.starmuseum.domain.Exhibit;
import com.starmuseum.domain.Session;
import com.starmuseum.domain.TargetType;
import com.starmuseum.media.FileStorageService;
import com.starmuseum.media.WavSupport;
import com.starmuseum.repo.EchoRepository;
import com.starmuseum.repo.ExhibitRepository;
import com.starmuseum.web.dto.Dtos;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 回音。
 *
 * 三条产品约束在这里落地：
 * 1）回音默认**公开** —— 查询不带任何"仅作者可见"的过滤，收件箱只是汇总视图；
 * 2）语音回音 10–30 秒，服务端从 WAV 里读时长，不听客户端报数；
 * 3）**转写不出来就不能放行**。语音回音只有转写这一条审核依据。
 */
@Service
public class EchoService {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final int MAX_TEXT_CHARS = 200;

    private final EchoRepository echoes;
    private final ExhibitRepository exhibits;
    private final FileStorageService storage;
    private final AuditService audit;
    private final AsrClient asr;
    private final NotificationService notifications;
    private final int minSeconds;
    private final int maxSeconds;

    public EchoService(EchoRepository echoes, ExhibitRepository exhibits,
                       FileStorageService storage, AuditService audit, AsrClient asr,
                       NotificationService notifications,
                       @Value("${museum.limits.echo-min-seconds:10}") int minSeconds,
                       @Value("${museum.limits.echo-max-seconds:30}") int maxSeconds) {
        this.echoes = echoes;
        this.exhibits = exhibits;
        this.storage = storage;
        this.audit = audit;
        this.asr = asr;
        this.notifications = notifications;
        this.minSeconds = minSeconds;
        this.maxSeconds = maxSeconds;
    }

    public List<Dtos.EchoView> listFor(Long exhibitId) {
        return echoes.findByExhibitIdAndStatusOrderByCreatedAtDesc(exhibitId, Echo.AuditStatus.PASS)
                .stream().map(this::toView).toList();
    }

    public Dtos.EchoView toView(Echo e) {
        return new Dtos.EchoView(
                e.getId(), e.getAuthorName(), e.getKind().name().toLowerCase(),
                e.getBody(),
                e.getAudioPath() == null ? null : "/audio/" + e.getAudioPath(),
                e.getSeconds(), TraceCodec.decode(e.getTrace()),
                e.getCreatedAt() == null ? null : e.getCreatedAt().format(ISO),
                e.getStatus().name(), e.getAuditReason(), e.getAuthorId());
    }

    /**
     * 「我发出的回音」。
     *
     * 发送人有权知道自己那条现在在哪：是已经公开、在等人工、还是被驳回、为什么。
     * 只列 PASS 的公开列表做不到这件事。
     */
    public List<Dtos.MyEcho> mine(Long authorId) {
        return echoes.findByAuthorIdOrderByCreatedAtDesc(authorId).stream()
                .map(e -> {
                    Exhibit exhibit = exhibits.findById(e.getExhibitId()).orElse(null);
                    return new Dtos.MyEcho(
                            e.getId(),
                            exhibit == null ? null : exhibit.getNo(),
                            exhibit == null ? null : exhibit.getTitle(),
                            e.getKind().name().toLowerCase(),
                            e.getBody(),
                            e.getAudioPath() == null ? null : "/audio/" + e.getAudioPath(),
                            e.getSeconds(),
                            e.getStatus().name(), e.getAuditReason(),
                            e.getCreatedAt() == null ? null : e.getCreatedAt().format(ISO));
                })
                .toList();
    }

    @Transactional
    public Echo addText(Session author, Exhibit exhibit, String body) {
        String text = body == null ? "" : body.strip();
        if (text.isEmpty()) {
            throw new BizException("先写点什么吧");
        }
        if (text.length() > MAX_TEXT_CHARS) {
            throw new BizException("回音请控制在 " + MAX_TEXT_CHARS + " 个字以内");
        }
        return save(author, exhibit, Echo.Kind.TEXT, text, null, null, null);
    }

    @Transactional
    public Echo addVoice(Session author, Exhibit exhibit, MultipartFile audio) {
        String relative = storage.save(audio, "echoes");
        Path saved = storage.resolve(relative);
        WavSupport.Info info = WavSupport.read(saved, 200);

        int seconds = (int) Math.round(info.seconds());
        if (seconds < minSeconds || seconds > maxSeconds) {
            try {
                Files.deleteIfExists(saved);
            } catch (Exception ignored) {
                // 删不掉不影响返回结果
            }
            throw new BizException("这段语音 %d 秒。回音要控制在 %d–%d 秒之间。"
                    .formatted(seconds, minSeconds, maxSeconds));
        }

        // 没有配置真实语音识别时，**不要假装识别过**。
        // 拿随机样例句子当用户说的话、再据以放行，是最坏的一种假通过。
        if (!asr.reliable()) {
            Echo echo = new Echo(exhibit.getId(), author.getId(), author.getNickname(),
                    Echo.Kind.VOICE, null, relative, seconds,
                    TraceCodec.encode(info.trace()), false);
            echo = echoes.save(echo);
            echo.sendToReview("没有配置真实语音识别（provider=" + asr.provider()
                    + "），这段语音需要人工听一遍");
            audit.recordWithCost(TargetType.ECHO, echo.getId(), AuditRecord.Stage.ASR,
                    AuditRecord.Verdict.REVIEW,
                    "未配置真实语音识别，未生成转写文本，直接转人工", 0L,
                    "asr:" + asr.provider());
            return echoes.save(echo);
        }

        // 语音回音**只有这一条审核依据**：先把音频转成文字，再用文字过审核。
        AsrResult asrResult = asr.transcribe(saved);

        Echo echo = new Echo(exhibit.getId(), author.getId(), author.getNickname(),
                Echo.Kind.VOICE, asrResult.ok() ? asrResult.text() : null,
                relative, seconds, TraceCodec.encode(info.trace()), false);
        echo = echoes.save(echo);

        audit.recordWithCost(TargetType.ECHO, echo.getId(), AuditRecord.Stage.ASR,
                asrResult.ok() ? AuditRecord.Verdict.PASS : AuditRecord.Verdict.REVIEW,
                (asrResult.ok() ? "转写 " + asrResult.text().length() + " 字" : "转写失败：" + asrResult.error()),
                asrResult.costMs(), "asr:" + asr.provider());

        if (!asrResult.ok()) {
            // 转写失败 ≠ 内容没问题。这段音频从没被人看过，
            // 所以它不能直接进公开列表，必须转人工听一遍。
            echo.sendToReview("语音没能转成文字，需要人工听一遍：" + asrResult.error());
            return echoes.save(echo);
        }

        // 转写成功：拿文本走规则路
        AuditService.Outcome outcome = audit.auditText(TargetType.ECHO, echo.getId(), asrResult.text());
        applyOutcome(echo, outcome);
        Echo savedEcho = echoes.save(echo);
        notifyIfDelivered(exhibit, savedEcho);
        return savedEcho;
    }

    private Echo save(Session author, Exhibit exhibit, Echo.Kind kind, String body,
                      String audioPath, Integer seconds, String trace) {
        Echo echo = new Echo(exhibit.getId(), author.getId(), author.getNickname(), kind,
                body, audioPath, seconds, trace, false);
        echo = echoes.save(echo);

        AuditService.Outcome outcome = audit.auditText(TargetType.ECHO, echo.getId(),
                body == null ? "" : body);
        applyOutcome(echo, outcome);
        Echo savedEcho = echoes.save(echo);
        notifyIfDelivered(exhibit, savedEcho);
        return savedEcho;
    }

    private static void applyOutcome(Echo echo, AuditService.Outcome outcome) {
        switch (outcome.verdict()) {
            case PASS -> echo.approve();
            case REVIEW -> echo.sendToReview(outcome.reason());
            case REJECT -> echo.reject(outcome.reason());
        }
    }

    /** 只有真正公开了才通知展品作者。待审和被驳回的不算送达 */
    private void notifyIfDelivered(Exhibit exhibit, Echo echo) {
        if (echo.getStatus() == Echo.AuditStatus.PASS) {
            notifications.echoReceived(exhibit, echo);
        }
    }

    /** 我的声音页面：我回应过的展品 */
    public List<Echo> byAuthor(Long authorId) {
        return echoes.findByAuthorIdOrderByCreatedAtDesc(authorId);
    }

    public long countPassed() {
        return echoes.countByStatus(Echo.AuditStatus.PASS);
    }
}
