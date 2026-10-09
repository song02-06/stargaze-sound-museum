package com.yourname.museum.common;

/** 可预期的业务异常，统一由 GlobalExceptionHandler 转成 ApiResponse。 */
public class BizException extends RuntimeException {

    private final int code;

    public BizException(String message) {
        this(4000, message);
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
