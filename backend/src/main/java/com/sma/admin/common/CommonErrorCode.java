package com.sma.admin.common;

/**
 * Errors shared by all modules.
 */
public enum CommonErrorCode implements ErrorCode {

	BAD_REQUEST("common.error.bad-request", 400),
	VALIDATION_FAILED("common.error.validation-failed", 400),
	DATA_NOT_FOUND("common.error.data-not-found", 400),
	DUPLICATE_DATA("common.error.duplicate-data", 400),
	UNAUTHORIZED("common.error.unauthorized", 401),
	FORBIDDEN("common.error.forbidden", 403),
	NOT_FOUND("common.error.not-found", 404),
	METHOD_NOT_ALLOWED("common.error.method-not-allowed", 405),
    CACHE_CONTROL_CONFLICT("common.error.cache-control-conflict",409),
    INTERNAL_ERROR("common.error.internal", 500),
    PAYLOAD_TOO_LARGE("common.error.payload-too-large",413),
    UNSUPPORTED_MEDIA_TYPE("common.error.unsupported-media-type",415),
    SERVICE_UNAVAILABLE("common.error.unavailable",503);

	private final String key;

	private final int status;

	CommonErrorCode(String key, int status) {
		this.key = key;
		this.status = status;
	}

	@Override
	public String key() {
		return key;
	}

	@Override
	public int status() {
		return status;
	}

}
