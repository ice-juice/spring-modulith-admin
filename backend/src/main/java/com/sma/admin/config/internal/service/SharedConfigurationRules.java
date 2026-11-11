package com.sma.admin.config.internal.service;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.BiFunction;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.sma.admin.config.*;

/** Pure, bounded composition. Persistence, authorization, publication and cache admission belong to the caller. */
public final class SharedConfigurationRules {
    public static final int MAX_ITEMS = 512;
    public static final int MAX_DEPTH = 32;
    private final JsonMapper json;
    public SharedConfigurationRules(JsonMapper json) { this.json = Objects.requireNonNull(json); }
    public void validateLabel(ConfigurationLabel value) { label(value); }
    /** Optional.empty is inheritance; Optional.of(new Value(null)) is an explicit null. */
    public record Value(String value) {
        @Override public String toString() { return "ConfigurationValue[redacted]"; }
    }
    /** A label override replaces its complete localized bundle. Null fields inherit; false explicitly restores visibility. */
    public record ItemOverride(String code, ConfigurationLabel label, Integer sort, Boolean hidden) {}

    public void validate(ParameterDefinition definition) {
        Objects.requireNonNull(definition); owner(definition.owner());
        if (definition.key() == null || !definition.key().matches("[a-z][a-zA-Z0-9.-]{0,127}")
                || definition.group() == null || !definition.group().matches("[a-z][a-z0-9_-]{0,63}")) invalid("parameter identity");
        if (!definition.owner().equals("config") && !definition.key().startsWith(definition.owner() + ".")) invalid("parameter namespace");
        if (LegacyBrandParameters.KEYS.stream().anyMatch(key -> key.equalsIgnoreCase(definition.key()))) invalid("brand-owned parameter");
        if (definition.owner().equals("config") && (definition.key().startsWith("auth.") || definition.key().startsWith("login.")) && !definition.securitySensitive()) invalid("security parameter classification");
        label(definition.label()); Objects.requireNonNull(definition.scope()); Objects.requireNonNull(definition.valueType());
        Objects.requireNonNull(definition.overridePolicy()); Objects.requireNonNull(definition.publicProjection());
        if (definition.scope() == ParameterDefinition.Scope.TENANT_ONLY && definition.overridePolicy() != ParameterDefinition.OverridePolicy.OVERRIDABLE) invalid("private parameter policy");
        if (definition.publicProjection() == ParameterDefinition.PublicProjection.LOGIN_CAPTCHA
                && (!definition.owner().equals("config") || !definition.key().equals("login.captchaEnabled")
                    || definition.valueType() != ParameterDefinition.ValueType.BOOLEAN || !definition.securitySensitive())) invalid("public projection");
        var rules = Objects.requireNonNull(definition.constraints());
        if (rules.maxLength() < 1 || rules.maxLength() > 4000 || rules.choices().size() > 100) invalid("value budget");
        boolean numeric = definition.valueType() == ParameterDefinition.ValueType.INTEGER || definition.valueType() == ParameterDefinition.ValueType.DECIMAL;
        if (!numeric && (rules.minimum() != null || rules.maximum() != null)) invalid("numeric constraint type");
        BigDecimal min = boundary(rules.minimum()), max = boundary(rules.maximum());
        if (min != null && max != null && min.compareTo(max) > 0) invalid("numeric constraint order");
        for (var choice : rules.choices()) value(definition, choice, false);
        value(definition, definition.defaultValue(), true);
    }

    public ParameterSnapshot parameter(ParameterDefinition definition, Optional<Value> platform, Optional<Value> tenant, ConfigurationRevision revision) {
        validate(definition); Objects.requireNonNull(platform); Objects.requireNonNull(tenant); Objects.requireNonNull(revision);
        if (definition.scope() == ParameterDefinition.Scope.TENANT_ONLY && (platform.isPresent() || revision.platform() != 0)) invalid("private parameter platform layer");
        if (definition.scope() == ParameterDefinition.Scope.PLATFORM_SHARED && definition.overridePolicy() == ParameterDefinition.OverridePolicy.LOCKED && tenant.isPresent()) invalid("locked parameter override");
        platform.ifPresent(layer -> value(definition, layer.value(), true));
        tenant.ifPresent(layer -> value(definition, layer.value(), true));
        String effective = definition.defaultValue(); ConfigurationOrigin origin = ConfigurationOrigin.DEFAULT;
        if (platform.isPresent()) { effective = platform.get().value(); origin = ConfigurationOrigin.PLATFORM; }
        if (tenant.isPresent()) { effective = tenant.get().value(); origin = definition.scope() == ParameterDefinition.Scope.TENANT_ONLY ? ConfigurationOrigin.TENANT_ONLY : ConfigurationOrigin.TENANT_OVERRIDE; }
        return new ParameterSnapshot(definition.key(), definition.valueType(), effective, origin, revision);
    }

