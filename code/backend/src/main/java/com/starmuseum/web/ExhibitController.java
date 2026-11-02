package com.starmuseum.web;

import com.starmuseum.common.ApiResponse;
import com.starmuseum.common.BizException;
import com.starmuseum.domain.Exhibit;
import com.starmuseum.domain.Session;
import com.starmuseum.service.ExhibitService;
import com.starmuseum.service.SessionService;
import com.starmuseum.web.dto.Dtos;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/exhibits")
public class ExhibitController {

    /**
     * 编辑请求体。
     * 只带用户能改的那几样：标题、故事、地点、录制日期、分类、原创声明。
     * 音频、编号、作者都不在这里 —— 那些不属于「改一改」。
     */
    public record EditRequest(String title, String full, String place, LocalDate recordedOn,
                              String soundType, Boolean originalConfirmed) {
    }

    private final ExhibitService exhibits;
    private final SessionService sessions;

    public ExhibitController(ExhibitService exhibits, SessionService sessions) {
        this.exhibits = exhibits;
        this.sessions = sessions;
    }

    /**
     * 卡片列表：只有展签，不含全文。
     * `q` 是可选关键词，覆盖标题、故事、地点；只筛已通过的内容。
     */
    @GetMapping
    public ApiResponse<List<Dtos.ExhibitCard>> list(
            @RequestParam(required = false) String q) {
        return ApiResponse.ok(exhibits.passedCards(q));
    }

    @GetMapping("/recent")
    public ApiResponse<List<Dtos.ExhibitCard>> recent(@RequestParam(defaultValue = "3") int limit) {
        return ApiResponse.ok(exhibits.recentPassed(Math.min(Math.max(limit, 1), 20)));
    }

    /** 详情：这里才带全文。展签与全文分两个接口拿，前端就没法「并排对比」 */
    @GetMapping("/{idOrNo}")
    public ResponseEntity<ApiResponse<Dtos.ExhibitDetail>> detail(@PathVariable String idOrNo) {
        Exhibit exhibit = exhibits.require(idOrNo);
        if (exhibit.getStatus() != Exhibit.AuditStatus.PASS) {
            // 未通过审核的内容不公开。这里给真正的 404，
            // 而不是 200 里塞一个 404 的 code —— 缓存、代理、日志都按 HTTP 状态判断。
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.fail(404, "这一段还没有通过审核"));
        }
        return ResponseEntity.ok(ApiResponse.ok(exhibits.toDetail(exhibit)));
    }

    /**
     * 编辑自己埋下的一段。
     *
     * 这是 PATCH 而不是 POST：改的是同一件东西，不新增记录。
     * 编号、作者、音频、创建时间都不变；改完重新审核。
     */
    @PatchMapping("/{idOrNo}")
    public ApiResponse<Dtos.ExhibitDetail> edit(
            @RequestHeader("X-Session-Id") Long sessionId,
            @PathVariable String idOrNo,
            @RequestBody EditRequest request) {
        Session keeper = sessions.require(sessionId);
        Exhibit updated = exhibits.update(keeper, idOrNo,
                request.title(), request.full(), request.place(), request.recordedOn(),
                parseSoundType(request.soundType()), request.originalConfirmed());
        return ApiResponse.ok(exhibits.toDetail(updated));
    }

    /**
     * 删掉自己埋下的一段。只有留下它的人能删 —— 服务端认的是会话，不是请求里写了什么。
     * 响应里带回「一起删掉了几条公开回音」，前端要把这件事说在删除之前。
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
     * 10–60 秒是硬约束，硬约束必须由服务端裁决。
     *
     * soundType / originalConfirmed 都要落到库里：
     * 产品页面写了「演唱类会转人工」「必须是自己录的」，
     * 那这两件事就不能只停在前端。
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Dtos.ExhibitDetail> upload(
            @RequestHeader("X-Session-Id") Long sessionId,
            @RequestPart("file") MultipartFile file,
            @RequestParam String title,
            @RequestParam String full,
            @RequestParam String place,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate recordedOn,
            @RequestParam(required = false) String sign,
            @RequestParam(required = false) String soundType,
            @RequestParam(required = false) Boolean originalConfirmed) {

        Session keeper = sessions.require(sessionId);
        Exhibit created = exhibits.create(keeper, file, title, full, place, recordedOn, sign,
                parseSoundType(soundType), originalConfirmed);
        return ApiResponse.ok(exhibits.toDetail(created));
    }

    /** 未知分类直接拒收，不要默默当成「环境声」 */
    static Exhibit.SoundType parseSoundType(String raw) {
        if (raw == null || raw.isBlank()) {
            return Exhibit.SoundType.ENVIRONMENT;
        }
        try {
            return Exhibit.SoundType.valueOf(raw.strip().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BizException("未知的投稿分类：" + raw);
        }
    }
}
