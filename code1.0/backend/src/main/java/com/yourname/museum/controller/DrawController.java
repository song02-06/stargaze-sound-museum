package com.yourname.museum.controller;

import com.yourname.museum.common.ApiResponse;
import com.yourname.museum.dto.DrawItem;
import com.yourname.museum.service.DrawService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class DrawController {

    private final DrawService drawService;

    public DrawController(DrawService drawService) {
        this.drawService = drawService;
    }

    /** 随机捞一段声音：可能是普通人的留言，也可能是历史声音彩蛋。 */
    @GetMapping("/draw")
    public ApiResponse<DrawItem> draw() {
        return ApiResponse.ok(drawService.draw());
    }
}
