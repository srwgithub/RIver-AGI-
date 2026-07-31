package com.river.agi.common;

import java.util.UUID;

public class ApiResponse<T> {
    private final int code;
    private final String message;
    private final T data;
    private final String traceId;

    public ApiResponse(int code, String message, T data, String traceId) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.traceId = traceId;
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(200, "success", data, UUID.randomUUID().toString());
    }

    public static <T> ApiResponse<T> ok(T data, String traceId) {
        return new ApiResponse<>(200, "success", data, traceId);
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(400, message, null, UUID.randomUUID().toString());
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null, UUID.randomUUID().toString());
    }

    public static <T> ApiResponse<T> error(String message, String traceId) {
        return new ApiResponse<>(400, message, null, traceId);
    }

    public int getCode() { return code; }
    public String getMessage() { return message; }
    public T getData() { return data; }
    public String getTraceId() { return traceId; }
}
