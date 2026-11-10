package com.sma.admin.common;

/**
 * Expected business failure that should be reported to the caller as a readable message.
 */
public class BizException extends RuntimeException {

	private final int code;

	public BizException(String message) {
		this(400, message);
	}

	public BizException(int code, String message) {
		super(message);
		this.code = code;
	}

	public int getCode() {
		return code;
	}

}
