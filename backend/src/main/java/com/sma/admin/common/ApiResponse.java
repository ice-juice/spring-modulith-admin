package com.sma.admin.common;

/**
 * Unified JSON response body returned by every REST endpoint.
 *
 * @param code    {@code 0} on success, otherwise an HTTP-like category: 400 business/validation, 401, 403, 404, 500
 * @param error   stable, machine readable error key (also the i18n message key), {@code null} on success
 * @param message human readable message in the request language
 * @param data    payload, may be {@code null}
 */
public record ApiResponse<T>(int code, String error, String message, T data) {

	public static final int OK = 0;

	public static <T> ApiResponse<T> ok(T data) {
		return new ApiResponse<>(OK, null, "ok", data);
	}

	public static ApiResponse<Void> ok() {
		return new ApiResponse<>(OK, null, "ok", null);
	}

	public static <T> ApiResponse<T> fail(int code, String error, String message, T data) {
		return new ApiResponse<>(code, error, message, data);
	}

	public boolean success() {
		return code == OK;
	}

}
