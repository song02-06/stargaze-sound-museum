package com.starmuseum.config;

import com.starmuseum.media.FileStorageService;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.concurrent.TimeUnit;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final FileStorageService storage;

    public WebConfig(FileStorageService storage) {
        this.storage = storage;
    }

    /**
     * 音频走静态资源映射，不自己写 Controller 返 byte[]。
     *
     * 原因很实在：Spring 的 ResourceHttpRequestHandler 自带 **HTTP Range** 支持，
     * 浏览器 <audio> 的进度条才能拖动。自己返回 byte[] 就得手写 206，
     * 十有八九会写错。
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/audio/**")
                .addResourceLocations(storage.root().toUri().toString())
                .setCachePeriod((int) TimeUnit.HOURS.toSeconds(6));
    }
}
