package com.sma.release.web;

public record ApiReply<T>(int code, String error, String message, T data) {
    public static <T> ApiReply<T> ok(T value) { return new ApiReply<>(0, null, "ok", value); }
    public static ApiReply<Void> error(int code, String error) { return new ApiReply<>(code, "extension." + error, error, null); }
}
