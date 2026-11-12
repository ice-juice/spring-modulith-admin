package com.sma.admin.common;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Writes a localized {@link ApiResponse} error outside of Spring MVC, e.g. from security filters.
 */
public interface ErrorResponseWriter {

	void write(HttpServletRequest request, HttpServletResponse response, ErrorCode errorCode) throws IOException;

    /** Trusted fixed observation at a known rejection point. Existing writers retain identical wire behavior. */
    default void writeObserved(HttpServletRequest request,HttpServletResponse response,ErrorCode errorCode,
            com.sma.admin.common.security.SecuritySignal.Fact fact)throws IOException {write(request,response,errorCode);}

}
