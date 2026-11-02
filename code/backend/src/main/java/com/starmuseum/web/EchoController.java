package com.starmuseum.web;

import com.starmuseum.common.ApiResponse;
import com.starmuseum.domain.Echo;
import com.starmuseum.domain.Exhibit;
import com.starmuseum.domain.Session;
import com.starmuseum.service.EchoService;
import com.starmuseum.service.ExhibitService;
import com.starmuseum.service.SessionService;
import com.starmuseum.web.dto.Dtos;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/exhibits/{idOrNo}/echoes")
public class EchoController {

    private final EchoService echoService;
    private final ExhibitService exhibitService;
    private final SessionService sessions;

    public EchoController(EchoService echoService, ExhibitService exhibitService,
                          SessionService sessions) {
        this.echoService = echoService;
        this.exhibitService = exhibitService;
        this.sessions = sessions;
    }

    /** 回音是公开的：任何人都能看到这段声音收到的全部回音 */
    @GetMapping
    public ApiResponse<List<Dtos.EchoView>> list(@PathVariable String idOrNo) {
        Exhibit exhibit = exhibitService.require(idOrNo);
        return ApiResponse.ok(echoService.listFor(exhibit.getId()));
    }

    /**
     * 一次接口同时处理文字回音和语音回音：
     * 有 file 就是语音，没有就是文字。前端不用判断走哪个端点。
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Dtos.EchoView> create(
            @RequestHeader("X-Session-Id") Long sessionId,
            @PathVariable String idOrNo,
            @RequestPart(value = "file", required = false) MultipartFile file,
            @RequestParam(required = false) String body) {

        Session author = sessions.require(sessionId);
        Exhibit exhibit = exhibitService.require(idOrNo);
        Echo echo = (file != null && !file.isEmpty())
                ? echoService.addVoice(author, exhibit, file)
                : echoService.addText(author, exhibit, body);
        return ApiResponse.ok(echoService.toView(echo));
    }
}
