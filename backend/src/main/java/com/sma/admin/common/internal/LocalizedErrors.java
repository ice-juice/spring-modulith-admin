package com.sma.admin.common.internal;

import java.io.IOException;
import java.util.Locale;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.context.MessageSource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.LocaleContextResolver;

import com.sma.admin.common.ApiResponse;
import com.sma.admin.common.ErrorCode;
import com.sma.admin.common.ErrorResponseWriter;

import tools.jackson.databind.json.JsonMapper;

/**
 * Resolves error messages in the request language and writes error bodies for code running outside Spring MVC.
 */
@Component
class LocalizedErrors implements ErrorResponseWriter {

	private final MessageSource messages;

	private final LocaleContextResolver localeResolver;

	private final JsonMapper json;

	LocalizedErrors(MessageSource messages, LocaleContextResolver localeResolver, JsonMapper json) {
		this.messages = messages;
		this.localeResolver = localeResolver;
		this.json = json;
	}

	String message(ErrorCode code, Object[] args, Locale locale) {
		return messages.getMessage(code.key(), args, code.key(), locale);
	}

	<T> ApiResponse<T> body(ErrorCode code, Object[] args, Locale locale, T data) {
		return ApiResponse.fail(code.status(), code.key(), message(code, args, locale), data);
	}

	@Override
	public void write(HttpServletRequest request, HttpServletResponse response, ErrorCode errorCode) throws IOException {
		Locale locale = localeResolver.resolveLocale(request);
		response.setStatus(errorCode.status());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		json.writeValue(response.getOutputStream(), body(errorCode, new Object[0], locale, null));
	}

}
