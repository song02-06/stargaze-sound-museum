package com.yourname.museum.client;

public record AsrResult(String text, boolean success, String error, int costMs) {

    public static AsrResult ok(String text, int costMs) {
        return new AsrResult(text, true, null, costMs);
    }

    public static AsrResult fail(String error, int costMs) {
        return new AsrResult(null, false, error, costMs);
    }
}
