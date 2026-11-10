package com.sma.admin.common.internal;

import java.util.List;
import java.util.Locale;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code sma.i18n.*}: supported languages, default language and default time zone of requests.
 */
@ConfigurationProperties("sma.i18n")
record I18nProperties(List<Locale> supportedLocales, Locale defaultLocale, String defaultTimeZone) {

	I18nProperties {
		supportedLocales = supportedLocales == null || supportedLocales.isEmpty()
				? List.of(Locale.forLanguageTag("zh-CN"), Locale.forLanguageTag("en-US")) : List.copyOf(supportedLocales);
		defaultLocale = defaultLocale == null ? supportedLocales.get(0) : defaultLocale;
		defaultTimeZone = defaultTimeZone == null || defaultTimeZone.isBlank() ? "Asia/Shanghai" : defaultTimeZone;
	}

}
