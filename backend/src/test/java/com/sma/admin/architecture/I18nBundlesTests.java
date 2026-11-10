package com.sma.admin.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.modulith.core.ApplicationModules;

import com.sma.admin.SpringbootModulithAdminApplication;
import com.sma.admin.common.ErrorCode;
import com.tngtech.archunit.core.domain.JavaClass;

/**
 * Language bundle rules (README constraints I2-I4): bundles live in {@code i18n/<module>/}, keys start with the module
 * id, every language has exactly the same keys and placeholders, and every {@link ErrorCode} has a message.
 */
class I18nBundlesTests {

	static final List<String> LANGUAGES = List.of("messages.properties", "messages_en.properties");

	private static final Pattern BUNDLE = Pattern.compile("/i18n/([^/]+)/(messages[^/]*\\.properties)$");

	private static final Pattern PLACEHOLDER = Pattern.compile("\\{[^}]+}");

	/** module id -> file name -> properties */
	private final Map<String, Map<String, Properties>> bundles = loadBundles();

	@Test
	void bundlesBelongToModulesAndKeysArePrefixed() {
		Set<String> moduleIds = new HashSet<>();
		ApplicationModules.of(SpringbootModulithAdminApplication.class)
				.forEach(module -> moduleIds.add(module.getIdentifier().toString()));
		List<String> problems = new ArrayList<>();
		bundles.forEach((module, files) -> {
			if (!moduleIds.contains(module)) {
				problems.add("i18n/" + module + " does not match an application module");
			}
			files.forEach((file, props) -> props.stringPropertyNames().stream()
					.filter(key -> !key.startsWith(module + "."))
					.forEach(key -> problems.add("i18n/" + module + "/" + file + ": key '" + key + "' must start with '" + module + ".'")));
		});
		assertThat(problems).isEmpty();
	}

	@Test
	void everyLanguageHasTheSameKeysAndPlaceholders() {
		List<String> problems = new ArrayList<>();
		bundles.forEach((module, files) -> {
			for (String language : LANGUAGES) {
				if (!files.containsKey(language)) {
					problems.add("i18n/" + module + " is missing " + language);
				}
			}
			Properties reference = files.get(LANGUAGES.get(0));
			if (reference == null) {
				return;
			}
			files.forEach((file, props) -> {
				Set<String> missing = new TreeSet<>(reference.stringPropertyNames());
				missing.removeAll(props.stringPropertyNames());
				Set<String> extra = new TreeSet<>(props.stringPropertyNames());
				extra.removeAll(reference.stringPropertyNames());
				missing.forEach(key -> problems.add("i18n/" + module + "/" + file + " lacks " + key));
				extra.forEach(key -> problems.add("i18n/" + module + "/" + file + " has extra " + key));
				reference.stringPropertyNames().stream().filter(props::containsKey).forEach(key -> {
					if (!placeholders(reference.getProperty(key)).equals(placeholders(props.getProperty(key)))) {
						problems.add("i18n/" + module + "/" + file + ": placeholders of " + key + " differ");
					}
				});
			});
		});
		assertThat(problems).isEmpty();
	}

	@Test
	void everyErrorCodeHasAMessageInEveryLanguage() {
		List<String> problems = new ArrayList<>();
		for (JavaClass type : ArchitectureRulesTests.CLASSES) {
			if (!type.isEnum() || !type.isAssignableTo(ErrorCode.class)) {
				continue;
			}
			String module = ArchitectureRulesTests.moduleOf(type);
			for (Object constant : type.reflect().getEnumConstants()) {
				String key = ((ErrorCode) constant).key();
				if (!key.startsWith(module + ".")) {
					problems.add(type.getSimpleName() + "." + constant + ": key '" + key + "' must start with '" + module + ".'");
				}
				Map<String, Properties> files = bundles.getOrDefault(module, Map.of());
				for (String language : LANGUAGES) {
					Properties props = files.get(language);
					if (props == null || !props.containsKey(key)) {
						problems.add(type.getSimpleName() + "." + constant + ": '" + key + "' missing in i18n/" + module + "/" + language);
					}
				}
			}
		}
		assertThat(problems).isEmpty();
	}

	static Set<String> placeholders(String message) {
		Matcher matcher = PLACEHOLDER.matcher(message);
		Set<String> result = new TreeSet<>();
		while (matcher.find()) {
			result.add(matcher.group());
		}
		return result;
	}

	static Map<String, Map<String, Properties>> loadBundles() {
		try {
			Map<String, Map<String, Properties>> result = new TreeMap<>();
			for (Resource resource : new PathMatchingResourcePatternResolver().getResources("classpath*:i18n/*/messages*.properties")) {
				Matcher matcher = BUNDLE.matcher(resource.getURL().toString());
				if (!matcher.find()) {
					continue;
				}
				Properties props = new Properties();
				try (Reader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
					props.load(reader);
				}
				result.computeIfAbsent(matcher.group(1), key -> new TreeMap<>()).put(matcher.group(2), props);
			}
			return result.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, TreeMap::new));
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
