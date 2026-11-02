package com.starmuseum.service;

import com.starmuseum.common.BizException;
import com.starmuseum.common.TraceCodec;
import com.starmuseum.asr.AsrClient;
import com.starmuseum.asr.AsrResult;
import com.starmuseum.config.ExhibitNoAllocator;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
    private final NotificationService notifications;
    private final ExhibitNoAllocator noAllocator;
    private final boolean privacyCheck;
    private final int minSeconds;
    private final int maxSeconds;
    private final int signMinChars;
    private final int signMaxChars;

    public ExhibitService(ExhibitRepository exhibits, EchoRepository echoes,
                          FileStorageService storage, AuditService audit, SignService signs,
                          AsrClient asr, NotificationService notifications,
                          ExhibitNoAllocator noAllocator,
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
        this.notifications = notifications;
        this.noAllocator = noAllocator;
        this.privacyCheck = privacyCheck;
        this.minSeconds = minSeconds;
        this.maxSeconds = maxSeconds;
        this.signMinChars = signMinChars;
        this.signMaxChars = signMaxChars;
    }

    public List<Dtos.ExhibitCard> passedCards() {
        return passedCards(null);
    }

    /**
     * 馆藏列表 + 关键词检索。
     *
     * 只筛 PASS 的内容；关键词覆盖标题、故事、地点。
     * 数据量还小，用不上搜索引擎，也不用为检索重算波形。
     */
    public List<Dtos.ExhibitCard> passedCards(String keyword) {
        List<Exhibit> all = exhibits.findByStatusOrderByCreatedAtDesc(Exhibit.AuditStatus.PASS);
        String q = keyword == null ? "" : keyword.strip().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) {
            return all.stream().map(this::toCard).toList();
        }
        List<Dtos.ExhibitCard> out = new ArrayList<>();
        for (Exhibit e : all) {
            if (matches(e, q)) {
                out.add(toCard(e));
            }
        }
        return out;
    }

    private static boolean matches(Exhibit e, String q) {
        return contains(e.getTitle(), q) || contains(e.getFullText(), q)
                || contains(e.getPlace(), q) || contains(e.getSignText(), q);
    }

    private static boolean contains(String haystack, String q) {
        return haystack != null && haystack.toLowerCase(Locale.ROOT).contains(q);
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
                e.isSeeded(), e.getStatus().name(), e.getAuditReason(),
                e.getSoundType().name(), e.getSourceUrl(), e.getSourceAuthor(), e.getSourceLicense(), e.getSourceDateText());
    }

    /** 「我的声音」里的投稿列表。任何状态都列 —— 包括没通过的那几段 */
    public List<Dtos.MyUpload> myUploads(Long sessionId) {
        return exhibits.findByKeeperIdOrderByCreatedAtDesc(sessionId).stream()
                .map(e -> new Dtos.MyUpload(
                        e.getNo(), e.getTitle(), e.getStatus().name(), e.getAuditReason(),
                        e.getRecordedOn().toString(), e.getSeconds(),
                        "/audio/" + e.getAudioPath(),
                        e.getCreatedAt() == null ? null : e.getCreatedAt().toString(),
                        // 删之前要说清楚后果：这件下面挂着几条别人的回音
                        echoes.findByExhibitId(e.getId()).size(),
                        echoes.countByExhibitIdAndStatus(e.getId(), Echo.AuditStatus.PASS),
                        e.getSoundType().name(),
                        e.getUpdatedAt() == null ? null : e.getUpdatedAt().toString()))
                .toList();
    }

    /**
     * 编辑页要回填的原始字段。
     *
     * 只给**原作者**，且包括待审和驳回的内容 ——
     * 公共详情接口只公开已通过的，未通过的内容不会从那里漏出去。
     */
    public Dtos.UploadSource sourceFor(Session keeper, String idOrNo) {
        Exhibit e = own(keeper, idOrNo);
        return new Dtos.UploadSource(
                e.getNo(), e.getTitle(), e.getFullText(), e.getPlace(),
                e.getRecordedOn().toString(), e.getSoundType().name(), e.isOriginalConfirmed(),
                e.getStatus().name(), e.getAuditReason(),
                "/audio/" + e.getAudioPath(), e.getSeconds());
    }

    /**
     * 用户投稿。
     *
     * 时长由**服务端从 WAV 里读**，不用客户端报上来的数字 ——
     * 「10–60 秒」是硬约束，硬约束得由服务端裁决。
     */
    @Transactional
    public Exhibit create(Session keeper, MultipartFile audio, String title, String fullText,
                          String place, LocalDate recordedOn, String requestedSign,
                          Exhibit.SoundType soundType, Boolean originalConfirmed) {
        String cleanTitle = require(title, "给这段声音起个名字吧", 80);
        String cleanFull = require(fullText, "写一段它的故事吧", MAX_FULL_CHARS);
        String cleanPlace = require(place, "这段声音是在哪里录的？", 60);
        requireConfirmed(originalConfirmed);
        requireRecordedOn(recordedOn);
        if (cleanFull.length() < MIN_FULL_CHARS) {
            throw new BizException("故事太短了，至少写 " + MIN_FULL_CHARS + " 个字");
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
        exhibit.applySubmission(soundType, Boolean.TRUE.equals(originalConfirmed));

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

        applyOutcome(exhibit, audit.auditExhibit(exhibit.getId(), cleanTitle, cleanFull, cleanPlace),
                true);
        applySingingRule(exhibit);
        return exhibits.save(exhibit);
    }

    /**
     * 编辑自己的投稿。
     *
     * 和「再投一次」的区别很重要：id、编号、作者、音频、创建时间全都不变，
     * 变的是用户写的那几个字段。改完**重新审一遍** ——
     * 不能靠之前那次通过的结论给新内容背书。
     *
     * 本轮编辑不支持替换音频：换声音等于换了一件东西，那应该重新投稿。
     */
    @Transactional
    public Exhibit update(Session keeper, String idOrNo, String title, String fullText,
                          String place, LocalDate recordedOn, Exhibit.SoundType soundType,
                          Boolean originalConfirmed) {
        Exhibit exhibit = own(keeper, idOrNo);
        if (exhibit.getAudioReviewReason() == null && exhibit.getStatus() == Exhibit.AuditStatus.REVIEW
                && exhibit.getAuditReason() != null && exhibit.getAuditReason().startsWith("音频里可能录到了隐私信息")) {
            exhibit.requireAudioReview(exhibit.getAuditReason());
        }

        String cleanTitle = require(title, "给这段声音起个名字吧", 80);
        String cleanFull = require(fullText, "写一段它的故事吧", MAX_FULL_CHARS);
        String cleanPlace = require(place, "这段声音是在哪里录的？", 60);
        requireConfirmed(originalConfirmed);
        requireRecordedOn(recordedOn);
        if (cleanFull.length() < MIN_FULL_CHARS) {
            throw new BizException("故事太短了，至少写 " + MIN_FULL_CHARS + " 个字");
        }

        exhibit.applyEdit(cleanTitle, cleanFull, cleanPlace, recordedOn, soundType,
                Boolean.TRUE.equals(originalConfirmed));

        // 故事变了，展签必须跟着重新生成 —— 否则展签会指向已经不存在的原文
        exhibit.setSignText(signs.compress(cleanFull, cleanTitle, exhibit.getId()).sign());

        applyOutcome(exhibit, audit.auditExhibit(exhibit.getId(), cleanTitle, cleanFull, cleanPlace),
                false);
        applySingingRule(exhibit);
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
     * 磁盘上的音频分两批删：展品自己那一段，和**全部**语音回音 ——
     * 包括待审和被驳回的。它们也已经落盘了，只清 PASS 会把文件留在磁盘上。
     *
     * @return 一起被删掉的公开回音条数，前端要拿它说清楚后果
     */
    @Transactional
    public long delete(Session keeper, String idOrNo) {
        Exhibit exhibit = own(keeper, idOrNo);
        Long exhibitId = exhibit.getId();

        List<Echo> allEchoes = echoes.findByExhibitId(exhibitId);
        long publicEchoes = allEchoes.stream()
                .filter(e -> e.getStatus() == Echo.AuditStatus.PASS).count();

        List<String> audioToRemove = new ArrayList<>();
        for (Echo echo : allEchoes) {
            if (echo.getAudioPath() != null && !echo.getAudioPath().isBlank()) {
                audioToRemove.add(echo.getAudioPath());
            }
        }
        String exhibitAudio = exhibit.getAudioPath();

        // 指向这件展品的消息一并失效：不能留一个必然点不开的入口
        notifications.invalidateTarget(TargetType.EXHIBIT, exhibitId);

        echoes.deleteByExhibitId(exhibitId);
        exhibits.delete(exhibit);

        // 数据库真的提交之后再删文件。反过来会出现
        // 「文件没了、事务回滚了」这种对不上的状态。
        deleteFilesAfterCommit(audioToRemove, exhibitAudio);
        return publicEchoes;
    }

    private void deleteFilesAfterCommit(List<String> echoAudio, String exhibitAudio) {
        Runnable cleanup = () -> {
            for (String path : echoAudio) {
                storage.delete(path);
            }
            storage.delete(exhibitAudio);
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cleanup.run();
                }
            });
        } else {
            cleanup.run();
        }
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
            if (exhibit.getSoundType() != Exhibit.SoundType.ENVIRONMENT) {
                exhibit.requireAudioReview("音频尚未核验，需要人工听一遍");
            }
            return;
        }
        var privacyHits = audit.scanPrivacy(result.text());
        if (!privacyHits.isEmpty()) {
            exhibit.requireAudioReview("音频里可能录到了隐私信息：" + String.join("、", privacyHits));
        }
    }

    /** 把规则路的结论落到状态上。创建时不能把隐私检查留下的 REVIEW 盖回 PASS */
    private void applyOutcome(Exhibit exhibit, AuditService.Outcome outcome, boolean onCreate) {
        switch (outcome.verdict()) {
            case PASS -> {
                if (exhibit.getAudioReviewReason() != null) {
                    exhibit.sendToReview(exhibit.getAudioReviewReason());
                } else if (!onCreate || exhibit.getStatus() == Exhibit.AuditStatus.PENDING) {
                    exhibit.approve();
                }
            }
            case REVIEW -> exhibit.sendToReview(outcome.reason());
            case REJECT -> exhibit.reject(outcome.reason());
        }
    }

    /**
     * 演唱类强制人工复审。
     *
     * 产品页面上写了「演唱类的会转人工复审，慢一点，但会有人认真听」——
     * 那就必须真的这么做，不能只写在前端。
     */
    private void applySingingRule(Exhibit exhibit) {
        if (exhibit.getSoundType() == Exhibit.SoundType.SINGING
                && exhibit.getStatus() == Exhibit.AuditStatus.PASS) {
            exhibit.sendToReview("演唱类内容按约定转人工复审，需要有人听一遍");
        }
    }

    private Exhibit own(Session keeper, String idOrNo) {
        Exhibit exhibit = require(idOrNo);
        if (exhibit.getKeeperId() == null || !exhibit.getKeeperId().equals(keeper.getId())) {
            throw new BizException("这不是你埋下的那一段");
        }
        return exhibit;
    }

    private static void requireConfirmed(Boolean originalConfirmed) {
        if (!Boolean.TRUE.equals(originalConfirmed)) {
            throw new BizException("要先确认这段声音是你自己录的或自己创作的");
        }
    }

    private static void requireRecordedOn(LocalDate recordedOn) {
        if (recordedOn == null) {
            throw new BizException("这段声音是什么时候录的？");
        }
        if (recordedOn.isAfter(LocalDate.now())) {
            throw new BizException("录制日期不能是未来");
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

    /** 编号由数据库序列分配：删号不回头，并发不撞号 */
    public String nextNo() {
        return noAllocator.next();
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
