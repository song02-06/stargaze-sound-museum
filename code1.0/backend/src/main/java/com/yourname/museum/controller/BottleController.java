package com.yourname.museum.controller;

import com.yourname.museum.common.ApiResponse;
import com.yourname.museum.dto.BottleDetail;
import com.yourname.museum.service.BottleService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/bottle")
public class BottleController {

    private final BottleService bottleService;

    public BottleController(BottleService bottleService) {
        this.bottleService = bottleService;
    }

    /**
     * 上传一段声音。
     *
     * @param file       16k 单声道 WAV（前端已转码）
     * @param note       用户自己写的那句话，可为空
     * @param durationMs 录音时长，前端 MediaRecorder 侧计算
     */
    @PostMapping
    public ApiResponse<BottleDetail> upload(@RequestParam("file") MultipartFile file,
                                            @RequestParam(value = "note", required = false) String note,
                                            @RequestParam(value = "durationMs", required = false) Integer durationMs) {
        return ApiResponse.ok(bottleService.upload(file, note, durationMs));
    }

    @GetMapping("/{id}")
    public ApiResponse<BottleDetail> get(@PathVariable Long id) {
        return ApiResponse.ok(bottleService.get(id));
    }

    @GetMapping("/recent")
    public ApiResponse<List<BottleDetail>> recent() {
        return ApiResponse.ok(bottleService.recent());
    }
}
