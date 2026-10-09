package com.yourname.museum.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 把本地 data/audio 目录暴露成 /audio/**。
 *
 * <p>注意：Spring 的静态资源默认就支持 Range 请求（拖进度条靠它），
 * 所以这里不需要自己写 Range 处理。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${museum.audio-root}")
    private String audioRoot;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path dir = Paths.get(audioRoot).toAbsolutePath().normalize();
        String location = dir.toUri().toString();
        if (!location.endsWith("/")) {
            location = location + "/";
        }
        registry.addResourceHandler("/audio/**")
                .addResourceLocations(location)
                .setCachePeriod(3600);
    }

    /** 开发期方便：前端 5173 直接请求 8080。生产用 vite 代理或同源部署都行。 */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }
}