    private void value(ParameterDefinition definition, String value, boolean checkChoices) {
        var rules = definition.constraints();
        if (value == null) { if (!rules.nullable()) invalid("null value"); return; }
        if (value.length() > rules.maxLength() || (!rules.allowEmpty() && value.isEmpty())) invalid("value length");
        try {
            BigDecimal number = null;
            switch (definition.valueType()) {
                case STRING -> { }
                case BOOLEAN -> { if (!Set.of("true", "false").contains(value)) invalid("boolean value"); }
                case INTEGER -> number = BigDecimal.valueOf(Long.parseLong(value));
                case DECIMAL -> number = number(value);
                case JSON -> {
                    try (var parser = json.createParser(value)) {
                        JsonNode node = json.readTree(parser);
                        if (node == null || node.isMissingNode() || parser.nextToken() != null || (node.isNull() && !rules.nullable())) invalid("JSON document");
                        jsonBudget(node, 0, new int[]{0});
                    }
                }
            }
            if (number != null && (rules.minimum() != null && number.compareTo(boundary(rules.minimum())) < 0
                    || rules.maximum() != null && number.compareTo(boundary(rules.maximum())) > 0)) invalid("numeric value range");
        } catch (RuntimeException failure) {
            // Do not retain parser/number exceptions whose messages may contain a secret or customer value.
            throw new IllegalArgumentException("Invalid configuration value");
        }
        if (checkChoices && !rules.choices().isEmpty() && !rules.choices().contains(value)) invalid("value choices");
    }
    private BigDecimal boundary(String value) {
        if (value == null) return null;
        if (value.length() > 128) throw new IllegalArgumentException("Invalid numeric constraint");
        try { return number(value); } catch (RuntimeException failure) { throw new IllegalArgumentException("Invalid numeric constraint"); }
    }
    private BigDecimal number(String value) {
        var number = new BigDecimal(value);
        if (number.precision() > 128 || Math.abs((long) number.scale()) > 128) invalid("decimal budget");
        return number;
    }
    private void jsonBudget(JsonNode node, int depth, int[] count) {
        if (depth > MAX_DEPTH || ++count[0] > 1024) invalid("JSON structure budget");
        if (node.isArray() || node.isObject()) for (var child : node) jsonBudget(child, depth + 1, count);
    }

    public void validate(DictionaryDefinition definition) {
        Objects.requireNonNull(definition); owner(definition.owner()); label(definition.label()); Objects.requireNonNull(definition.mode());
        if (definition.key() == null || !definition.key().matches("[a-z][a-z0-9_-]{0,63}")) invalid("dictionary identity");
        if (!definition.owner().equals("config") && !definition.key().startsWith(definition.owner() + "_")) invalid("dictionary namespace");
        if (definition.mode() == DictionaryDefinition.Mode.TENANT_ONLY && !definition.items().isEmpty()) invalid("private dictionary shared defaults");
        items(definition.items(), definition.tree());
    }

    /** Publishing can disable stable codes, but cannot erase them or reinterpret their parent relationship. */
    public void publication(DictionaryDefinition definition, List<DictionaryDefinition.Item> previous, List<DictionaryDefinition.Item> next) {
        validate(definition);
        if (definition.mode() == DictionaryDefinition.Mode.TENANT_ONLY) invalid("private dictionary publication");
        var before = items(previous, definition.tree()); var after = items(next, definition.tree());
        for (var entry : before.values()) {
            var replacement = after.get(entry.code());
            if (replacement == null || !Objects.equals(entry.parentCode(), replacement.parentCode())) invalid("stable dictionary code");
        }
    }

