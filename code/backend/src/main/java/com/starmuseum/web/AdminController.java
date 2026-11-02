package com.starmuseum.web;

import com.starmuseum.common.ApiResponse;
import com.starmuseum.domain.AuditRecord;
import com.starmuseum.domain.Echo;
import com.starmuseum.domain.Exhibit;
import com.starmuseum.repo.AuditRecordRepository;
import com.starmuseum.repo.EchoRepository;
import com.starmuseum.repo.ExhibitRepository;
import com.starmuseum.service.ReviewService;
import com.starmuseum.web.dto.Dtos;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

// 人工复审队列。审核的最后一人。
//
// 鉴权在 AdminAuthFilter 里：没配 ADMIN_TOKEN 就等于关闭管理口（安全默认）。
//
// 这里只做「取队列」和「把决定转给服务」—— 决定本身（改状态、写流水、发消息）
// 必须在 ReviewService 的同一个事务里发生，控制器不自己动仓储。
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final ExhibitRepository exhibits;
    private final EchoRepository echoes;
    private final AuditRecordRepository auditRecords;
    private final ReviewService review;

    public AdminController(ExhibitRepository exhibits, EchoRepository echoes,
                           AuditRecordRepository auditRecords, ReviewService review) {
        this.exhibits = exhibits;
        this.echoes = echoes;
        this.auditRecords = auditRecords;
        this.review = review;
    }

    /**
     * 待复审队列。
     *
     * 每一项都带 audioUrl / seconds：语音回音转写失败时只能靠听，
     * 队列里没有播放入口，这一项就没法审。
     * 展品额外带地点和录制日期，看内容时要能判断语境。
     */
    @GetMapping("/queue")
    public ApiResponse<List<Dtos.PendingItem>> queue() {
        List<Dtos.PendingItem> out = new ArrayList<>();
        for (Exhibit e : exhibits.findByStatusOrderByCreatedAtDesc(Exhibit.AuditStatus.REVIEW)) {
            out.add(new Dtos.PendingItem(
                    e.getId(), "exhibit", e.getSoundType().name(), e.getTitle(), e.getFullText(),
                    e.getAuditReason(), e.getPlace(),
                    e.getRecordedOn() == null ? "" : e.getRecordedOn().toString(),
                    "/audio/" + e.getAudioPath(), e.getSeconds(),
                    e.getCreatedAt() == null ? "" : e.getCreatedAt().format(ISO)));
        }
        for (Echo e : echoes.findByStatusOrderByCreatedAtAsc(Echo.AuditStatus.REVIEW)) {
            // 语音回音的正文来自转写；转写失败时正文是空的 ——
            // 那就如实说「（没有转写文本，请直接听）」，不要编一句内容出来。
            String text;
            if (e.getKind() == Echo.Kind.VOICE) {
                text = e.getBody() == null || e.getBody().isBlank()
                        ? "（没有转写文本，请直接听这段音频）"
                        : "（转写）" + e.getBody();
            } else {
                text = e.getBody();
            }
            out.add(new Dtos.PendingItem(
                    e.getId(), "echo", e.getKind().name(),
                    "回音 · " + e.getAuthorName(), text, e.getAuditReason(), "",
                    "",
                    e.getAudioPath() == null ? null : "/audio/" + e.getAudioPath(),
                    e.getSeconds(),
                    e.getCreatedAt() == null ? "" : e.getCreatedAt().format(ISO)));
        }
        return ApiResponse.ok(out);
    }

    /**
     * 通过 / 驳回。
     *
     * 驳回必须带原因 —— 服务层会校验，不填就报错，不会默默写成「未填原因」。
     * 只有仍在 REVIEW 状态的记录才能被决定；队列快照过期时提示刷新。
     */
    @PostMapping("/review/{type}/{id}")
    public ApiResponse<String> review(@PathVariable String type, @PathVariable Long id,
                                      @RequestParam boolean pass,
                                      @RequestParam(defaultValue = "") String reason) {
        if ("exhibit".equalsIgnoreCase(type)) {
            review.reviewExhibit(id, pass, reason);
        } else if ("echo".equalsIgnoreCase(type)) {
            review.reviewEcho(id, pass, reason);
        } else {
            throw new com.starmuseum.common.BizException("未知的复审对象：" + type);
        }
        return ApiResponse.ok(pass ? "已通过" : "已驳回");
    }

    // 审核流水 —— 也是论文里审核对比实验的数据源
    @GetMapping("/audit-records")
    public ApiResponse<List<AuditRecord>> auditRecords() {
        return ApiResponse.ok(auditRecords.findTop200ByOrderByCreatedAtDesc());
    }
}
