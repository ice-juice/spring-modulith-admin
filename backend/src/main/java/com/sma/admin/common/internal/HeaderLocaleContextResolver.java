package com.sma.admin.common.internal;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.context.i18n.LocaleContext;
import org.springframework.context.i18n.SimpleTimeZoneAwareLocaleContext;
import org.springframework.web.servlet.LocaleContextResolver;

/**
 * Stateless locale and time zone resolution: the SPA decides the language and time zone of the user and sends them
 * with every request ({@code Accept-Language}, {@code X-Time-Zone}). Unsupported values fall back to the defaults.
 */
class HeaderLocaleContextResolver implements LocaleContextResolver {

	static final String TIME_ZONE_HEADER = "X-Time-Zone";

	private final List<Locale> supported;

	private final Locale defaultLocale;

	private final TimeZone defaultTimeZone;

	HeaderLocaleContextResolver(I18nProperties properties) {
		this.supported = properties.supportedLocales();
		this.defaultLocale = properties.defaultLocale();
		this.defaultTimeZone = TimeZone.getTimeZone(ZoneId.of(properties.defaultTimeZone()));
	}

	@Override
	public LocaleContext resolveLocaleContext(HttpServletRequest request) {
		return new SimpleTimeZoneAwareLocaleContext(resolveLocale(request), resolveTimeZone(request));
	}

	@Override
	public Locale resolveLocale(HttpServletRequest request) {
		String header = request.getHeader("Accept-Language");
		if (header == null || header.isBlank()) {
			return defaultLocale;
		}
		try {
			List<Locale.LanguageRange> ranges = Locale.LanguageRange.parse(header);
			Locale exact = Locale.lookup(ranges, supported);
			if (exact != null) {
				return exact;
			}
			for (Locale.LanguageRange range : ranges) {
				String language = Locale.forLanguageTag(range.getRange()).getLanguage();
				for (Locale candidate : supported) {
					if (candidate.getLanguage().equals(language)) {
						return candidate;
					}
				}
			}
		}
		catch (IllegalArgumentException ex) {
			// malformed header: use the default
		}
		return defaultLocale;
	}

	TimeZone resolveTimeZone(HttpServletRequest request) {
		String header = request.getHeader(TIME_ZONE_HEADER);
		if (header == null || header.isBlank()) {
			return defaultTimeZone;
		}
		try {
			return TimeZone.getTimeZone(ZoneId.of(header.trim()));
		}
		catch (DateTimeException ex) {
			return defaultTimeZone;
		}
	}

	@Override
	public void setLocaleContext(HttpServletRequest request, HttpServletResponse response, LocaleContext localeContext) {
		throw new UnsupportedOperationException("Locale and time zone are sent by the client with every request");
	}

	@Override
	public void setLocale(HttpServletRequest request, HttpServletResponse response, Locale locale) {
		throw new UnsupportedOperationException("Locale and time zone are sent by the client with every request");
	}

}
