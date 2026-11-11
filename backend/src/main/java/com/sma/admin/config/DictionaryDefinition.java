package com.sma.admin.config;

import java.util.List;

/** Stable codes and parent codes are exact strings, independent of labels and locale. */
public record DictionaryDefinition(String owner, String key, ConfigurationLabel label, Mode mode, boolean tree, List<Item> items) {
    public enum Mode { PLATFORM_LOCKED, EXTENDABLE, TENANT_ONLY }
    public record Item(String code, String parentCode, ConfigurationLabel label, boolean enabled, int sort) {}
    public DictionaryDefinition { items = items == null ? List.of() : List.copyOf(items); }
}
