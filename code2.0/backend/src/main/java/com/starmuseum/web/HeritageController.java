package com.starmuseum.web;

import com.starmuseum.common.ApiResponse;
import com.starmuseum.service.HeritageService;
import com.starmuseum.web.dto.Dtos;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/heritage")
public class HeritageController {

    private final HeritageService heritage;

    public HeritageController(HeritageService heritage) {
        this.heritage = heritage;
    }

    @GetMapping
    public ApiResponse<List<Dtos.HeritageCard>> list() {
        return ApiResponse.ok(heritage.listAll());
    }

    @GetMapping("/{idOrSlug}")
    public ApiResponse<Dtos.HeritageCard> detail(@PathVariable String idOrSlug) {
        return ApiResponse.ok(heritage.toCard(heritage.require(idOrSlug)));
    }
}
