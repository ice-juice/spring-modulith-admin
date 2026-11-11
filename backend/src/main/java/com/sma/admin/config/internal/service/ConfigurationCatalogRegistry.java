package com.sma.admin.config.internal.service;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.aop.support.AopUtils;
import org.springframework.modulith.ApplicationModule;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import com.sma.admin.config.*;

/** Registration for trusted static modules, not a sandbox for hostile classes placed in an approved Java package. */
@Component
public class ConfigurationCatalogRegistry {
    public enum Kind { PARAMETER, DICTIONARY }
    public record Entry(Kind kind, String key, String owner, ParameterDefinition parameter, DictionaryDefinition dictionary,
            String definitionJson, String contractHash, String fingerprint) {
        public boolean shared() { return kind == Kind.PARAMETER ? parameter.scope() == ParameterDefinition.Scope.PLATFORM_SHARED : dictionary.mode() != DictionaryDefinition.Mode.TENANT_ONLY; }
        @Override public String toString() { return "ConfigurationEntry[kind=" + kind + ", key=" + key + ", owner=" + owner + "]"; }
    }
    private record Key(Kind kind, String key) {
        private Key { Objects.requireNonNull(kind); key = key == null ? "" : key.toLowerCase(Locale.ROOT); }
    }
    private static final Pattern MODULE = Pattern.compile("^(com\\.sma\\.(admin|business))\\.([a-z]+)(?:\\..*)?$");
    private final Map<Key,Entry> entries;
    private final JsonMapper json;
    private final SharedConfigurationRules rules;

    public ConfigurationCatalogRegistry(List<ConfigurationCatalog> providers, JsonMapper json) {
        this.json = json.rebuild().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).build();
        this.rules = new SharedConfigurationRules(this.json);
        var found = new LinkedHashMap<Key,Entry>();
        long totalBytes = 0;
        for (var provider : providers) {
            String owner = owner(provider);
            for (var definition : provider.parameters()) {
                rules.validate(definition); if (!definition.owner().equals(owner)) invalid("provider cannot register another module's parameter");
                String data = encode(definition);
                var contract = Map.of("owner", owner, "key", definition.key(), "scope", definition.scope(), "valueType", definition.valueType(),
                        "overridePolicy", definition.overridePolicy(), "constraints", definition.constraints(), "securitySensitive", definition.securitySensitive(), "publicProjection", definition.publicProjection());
                totalBytes += add(found, new Entry(Kind.PARAMETER, definition.key(), owner, definition, null, data, digest(encode(contract)), digest(data)));
                if (totalBytes > 8 * 1024 * 1024) invalid("catalog exceeds total definition budget");
            }
            for (var definition : provider.dictionaries()) {
                rules.validate(definition); if (!definition.owner().equals(owner)) invalid("provider cannot register another module's dictionary");
                String data = encode(definition);
                var contract = Map.of("owner", owner, "key", definition.key(), "mode", definition.mode(), "tree", definition.tree());
                totalBytes += add(found, new Entry(Kind.DICTIONARY, definition.key(), owner, null, definition, data, digest(encode(contract)), digest(data)));
                if (totalBytes > 8 * 1024 * 1024) invalid("catalog exceeds total definition budget");
            }
            if (totalBytes > 8 * 1024 * 1024) invalid("catalog exceeds total definition budget");
        }
        this.entries = Collections.unmodifiableMap(found);
    }
    public Collection<Entry> entries() { return entries.values(); }
    public Optional<Entry> find(Kind kind, String key) { return Optional.ofNullable(entries.get(new Key(kind, key))); }
    public Entry require(Kind kind, String key) { return find(kind, key).orElseThrow(() -> new IllegalArgumentException("Configuration definition is not registered")); }
    public String encode(Object value) { return json.writeValueAsString(value); }
    private int add(Map<Key,Entry> found, Entry value) {
        int bytes = value.definitionJson().getBytes(StandardCharsets.UTF_8).length;
        if (bytes > 256 * 1024 || found.size() >= 256) invalid("catalog exceeds definition budget");
        if (found.putIfAbsent(new Key(value.kind(), value.key()), value) != null) invalid("duplicate configuration definition");
        return bytes;
    }
    private String owner(Object provider) {
        var type = ClassUtils.getUserClass(AopUtils.getTargetClass(provider)); var match = MODULE.matcher(type.getPackageName());
        if (!match.matches()) throw new IllegalArgumentException("Configuration provider must belong to a declared module");
        try {
            var metadata = Class.forName(match.group(1) + "." + match.group(3) + ".package-info", false, type.getClassLoader()).getAnnotation(ApplicationModule.class);
            if (metadata == null) invalid("provider module has no Modulith declaration");
        } catch (ClassNotFoundException missing) { throw new IllegalArgumentException("Configuration provider module is not declared"); }
        return match.group(3);
    }
    private String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private void invalid(String reason) { throw new IllegalArgumentException("Invalid configuration catalog: " + reason); }
}
