package com.yourname.museum.common;

/**
 * 统一返回体。约定：code == 0 表示成功，非 0 为业务错误码。
 */
public record ApiResponse<T>(int code, String msg, T data) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "ok", data);
    }

    public static <T> ApiResponse<T> ok() {
        return new ApiResponse<>(0, "ok", null);
    }

    public static <T> ApiResponse<T> fail(int code, String msg) {
        return new ApiResponse<>(code, msg, null);
    }
}
