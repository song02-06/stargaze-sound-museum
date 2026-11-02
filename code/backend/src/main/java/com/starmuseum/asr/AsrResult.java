package com.starmuseum.asr;

import java.util.List;

/**
 * 一次转写的结果。
 *
 * ok=false 时 text 一定是空的 —— 调用方必须显式处理失败，
 * 不能把 null 当成「没说话」蒙混过去。
 */
public record AsrResult(boolean ok, String text, String error, List<String> words, int costMs) {

    public static AsrResult ok(String text, List<String> words, int costMs) {
        return new AsrResult(true, text == null ? "" : text.strip(), null,
                words == null ? List.of() : words, costMs);
    }

    public static AsrResult empty(int costMs) {
        return new AsrResult(true, "", null, List.of(), costMs);
    }

    public static AsrResult fail(String error, int costMs) {
        return new AsrResult(false, "", error, List.of(), costMs);
    }

    /** 成功但没识别出任何字 —— 多半是环境声或纯噪声，这是正常结果，不是失败 */
    public boolean recognizedNothing() {
        return ok && text.isBlank();
    }
}
