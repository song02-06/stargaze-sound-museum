package com.starmuseum.service;

import com.starmuseum.common.BizException;
import com.starmuseum.common.TraceCodec;
import com.starmuseum.asr.AsrClient;
import com.starmuseum.asr.AsrResult;
import com.starmuseum.domain.AuditRecord;
import com.starmuseum.domain.Exhibit;
import com.starmuseum.domain.Echo;
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

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

@Service
public class ExhibitService {

    private static final int MIN_FULL_CHARS = 15;
    private static final int MAX_FULL_CHARS = 400;

    private final ExhibitRepository exhibits;
    private final EchoRepository echoes;
    private final FileStorageService storage;
    private final AuditService audit;
    private final SignService signs;
    private final AsrClient asr;
    private final boolean privacyCheck;
    private final int minSeconds;
    private final int maxSeconds;
    private final int signMinChars;
    private final int signMaxChars;

    public ExhibitService(ExhibitRepository exhibits, EchoRepository echoes,
                          FileStorageService storage, AuditService audit, SignService signs,
                          AsrClient asr,
                          @Value("${museum.asr.exhibit-privacy-check:true}") boolean privacyCheck,
                          @Value("${museum.limits.exhibit-min-seconds:15}") int minSeconds,
                          @Value("${museum.limits.exhibit-max-seconds:60}") int maxSeconds,
                          @Value("${museum.limits.sign-min-chars:20}") int signMinChars,
                          @Value("${museum.limits.sign-max-chars:60}") int signMaxChars) {
        this.exhibits = exhibits;
        this.echoes = echoes;
        this.storage = storage;
        this.audit = audit;
        this.signs = signs;
        this.asr = asr;
        this.privacyCheck = privacyCheck;
        this.minSeconds = minSeconds;
        this.maxSeconds = maxSeconds;
        this.signMinChars = signMinChars;
        this.signMaxChars = signMaxChars;
    }

    public List<Dtos.ExhibitCard> passedCards() {
        return exhibits.findByStatusOrderByCreatedAtDesc(Exhibit.AuditStatus.PASS)
                .stream().map(this::toCard).toList();
    }

    /** 最近入馆的 N 件，首页底部用 */
    public List<Dtos.ExhibitCard> recentPassed(int limit) {
        List<Exhibit> all = exhibits.findByStatusOrderByCreatedAtDesc(Exhibit.AuditStatus.PASS);
        return all.stream().limit(limit).map(this::toCard).toList();
    }

    public Exhibit require(String idOrNo) {
        return exhibits.findByNo(idOrNo)
                .or(() -> parseId(idOrNo).flatMap(exhibits::findById))
                .orElseThrow(() -> new BizException("馆藏里没有这一段"));
    }

    public Dtos.ExhibitCard toCard(Exhibit e) {
        return new Dtos.ExhibitCard(
                e.getId(), e.getNo(), e.getTitle(), e.getSignText(), e.getPlace(),
                e.getRecordedOn().toString(), e.getSeconds(),
                TraceCodec.decode(e.getTrace()), "/audio/" + e.getAudioPath(),
                echoes.countByExhibitIdAndStatus(e.getId(), Echo.AuditStatus.PASS), e.isSeeded());
    }

    public Dtos.ExhibitDetail toDetail(Exhibit e) {
        return new Dtos.ExhibitDetail(
                e.getId(), e.getNo(), e.getTitle(), e.getSignText(), e.getFullText(), e.getPlace(),
                e.getRecordedOn().toString(), e.getSeconds(),
                TraceCodec.decode(e.getTrace()), "/audio/" + e.getAudioPath(),
                e.getKeeperName(), echoes.countByExhibitIdAndStatus(e.getId(), Echo.AuditStatus.PASS),
                e.isSeeded(), e.getStatus().name(), e.getAuditReason());
    }

