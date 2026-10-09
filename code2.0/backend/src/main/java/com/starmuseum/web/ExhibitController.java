package com.starmuseum.web;

import com.starmuseum.common.ApiResponse;
import com.starmuseum.domain.Exhibit;
import com.starmuseum.domain.Session;
import com.starmuseum.service.ExhibitService;
import com.starmuseum.service.SessionService;
import com.starmuseum.web.dto.Dtos;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/exhibits")
public class ExhibitController {

    private final ExhibitService exhibits;
    private final SessionService sessions;

    public ExhibitController(ExhibitService exhibits, SessionService sessions) {
        this.exhibits = exhibits;
        this.sessions = sessions;
    }

    /** 卡片列表：只有展签，不含全文 */
    @GetMapping
    public ApiResponse<List<Dtos.ExhibitCard>> list() {
        return ApiResponse.ok(exhibits.passedCards());
    }

    @GetMapping("/recent")
    public ApiResponse<List<Dtos.ExhibitCard>> recent(@RequestParam(defaultValue = "3") int limit) {
        return ApiResponse.ok(exhibits.recentPassed(Math.min(Math.max(limit, 1), 20)));
    }

    /** 详情：这里才带全文。展签与全文分两个接口拿，前端就没法「并排对比」 */
    @GetMapping("/{idOrNo}")
    public ApiResponse<Dtos.ExhibitDetail> detail(@PathVariable String idOrNo) {
        Exhibit exhibit = exhibits.require(idOrNo);
        if (exhibit.getStatus() != Exhibit.AuditStatus.PASS) {
            return ApiResponse.fail(404, "这一段还没有通过审核");
        }
        return ApiResponse.ok(exhibits.toDetail(exhibit));
    }

    /**
     * 删掉自己埋下的一段。只有留下它的人能删 —— 服务端认的是会话，不是请求里写了什么。
     * 响应里带回「一起删掉了几条回音」，前端要把这件事说在删除之前。
     */
    @DeleteMapping("/{idOrNo}")
    public ApiResponse<Map<String, Object>> delete(
            @RequestHeader("X-Session-Id") Long sessionId,
            @PathVariable String idOrNo) {
        Session keeper = sessions.require(sessionId);
        long removedEchoes = exhibits.delete(keeper, idOrNo);
        return ApiResponse.ok(Map.of("removedEchoes", removedEchoes));
    }

    /**
     * 上传。音频时长由服务端从 WAV 里读，客户端报的数字不作数。
     * 15–60 秒是硬约束，硬约束必须由服务端裁决。
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Dtos.ExhibitDetail> upload(
            @RequestHeader("X-Session-Id") Long sessionId,
            @RequestPart("file") MultipartFile file,
            @RequestParam String title,
            @RequestParam String full,
            @RequestParam String place,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate recordedOn,
            @RequestParam(required = false) String sign) {

        Session keeper = sessions.require(sessionId);
        Exhibit created = exhibits.create(keeper, file, title, full, place, recordedOn, sign);
        return ApiResponse.ok(exhibits.toDetail(created));
    }
}
