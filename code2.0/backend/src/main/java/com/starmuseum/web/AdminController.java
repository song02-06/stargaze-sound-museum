package com.starmuseum.web;

import com.starmuseum.common.ApiResponse;
import com.starmuseum.common.BizException;
import com.starmuseum.domain.AuditRecord;
import com.starmuseum.domain.Echo;
import com.starmuseum.domain.Exhibit;
import com.starmuseum.domain.TargetType;
import com.starmuseum.repo.AuditRecordRepository;
import com.starmuseum.repo.EchoRepository;
import com.starmuseum.repo.ExhibitRepository;
import com.starmuseum.web.dto.Dtos;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

// 人工复审队列。审核三路的最后一环。
// ⚠️ 这组接口现在没有鉴权 —— 上线前必须补，否则任何人都能替你做复审决定。
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final ExhibitRepository exhibits;
    private final EchoRepository echoes;
    private final AuditRecordRepository auditRecords;

    public AdminController(ExhibitRepository exhibits, EchoRepository echoes,
                           AuditRecordRepository auditRecords) {
        this.exhibits = exhibits;
        this.echoes = echoes;
        this.auditRecords = auditRecords;
    }

    @GetMapping("/queue")
    public ApiResponse<List<Dtos.PendingItem>> queue() {
        List<Dtos.PendingItem> out = new ArrayList<>();
        for (Exhibit e : exhibits.findByStatusOrderByCreatedAtDesc(Exhibit.AuditStatus.REVIEW)) {
            out.add(new Dtos.PendingItem(e.getId(), "exhibit", e.getTitle(), e.getFullText(),
                    e.getAuditReason(), e.getCreatedAt().format(ISO)));
        }
        for (Echo e : echoes.findByStatusOrderByCreatedAtAsc(Echo.AuditStatus.REVIEW)) {
            out.add(new Dtos.PendingItem(e.getId(), "echo",
                    "回音 · " + e.getAuthorName(),
                    e.getBody() == null ? "（语音回音）" : e.getBody(),
                    e.getAuditReason(),
                    e.getCreatedAt() == null ? "" : e.getCreatedAt().format(ISO)));
        }
        return ApiResponse.ok(out);
    }

    @PostMapping("/review/{type}/{id}")
    @Transactional
    public ApiResponse<String> review(@PathVariable String type, @PathVariable Long id,
                                      @RequestParam boolean pass,
                                      @RequestParam(defaultValue = "") String reason) {
        String operator = "admin";
        if ("exhibit".equalsIgnoreCase(type)) {
            Exhibit e = exhibits.findById(id).orElseThrow(() -> new BizException("没有这条展品"));
            if (pass) {
                e.approve();
            } else {
                e.reject(reason.isBlank() ? "人工复审未通过" : reason);
            }
            exhibits.save(e);
            record(TargetType.EXHIBIT, id, pass, reason, operator);
        } else if ("echo".equalsIgnoreCase(type)) {
            Echo e = echoes.findById(id).orElseThrow(() -> new BizException("没有这条回音"));
            if (pass) {
                e.approve();
            } else {
                e.reject(reason.isBlank() ? "人工复审未通过" : reason);
            }
            echoes.save(e);
            record(TargetType.ECHO, id, pass, reason, operator);
        } else {
            throw new BizException("未知的复审对象：" + type);
        }
        return ApiResponse.ok(pass ? "已通过" : "已驳回");
    }

    // 审核流水 —— 也是论文里审核对比实验的数据源
    @GetMapping("/audit-records")
    public ApiResponse<List<AuditRecord>> auditRecords() {
        return ApiResponse.ok(auditRecords.findTop200ByOrderByCreatedAtDesc());
    }

    private void record(TargetType type, Long id, boolean pass, String reason, String operator) {
        // 判定理由一定要写清楚，哪怕是通过。
        // 这张表同时是论文里审核对比实验的数据源 ——
        // 事后翻流水时，「谁在什么时候放行了什么」必须一眼看得见。
        String detail = pass
                ? (reason.isBlank() ? "人工复审：通过" : "人工复审：通过 · " + reason)
                : (reason.isBlank() ? "人工复审：驳回（未填原因）" : "人工复审：驳回 · " + reason);
        auditRecords.save(new AuditRecord(type, id, AuditRecord.Stage.MANUAL,
                pass ? AuditRecord.Verdict.PASS : AuditRecord.Verdict.REJECT,
                detail, 0L, operator));
    }
}
