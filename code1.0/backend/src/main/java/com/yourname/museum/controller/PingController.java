package com.yourname.museum.controller;

import com.yourname.museum.common.ApiResponse;
import com.yourname.museum.service.AuditService;
import com.yourname.museum.service.DrawService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class PingController {

    private final AuditService auditService;
    private final DrawService drawService;

    @Value("${museum.asr.provider}")
    private String asrProvider;

    @Value("${museum.ai.provider}")
    private String aiProvider;

    public PingController(AuditService auditService, DrawService drawService) {
        this.auditService = auditService;
        this.drawService = drawService;
    }

    @GetMapping("/ping")
    public ApiResponse<Map<String, Object>> ping() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("msg", "pong");
        data.put("ts", System.currentTimeMillis());
        data.put("asrProvider", asrProvider);
        data.put("aiProvider", aiProvider);
        data.put("auditEngine", auditService.engineName());
        data.put("customWords", auditService.wordCount());
        data.put("draw", drawService.stats());
        return ApiResponse.ok(data);
    }
}
