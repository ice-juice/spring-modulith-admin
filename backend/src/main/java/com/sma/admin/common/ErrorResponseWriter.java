package com.sma.admin.common;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Writes a localized {@link ApiResponse} error outside of Spring MVC, e.g. from security filters.
 */
public interface ErrorResponseWriter {

	void write(HttpServletRequest request, HttpServletResponse response, ErrorCode errorCode) throws IOException;

}
