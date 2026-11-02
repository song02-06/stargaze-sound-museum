package com.starmuseum.web;

import com.starmuseum.common.ApiResponse;
import com.starmuseum.sign.SignGuard;
import com.starmuseum.web.dto.Dtos;
import org.springframework.web.bind.annotation.*;

/**
 * 展签守卫的自检接口。
 *
 * 存在的理由：产品定义里「AI 只做减法」是一句承诺，
 * 承诺要能被当场验证才有意义 —— 这个接口让你（或者答辩老师）
 * 随手丢一段被改坏的展签进来，看它是不是真的会被拦下。
 *
 * 例：给「外婆家屋檐下的雨」编一句原文没有的话，这里会报出具体是哪几个字。
 */
@RestController
@RequestMapping("/api/sign")
public class SignController {

    public record CheckRequest(String full, String sign) {
    }

    @PostMapping("/check")
    public ApiResponse<Dtos.SignTrace> check(@RequestBody CheckRequest request) {
        SignGuard.Result result = SignGuard.check(request.full(), request.sign());
        return ApiResponse.ok(new Dtos.SignTrace(
                request.sign(), "guard", result.ok(), false, result.violations()));
    }
}
