package com.sma.admin.common;

import java.util.Arrays;

/**
 * Expected business failure. Only constructed from an {@link ErrorCode} plus message arguments, so no user facing text
 * is hard coded (constraint I1); the message is resolved in the request language by the global exception handler.
 */
public class BizException extends RuntimeException {

	private final ErrorCode errorCode;

	private final transient Object[] args;

	public BizException(ErrorCode errorCode, Object... args) {
		super(errorCode.key() + (args.length == 0 ? "" : " " + Arrays.toString(args)));
		this.errorCode = errorCode;
		this.args = args.clone();
	}

	public ErrorCode getErrorCode() {
		return errorCode;
	}

	public Object[] getArgs() {
		return args.clone();
	}

}
