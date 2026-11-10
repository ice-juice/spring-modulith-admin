package com.sma.admin.common.internal;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.web.servlet.LocaleContextResolver;

/**
 * i18n infrastructure: every module ships its own bundle at {@code i18n/<module>/messages*.properties}; all bundles
 * found on the classpath are registered automatically, so adding a module needs no change here.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(I18nProperties.class)
class I18nConfiguration {

	static final String BUNDLE_PATTERN = "classpath*:i18n/*/messages*.properties";

	private static final Pattern MODULE_DIR = Pattern.compile("/i18n/([a-z]+)/messages[^/]*\\.properties$");

	@Bean
	MessageSource messageSource() {
		ReloadableResourceBundleMessageSource source = new ReloadableResourceBundleMessageSource();
		source.setBasenames(moduleBasenames());
		source.setDefaultEncoding("UTF-8");
		source.setFallbackToSystemLocale(false);
		source.setAlwaysUseMessageFormat(false);
		return source;
	}

	@Bean
	LocaleContextResolver localeResolver(I18nProperties properties) {
		return new HeaderLocaleContextResolver(properties);
	}

	static String[] moduleBasenames() {
		try {
			Resource[] resources = new PathMatchingResourcePatternResolver().getResources(BUNDLE_PATTERN);
			TreeSet<String> basenames = new TreeSet<>();
			Arrays.stream(resources).forEach(resource -> {
				try {
					Matcher matcher = MODULE_DIR.matcher(resource.getURL().toString());
					if (matcher.find()) {
						basenames.add("classpath:i18n/" + matcher.group(1) + "/messages");
					}
				}
				catch (IOException ex) {
					throw new UncheckedIOException(ex);
				}
			});
			return basenames.toArray(String[]::new);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

}
