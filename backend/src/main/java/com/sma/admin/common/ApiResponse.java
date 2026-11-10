package com.sma.admin.common;

/**
 * Unified JSON response body returned by every REST endpoint.
 *
 * @param code    0 for success, otherwise a business or HTTP error code
 * @param message human readable message
 * @param data    payload, may be {@code null}
 */
public record ApiResponse<T>(int code, String message, T data) {

	public static final int OK = 0;

	public static <T> ApiResponse<T> ok(T data) {
		return new ApiResponse<>(OK, "ok", data);
	}

	public static ApiResponse<Void> ok() {
		return new ApiResponse<>(OK, "ok", null);
	}

	public static <T> ApiResponse<T> fail(int code, String message) {
		return new ApiResponse<>(code, message, null);
	}

}
