package com.starmuseum.asr;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AsrConfig {

    @Bean
    @ConditionalOnProperty(name = "museum.asr.provider", havingValue = "baidu")
    public AsrClient baiduAsrClient(
            @Value("${museum.asr.baidu.app-id:}") String appId,
            @Value("${museum.asr.baidu.api-key:}") String apiKey,
            @Value("${museum.asr.baidu.secret-key:}") String secretKey,
            @Value("${museum.asr.baidu.dev-pid:1537}") int devPid) {
        return new BaiduAsrClient(appId, apiKey, secretKey, devPid);
    }

    @Bean
    @ConditionalOnProperty(name = "museum.asr.provider", havingValue = "mock", matchIfMissing = true)
    public AsrClient mockAsrClient() {
        return new MockAsrClient();
    }
}
