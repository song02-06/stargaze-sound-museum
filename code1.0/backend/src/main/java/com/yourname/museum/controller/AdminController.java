package com.yourname.museum.controller;

import com.yourname.museum.common.ApiResponse;
import com.yourname.museum.dto.BottleDetail;
import com.yourname.museum.entity.AuditRecord;
import com.yourname.museum.repository.AuditRecordRepository;
import com.yourname.museum.service.BottleService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 管理后台：人工复审队列 + 审核流水（论文实验二的数据来源）。 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final BottleService bottleService;
    private final AuditRecordRepository auditRecordRepository;

    public AdminController(BottleService bottleService, AuditRecordRepository auditRecordRepository) {
        this.bottleService = bottleService;
        this.auditRecordRepository = auditRecordRepository;
    }

    @GetMapping("/queue")
    public ApiResponse<List<BottleDetail>> queue() {
        return ApiResponse.ok(bottleService.reviewQueue());
    }

    @GetMapping("/bottles")
    public ApiResponse<List<BottleDetail>> byStatus(@RequestParam(defaultValue = "PASS") String status) {
        return ApiResponse.ok(bottleService.byStatus(status));
    }

    @PostMapping("/review/{id}")
    public ApiResponse<BottleDetail> review(@PathVariable Long id,
                                            @RequestParam boolean pass,
                                            @RequestParam(required = false) String reason) {
        return ApiResponse.ok(bottleService.review(id, pass, reason));
    }

    @GetMapping("/audit-records")
    public ApiResponse<List<AuditRecord>> records() {
        return ApiResponse.ok(auditRecordRepository.findTop50ByOrderByCreatedAtDesc());
    }
}
