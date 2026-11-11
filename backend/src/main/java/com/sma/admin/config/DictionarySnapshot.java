package com.sma.admin.config;

import java.util.List;
import java.util.Optional;

/** Historical labels and current submission eligibility are deliberately separate. */
public record DictionarySnapshot(String key, DictionaryDefinition.Mode mode, ConfigurationRevision revision,
        List<Entry> entries, List<DictionaryOption> options) {
    public record Entry(String code, String parentCode, String label, boolean enabled, boolean hidden,
            boolean selectable, int sort, ConfigurationOrigin origin) {}
    public DictionarySnapshot { entries = List.copyOf(entries); options = List.copyOf(options); }
    public Optional<Entry> find(String code) { return entries.stream().filter(item -> item.code().equals(code)).findFirst(); }
    public boolean accepts(String code) { return find(code).map(Entry::selectable).orElse(false); }
}
