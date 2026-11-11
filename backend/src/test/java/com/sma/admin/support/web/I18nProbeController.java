package com.sma.admin.support.web;

import java.time.Instant;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sma.admin.common.ApiResponse;
import com.sma.admin.common.BizException;
import com.sma.admin.common.CommonErrorCode;
import com.sma.admin.common.security.PermitLoggedIn;

/**
 * Test-only endpoints exercising the i18n and time zone infrastructure (not part of the application).
 */
@RestController
@RequestMapping("/api/test-probe")
@PermitLoggedIn
public class I18nProbeController {

	public record ProbeRequest(@NotBlank(message = "{common.validation.not-blank}") String name) {
	}

	@GetMapping("/biz")
	public ApiResponse<Void> biz() {
		throw new BizException(CommonErrorCode.DATA_NOT_FOUND);
	}

    @GetMapping("/cache-busy")
    public ApiResponse<Void> cacheBusy() { throw new com.sma.admin.common.cache.CacheUnavailableException(); }

	@GetMapping("/duplicate")
	public ApiResponse<Void> duplicate() {
		throw new BizException(CommonErrorCode.DUPLICATE_DATA, "admin");
	}

	@PostMapping("/validate")
	public ApiResponse<String> validate(@Valid @RequestBody ProbeRequest request) {
		return ApiResponse.ok(request.name());
	}

	@GetMapping("/context")
	public ApiResponse<Map<String, Object>> context() {
		return ApiResponse.ok(Map.of(
				"locale", LocaleContextHolder.getLocale().toLanguageTag(),
				"zone", LocaleContextHolder.getTimeZone().getID(),
				"instant", Instant.parse("2026-11-10T03:00:00Z")));
	}

}
