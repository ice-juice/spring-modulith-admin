package com.sma.admin.common.internal;

import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.sma.admin.common.ApiResponse;
import com.sma.admin.common.BizException;
import com.sma.admin.common.CommonErrorCode;
import com.sma.admin.common.ErrorCode;

/**
 * Translates exceptions into localized {@link ApiResponse} bodies. Business errors are HTTP 200 with a non-zero code;
 * validation 400, authorization 403, unknown 404, everything unexpected 500 without exposing details.
 */
@RestControllerAdvice
class GlobalExceptionHandler {
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    ResponseEntity<ApiResponse<Void>> handleUploadLimit(Exception ex){return status(CommonErrorCode.PAYLOAD_TOO_LARGE);}
    @ExceptionHandler(java.nio.file.NoSuchFileException.class)
    ResponseEntity<ApiResponse<Void>> handleMissingFile(Exception ex){return status(CommonErrorCode.NOT_FOUND);}

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	private final LocalizedErrors errors;

	GlobalExceptionHandler(LocalizedErrors errors) {
		this.errors = errors;
	}

	/**
	 * A field that failed validation, with its localized message.
	 */
	record FieldViolation(String field, String message) {
	}

	@ExceptionHandler(BizException.class)
	ResponseEntity<ApiResponse<Void>> handleBiz(BizException ex) {
		return ResponseEntity.ok(errors.body(ex.getErrorCode(), ex.getArgs(), locale(), null));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ApiResponse<List<FieldViolation>>> handleValidation(MethodArgumentNotValidException ex) {
		List<FieldViolation> violations = ex.getBindingResult().getFieldErrors().stream()
				.map(error -> new FieldViolation(error.getField(), message(error)))
				.toList();
		return validationFailed(violations);
	}

	@ExceptionHandler(HandlerMethodValidationException.class)
	ResponseEntity<ApiResponse<List<FieldViolation>>> handleMethodValidation(HandlerMethodValidationException ex) {
		List<FieldViolation> violations = ex.getParameterValidationResults().stream()
				.flatMap(result -> result.getResolvableErrors().stream()
						.map(error -> new FieldViolation(result.getMethodParameter().getParameterName(), error.getDefaultMessage())))
				.toList();
		return validationFailed(violations);
	}

	@ExceptionHandler({ HttpMessageNotReadableException.class, MissingServletRequestParameterException.class,
			MethodArgumentTypeMismatchException.class })
	ResponseEntity<ApiResponse<Void>> handleBadRequest(Exception ex) {
		return status(CommonErrorCode.BAD_REQUEST);
	}

	@ExceptionHandler(AccessDeniedException.class)
	ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
		return status(CommonErrorCode.FORBIDDEN);
	}

	@ExceptionHandler(NoResourceFoundException.class)
	ResponseEntity<ApiResponse<Void>> handleNotFound(NoResourceFoundException ex) {
		return status(CommonErrorCode.NOT_FOUND);
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	ResponseEntity<ApiResponse<Void>> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex) {
		return status(CommonErrorCode.METHOD_NOT_ALLOWED);
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
		log.error("Unhandled exception", ex);
		return status(CommonErrorCode.INTERNAL_ERROR);
	}

	private ResponseEntity<ApiResponse<List<FieldViolation>>> validationFailed(List<FieldViolation> violations) {
		return ResponseEntity.badRequest()
				.body(errors.body(CommonErrorCode.VALIDATION_FAILED, new Object[0], locale(), violations));
	}

	private ResponseEntity<ApiResponse<Void>> status(ErrorCode code) {
		return ResponseEntity.status(code.status()).body(errors.body(code, new Object[0], locale(), null));
	}

	private static String message(FieldError error) {
		return error.getDefaultMessage();
	}

	private static Locale locale() {
		return LocaleContextHolder.getLocale();
	}

}
