package com.starmuseum.sign;

/** 把用户手写的全文压成一句展签。实现必须只做减法。 */
public interface SignCompressor {

    /** @return 展签文本；调用方会用 SignGuard 复核，不合规就退回安全实现 */
    String compress(String fullText, String title, int maxChars);

    String provider();
}
