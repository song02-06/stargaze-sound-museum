package com.starmuseum.web;

import com.starmuseum.common.ApiResponse;
import com.starmuseum.domain.Exhibit;
import com.starmuseum.repo.ExhibitRepository;
import com.starmuseum.repo.HeritageSoundRepository;
import com.starmuseum.service.AuditService;
import com.starmuseum.service.EchoService;
import com.starmuseum.service.SignService;
import com.starmuseum.web.dto.Dtos;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class PingController {

    private final ExhibitRepository exhibits;
    private final HeritageSoundRepository heritage;
    private final EchoService echoService;
    private final AuditService audit;
    private final SignService signs;

    public PingController(ExhibitRepository exhibits, HeritageSoundRepository heritage,
                          EchoService echoService, AuditService audit, SignService signs) {
        this.exhibits = exhibits;
        this.heritage = heritage;
        this.echoService = echoService;
        this.audit = audit;
        this.signs = signs;
    }

    @GetMapping("/ping")
    public ApiResponse<Dtos.Stats> ping() {
        Dtos.Stats stats = new Dtos.Stats(
                exhibits.countByStatus(Exhibit.AuditStatus.PASS),
                heritage.count(),
                exhibits.countByStatus(Exhibit.AuditStatus.REVIEW),
                echoService.countPassed(),
                signs.provider());
        return ApiResponse.ok(stats);
    }

    /** 词表规模单独暴露出来，答辩时能直接指给人看 */
    @GetMapping("/ping/lexicon")
    public ApiResponse<java.util.Map<String, Integer>> lexicon() {
        return ApiResponse.ok(java.util.Map.of(
                "denyWords", audit.denyWordCount(),
                "reviewOnlyWords", audit.reviewWordCount()));
    }
}
