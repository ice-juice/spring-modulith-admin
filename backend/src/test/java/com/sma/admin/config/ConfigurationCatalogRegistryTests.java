package com.sma.admin.config;

import static org.assertj.core.api.Assertions.*;
import java.lang.reflect.Proxy;
import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import com.sma.admin.config.internal.service.ConfigurationCatalogRegistry;
import com.sma.admin.config.internal.service.ConfigurationPlatformGuard;
import com.sma.admin.config.internal.service.SharedConfigurationRules;

class ConfigurationCatalogRegistryTests {
    private record Catalog(List<ParameterDefinition> parameters, List<DictionaryDefinition> dictionaries) implements ConfigurationCatalog {}
    private static ParameterDefinition definition(String owner, String key, String value, ParameterDefinition.Constraints constraints, Map<String,String> translations) {
        return new ParameterDefinition(owner, key, "basic", new ConfigurationLabel("Example", "config.example.label", translations), ParameterDefinition.Scope.PLATFORM_SHARED,
                ParameterDefinition.ValueType.STRING, ParameterDefinition.OverridePolicy.OVERRIDABLE, value, constraints, false, ParameterDefinition.PublicProjection.NONE);
    }
    private static ParameterDefinition definition(String key) { return definition("config", key, "default", ParameterDefinition.Constraints.required(), Map.of()); }
    private ConfigurationCatalogRegistry registry(ParameterDefinition... definitions) { return new ConfigurationCatalogRegistry(List.of(new Catalog(List.of(definitions), List.of())), JsonMapper.builder().build()); }

    @Test void actualProviderOwnershipAndCaseInsensitiveKeyUniquenessAreEnforced() {
        assertThatThrownBy(() -> registry(definition("shop", "shop.limit", "1", ParameterDefinition.Constraints.required(), Map.of()))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("another module");
        assertThatThrownBy(() -> registry(definition("config.example"), definition("config.Example"))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("duplicate");
        var registered = registry(definition("config.example"));
        assertThat(registered.require(ConfigurationCatalogRegistry.Kind.PARAMETER, "CONFIG.EXAMPLE").key()).isEqualTo("config.example");
        assertThat(registered.find(ConfigurationCatalogRegistry.Kind.PARAMETER, null)).isEmpty();
        assertThatThrownBy(() -> registered.entries().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test void providersOutsideDeclaredModulePackagesCannotRegisterEvenAnEmptyCatalog() {
        var proxy = (ConfigurationCatalog) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{ConfigurationCatalog.class}, (instance, method, arguments) -> List.of());
        assertThatThrownBy(() -> new ConfigurationCatalogRegistry(List.of(proxy), JsonMapper.builder().build())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void semanticFingerprintsDoNotDependOnMapOrChoiceInsertionOrder() {
        var first = new LinkedHashMap<String,String>(); first.put("zh-CN", "中文"); first.put("en-US", "English");
        var second = new LinkedHashMap<String,String>(); second.put("en-US", "English"); second.put("zh-CN", "中文");
        var choicesA = new LinkedHashSet<>(List.of("a", "b")); var choicesB = new LinkedHashSet<>(List.of("b", "a"));
        var a = registry(definition("config", "config.example", "a", new ParameterDefinition.Constraints(false, false, 100, null, null, choicesA), first)).entries().iterator().next();
        var b = registry(definition("config", "config.example", "a", new ParameterDefinition.Constraints(false, false, 100, null, null, choicesB), second)).entries().iterator().next();
        assertThat(a.fingerprint()).isEqualTo(b.fingerprint()); assertThat(a.contractHash()).isEqualTo(b.contractHash());
    }

    @Test void defaultChangesAreDistinctFromBreakingConstraintChanges() {
        var a = registry(definition("config", "config.example", "before", ParameterDefinition.Constraints.required(), Map.of())).entries().iterator().next();
        var b = registry(definition("config", "config.example", "after", ParameterDefinition.Constraints.required(), Map.of())).entries().iterator().next();
        var c = registry(definition("config", "config.example", "after", new ParameterDefinition.Constraints(false, false, 100, null, null, Set.of()), Map.of())).entries().iterator().next();
        assertThat(a.contractHash()).isEqualTo(b.contractHash()); assertThat(a.fingerprint()).isNotEqualTo(b.fingerprint());
        assertThat(b.contractHash()).isNotEqualTo(c.contractHash());
    }

    @Test void capacityAndHostAuthorityAreExplicitAndDiagnosticsOmitValues() {
        var definitions = new ArrayList<ParameterDefinition>();
        for (int index = 0; index < 257; index++) definitions.add(definition("config.item" + index));
        assertThatThrownBy(() -> registry(definitions.toArray(ParameterDefinition[]::new))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("budget");
        assertThatThrownBy(() -> new ConfigurationPlatformGuard(List.of())).isInstanceOf(IllegalStateException.class);
        ConfigurationPlatformAccess access = new ConfigurationPlatformAccess() {
            public boolean isPlatformTenant() { return false; }
            public void requireOperator(String permission) { throw new UnsupportedOperationException(); }
        };
        assertThatThrownBy(() -> new ConfigurationPlatformGuard(List.of(access, access))).isInstanceOf(IllegalStateException.class);
        var sensitive = definition("config", "config.example", "do-not-log", ParameterDefinition.Constraints.required(), Map.of());
        assertThat(sensitive.toString()).doesNotContain("do-not-log");
        assertThat(new SharedConfigurationRules.Value("do-not-log").toString()).doesNotContain("do-not-log");
        assertThat(new ParameterSnapshot("config.example", ParameterDefinition.ValueType.STRING, "do-not-log", ConfigurationOrigin.PLATFORM, new ConfigurationRevision(1, 0)).toString()).doesNotContain("do-not-log");
        assertThat(registry(sensitive).entries().toString()).doesNotContain("do-not-log");
    }
}
