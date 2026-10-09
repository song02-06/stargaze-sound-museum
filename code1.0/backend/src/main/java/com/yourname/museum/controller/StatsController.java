package com.yourname.museum.controller;

import com.yourname.museum.common.ApiResponse;
import com.yourname.museum.repository.HeritageSoundRepository;
import com.yourname.museum.repository.VoiceBottleRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/** 馆藏概览。首页那句「星空里有 N 段声音」用的就是这里的数字。 */
@RestController
@RequestMapping("/api")
public class StatsController {

    private final VoiceBottleRepository bottleRepository;
    private final HeritageSoundRepository heritageRepository;

    public StatsController(VoiceBottleRepository bottleRepository,
                           HeritageSoundRepository heritageRepository) {
        this.bottleRepository = bottleRepository;
        this.heritageRepository = heritageRepository;
    }

    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> stats() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("pass", bottleRepository.countByAuditStatus("PASS"));
        data.put("review", bottleRepository.countByAuditStatus("REVIEW"));
        data.put("reject", bottleRepository.countByAuditStatus("REJECT"));
        data.put("pending", bottleRepository.countByAuditStatus("PENDING"));
        data.put("heritage", heritageRepository.findByEnabledTrue().size());
        data.put("total", bottleRepository.count());
        return ApiResponse.ok(data);
    }
}
