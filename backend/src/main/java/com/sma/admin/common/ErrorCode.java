package com.sma.admin.common;

/**
 * A business error that can be reported to the caller. Every module defines its errors as an enum implementing this
 * interface; the key is both the stable error identifier and the i18n message key (it must start with the module id
 * and exist in all language bundles, enforced by {@code I18nBundlesTests}).
 */
public interface ErrorCode {

	/**
	 * Message key, e.g. {@code iam.user.username-exists}.
	 */
	String key();

	/**
	 * Response category written to {@link ApiResponse#code()}; business errors default to 400.
	 */
	default int status() {
		return 400;
	}

}
