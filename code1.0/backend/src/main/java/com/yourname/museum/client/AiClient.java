package com.yourname.museum.client;

/** 文本润色 + 适宜性判定。实现方必须自己保证「不抛异常」，失败时返回 AiResult.fallback。 */
public interface AiClient {

    AiResult polish(String rawText, String originalNote);

    String providerName();

    String modelName();
}