    /**
     * 用户投稿。
     *
     * 时长由**服务端从 WAV 里读**，不用客户端报上来的数字 ——
     * 「15–60 秒」是硬约束，硬约束得由服务端裁决。
     */
    @Transactional
    public Exhibit create(Session keeper, MultipartFile audio, String title, String fullText,
                          String place, LocalDate recordedOn, String requestedSign) {
        String cleanTitle = require(title, "给这段声音起个名字吧", 80);
        String cleanFull = require(fullText, "写一段它的故事吧", MAX_FULL_CHARS);
        String cleanPlace = require(place, "这段声音是在哪里录的？", 60);
        if (cleanFull.length() < MIN_FULL_CHARS) {
            throw new BizException("故事太短了，至少写 " + MIN_FULL_CHARS + " 个字");
        }
        if (recordedOn == null) {
            throw new BizException("这段声音是什么时候录的？");
        }
        if (recordedOn.isAfter(LocalDate.now())) {
            throw new BizException("录制日期不能是未来");
        }

        String relative = storage.save(audio, "exhibits");
        Path saved = storage.resolve(relative);
        WavSupport.Info info = WavSupport.read(saved, 720);

        int seconds = (int) Math.round(info.seconds());
        if (seconds < minSeconds || seconds > maxSeconds) {
            // 不合格就把刚落的文件删掉，不留垃圾
            try {
                java.nio.file.Files.deleteIfExists(saved);
            } catch (Exception ignored) {
                // 删不掉也不影响这次请求的结果
            }
            throw new BizException("这段 %d 秒。声音要控制在 %d–%d 秒之间。"
                    .formatted(seconds, minSeconds, maxSeconds));
        }

        Exhibit exhibit = new Exhibit(nextNo(), cleanTitle, "", cleanFull, cleanPlace,
                recordedOn, relative, seconds, TraceCodec.encode(info.trace()),
                keeper.getId(), keeper.getNickname(), false);

        // 先落库拿到 id，审核流水才有对象可记
        exhibit = exhibits.save(exhibit);

        String sign = (requestedSign != null && !requestedSign.isBlank())
                ? checkSuppliedSign(cleanFull, requestedSign)
                : signs.compress(cleanFull, cleanTitle, exhibit.getId()).sign();
        exhibit.setSignText(sign);

        // 先做音频侧的隐私检查。产品定义 5.4 承诺过
        // 「音频里如果能听清别人的名字或隐私对话，必须处理后再上传」——
        // 这条只能靠转写来兑现，没有文本就没得查。
        if (privacyCheck) {
            checkAudioPrivacy(exhibit, saved);
        }

        AuditService.Outcome outcome = audit.auditText(TargetType.EXHIBIT, exhibit.getId(), cleanFull);
        switch (outcome.verdict()) {
            // 上面隐私检查可能已经把它打成 REVIEW，这种 PASS 不该把它盖回去
            case PASS -> {
                if (exhibit.getStatus() == Exhibit.AuditStatus.PENDING) {
                    exhibit.approve();
                }
            }
            case REVIEW -> exhibit.sendToReview(outcome.reason());
            case REJECT -> exhibit.reject(outcome.reason());
        }
        return exhibits.save(exhibit);
    }

    /**
     * 删掉自己埋下的一段。
     *
     * 只认埋它的人 —— 馆藏里那些没有 keeper 的演示数据，谁都删不掉。
     * 有回音就一起删：回音挂在展品下面，展品没了它们无所依附。
     * **审核流水不删**：那是流水账，记的是「发生过什么」，不是「现在有什么」，
     * 而且它是论文实验二的数据源，删掉等于改数据。
     *
     * @return 一起被删掉的回音条数，前端要拿它说清楚后果
     */
    @Transactional
    public long delete(Session keeper, String idOrNo) {
        Exhibit exhibit = require(idOrNo);
        if (exhibit.getKeeperId() == null || !exhibit.getKeeperId().equals(keeper.getId())) {
            throw new BizException("这不是你埋下的那一段");
        }

        long removedEchoes = echoes.countByExhibitIdAndStatus(exhibit.getId(), Echo.AuditStatus.PASS);
        echoes.deleteByExhibitId(exhibit.getId());
        exhibits.delete(exhibit);
        storage.delete(exhibit.getAudioPath());
        return removedEchoes;
    }

    /**
     * 音频侧的隐私检查。只有转写成文本才查得动 —— 没有文本就没有这一层。
     *
     * 失败时的处理是刻意不对称的：展品的**主要**审核对象是用户手写的全文，
     * 转写只是附加检查，所以转写失败不拦住展品本身，但会留下一条流水。
     * （语音回音不一样：它只有转写这一条依据，失败必须转人工。）
     */
    private void checkAudioPrivacy(Exhibit exhibit, Path savedFile) {
        AsrResult result = asr.transcribe(savedFile);
        audit.recordWithCost(TargetType.EXHIBIT, exhibit.getId(), AuditRecord.Stage.ASR,
                result.ok() ? AuditRecord.Verdict.PASS : AuditRecord.Verdict.REVIEW,
                result.ok()
                        ? (result.text().isBlank() ? "没有识别到人声（当作环境声）"
                                                   : "转写 " + result.text().length() + " 字")
                        : "转写失败，隐私检查未执行：" + result.error(),
                result.costMs(), "asr:" + asr.provider());

        if (!result.ok()) {
            return;
        }
        var privacyHits = audit.scanPrivacy(result.text());
        if (!privacyHits.isEmpty()) {
            exhibit.sendToReview("音频里可能录到了隐私信息：" + String.join("、", privacyHits));
        }
    }

    /** 用户自己提供的展签同样要过守卫 —— 用户也可能写错，规则不分对象 */
    private String checkSuppliedSign(String full, String sign) {
        var result = com.starmuseum.sign.SignGuard.check(full, sign);
        if (!result.ok()) {
            throw new BizException("展签和你的原文对不上：" + result.joined());
        }
        return sign.strip();
    }

    public String nextNo() {
        return exhibits.findFirstByOrderByNoDesc()
                .map(last -> String.format("%04d", Integer.parseInt(last.getNo()) + 1))
                .orElse("0247");
    }

    public int signMaxChars() {
        return signMaxChars;
    }

    public int signMinChars() {
        return signMinChars;
    }

    public long countPassed() {
        return exhibits.countByStatus(Exhibit.AuditStatus.PASS);
    }

    private static String require(String value, String message, int max) {
        String v = value == null ? "" : value.strip();
        if (v.isEmpty()) {
            throw new BizException(message);
        }
        if (v.length() > max) {
            throw new BizException("太长了，请控制在 " + max + " 个字以内");
        }
        return v;
    }

    private static java.util.Optional<Long> parseId(String raw) {
        try {
            return java.util.Optional.of(Long.parseLong(raw));
        } catch (NumberFormatException e) {
            return java.util.Optional.empty();
        }
    }
}
