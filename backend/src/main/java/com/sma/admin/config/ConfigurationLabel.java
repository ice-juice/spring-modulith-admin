package com.sma.admin.config;

import java.util.Map;

/** Explicit translations, then the module message key, then text, matching the existing Config naming contract. */
public record ConfigurationLabel(String text, String messageKey, Map<String,String> translations) {
    public ConfigurationLabel { translations = translations == null ? Map.of() : Map.copyOf(translations); }
}