    public DictionarySnapshot dictionary(DictionaryDefinition definition, Optional<List<DictionaryDefinition.Item>> platform,
            List<ItemOverride> overlays, List<DictionaryDefinition.Item> privateItems, ConfigurationRevision revision,
            String language, BiFunction<String,String,String> messages) {
        validate(definition); Objects.requireNonNull(platform); Objects.requireNonNull(overlays); Objects.requireNonNull(privateItems); Objects.requireNonNull(revision); Objects.requireNonNull(messages);
        if (!Set.of("zh-CN", "en-US").contains(language)) invalid("configuration language");
        boolean onlyPrivate = definition.mode() == DictionaryDefinition.Mode.TENANT_ONLY;
        if (onlyPrivate && (platform.isPresent() || !overlays.isEmpty() || revision.platform() != 0)) invalid("private dictionary platform layer");
        if (definition.mode() == DictionaryDefinition.Mode.PLATFORM_LOCKED && (!overlays.isEmpty() || !privateItems.isEmpty())) invalid("locked dictionary override");
        if (overlays.size() > MAX_ITEMS || privateItems.size() > MAX_ITEMS) invalid("dictionary capacity");
        var base = items(onlyPrivate ? List.of() : platform.orElse(definition.items()), definition.tree());
        var combined = new LinkedHashMap<>(base);
        for (var item : privateItems) {
            item(item);
            if (combined.putIfAbsent(item.code(), item) != null) invalid("dictionary code collision");
        }
        if (combined.size() > MAX_ITEMS) invalid("dictionary capacity");
        tree(combined, definition.tree());
        var changes = new HashMap<String,ItemOverride>();
        for (var overlay : overlays) {
            Objects.requireNonNull(overlay);
            if (!base.containsKey(overlay.code()) || changes.putIfAbsent(overlay.code(), overlay) != null) invalid("dictionary override target");
            if (overlay.label() != null) label(overlay.label());
            if (overlay.sort() != null && overlay.sort() < 0) invalid("dictionary sort");
        }
        var entries = new ArrayList<DictionarySnapshot.Entry>();
        for (var item : combined.values()) {
            var overlay = changes.get(item.code());
            boolean hidden = overlay != null && Boolean.TRUE.equals(overlay.hidden());
            var label = overlay != null && overlay.label() != null ? overlay.label() : item.label();
            int sort = overlay != null && overlay.sort() != null ? overlay.sort() : item.sort();
            ConfigurationOrigin origin = !base.containsKey(item.code()) ? ConfigurationOrigin.TENANT_ONLY
                    : overlay != null ? ConfigurationOrigin.TENANT_OVERRIDE : platform.isPresent() ? ConfigurationOrigin.PLATFORM : ConfigurationOrigin.DEFAULT;
            entries.add(new DictionarySnapshot.Entry(item.code(), item.parentCode(), text(label, language, messages), item.enabled(), hidden,
                    selectable(item, combined, changes), sort, origin));
        }
        // Stable sort preserves publication/private source order for equal sort values.
        entries.sort(Comparator.comparingInt(DictionarySnapshot.Entry::sort));
        var children = new HashMap<String,List<DictionarySnapshot.Entry>>();
        for (var entry : entries) if (entry.selectable()) children.computeIfAbsent(entry.parentCode(), ignored -> new ArrayList<>()).add(entry);
        var options = children.getOrDefault(null, List.of()).stream().map(entry -> option(entry, children)).toList();
        return new DictionarySnapshot(definition.key(), definition.mode(), revision, entries, options);
    }

    private LinkedHashMap<String,DictionaryDefinition.Item> items(List<DictionaryDefinition.Item> values, boolean isTree) {
        Objects.requireNonNull(values); if (values.size() > MAX_ITEMS) invalid("dictionary capacity");
        var result = new LinkedHashMap<String,DictionaryDefinition.Item>();
        for (var value : values) { item(value); if (result.putIfAbsent(value.code(), value) != null) invalid("duplicate dictionary code"); }
        tree(result, isTree); return result;
    }
    private void item(DictionaryDefinition.Item value) {
        Objects.requireNonNull(value); code(value.code()); if (value.parentCode() != null) code(value.parentCode()); label(value.label());
        if (value.sort() < 0) invalid("dictionary sort");
    }
    private void tree(Map<String,DictionaryDefinition.Item> values, boolean isTree) {
        for (var value : values.values()) {
            if (!isTree && value.parentCode() != null) invalid("flat dictionary parent");
            var visited = new HashSet<String>(); var current = value;
            while (current != null) {
                if (!visited.add(current.code()) || visited.size() > MAX_DEPTH) invalid("dictionary tree structure");
                if (current.parentCode() == null) break;
                current = values.get(current.parentCode()); if (current == null) invalid("dictionary parent");
            }
        }
    }
    private boolean selectable(DictionaryDefinition.Item item, Map<String,DictionaryDefinition.Item> values, Map<String,ItemOverride> changes) {
        var current = item;
        while (current != null) {
            var change = changes.get(current.code());
            if (!current.enabled() || change != null && Boolean.TRUE.equals(change.hidden())) return false;
            current = current.parentCode() == null ? null : values.get(current.parentCode());
        }
        return true;
    }
    private DictionaryOption option(DictionarySnapshot.Entry entry, Map<String,List<DictionarySnapshot.Entry>> children) {
        return new DictionaryOption(entry.code(), entry.label(), children.getOrDefault(entry.code(), List.of()).stream().map(child -> option(child, children)).toList());
    }
    private String text(ConfigurationLabel label, String language, BiFunction<String,String,String> messages) {
        String translated = label.translations().get(language);
        if (translated != null) return translated;
        String message = label.messageKey() == null || label.messageKey().isBlank() ? null : messages.apply(label.messageKey(), language);
        return message == null ? label.text() : message;
    }
    private void label(ConfigurationLabel value) {
        Objects.requireNonNull(value);
        if (value.text() == null || value.text().isBlank() || value.text().length() > 128
                || value.messageKey() != null && value.messageKey().length() > 256
                || !Set.of("zh-CN", "en-US").containsAll(value.translations().keySet())
                || value.translations().values().stream().anyMatch(text -> text.isBlank() || text.length() > 128)) invalid("configuration label");
    }
    private void owner(String value) { if (value == null || !value.matches("[a-z]{1,64}")) invalid("configuration owner"); }
    private void code(String value) { DictionaryCodeIdentity.validate(value); }
    private void invalid(String reason) { throw new IllegalArgumentException("Invalid configuration: " + reason); }
}
