package com.sma.admin.config;

import static org.assertj.core.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import com.sma.admin.config.internal.service.SharedConfigurationRules;
import com.sma.admin.config.internal.service.SharedConfigurationRules.ItemOverride;
import com.sma.admin.config.internal.service.SharedConfigurationRules.Value;

class SharedConfigurationRulesTests {
    private final SharedConfigurationRules rules = new SharedConfigurationRules(JsonMapper.builder().build());
    private final ConfigurationRevision revision = new ConfigurationRevision(7, 11);
    private static ConfigurationLabel label(String value) { return new ConfigurationLabel(value, null, Map.of()); }
    private static ParameterDefinition parameter(ParameterDefinition.ValueType type, String fallback, ParameterDefinition.Constraints constraints) {
        return new ParameterDefinition("config", "config.example", "basic", label("Example"), ParameterDefinition.Scope.PLATFORM_SHARED,
                type, ParameterDefinition.OverridePolicy.OVERRIDABLE, fallback, constraints, false, ParameterDefinition.PublicProjection.NONE);
    }
    private static ParameterDefinition parameter(ParameterDefinition.ValueType type, String fallback) { return parameter(type, fallback, ParameterDefinition.Constraints.required()); }
    private static Optional<Value> value(String value) { return Optional.of(new Value(value)); }
    private ParameterSnapshot resolve(ParameterDefinition definition, Optional<Value> platform, Optional<Value> tenant) { return rules.parameter(definition, platform, tenant, revision); }
    private static DictionaryDefinition.Item item(String code, String parent, boolean enabled, int sort) { return new DictionaryDefinition.Item(code, parent, label("Label " + code), enabled, sort); }
    private static DictionaryDefinition dictionary(DictionaryDefinition.Mode mode, boolean tree, DictionaryDefinition.Item... items) { return new DictionaryDefinition("config", "config_example", label("Example"), mode, tree, List.of(items)); }
    private DictionarySnapshot dictionary(DictionaryDefinition definition, List<ItemOverride> changes, List<DictionaryDefinition.Item> additions) {
        return rules.dictionary(definition, Optional.of(definition.items()), changes, additions, revision, "en-US", (key, locale) -> null);
    }

    @Test void defaultsSharedValuesAndExplicitTenantValuesHaveDistinctOrigins() {
        var definition = parameter(ParameterDefinition.ValueType.STRING, "default");
        assertThat(resolve(definition, Optional.empty(), Optional.empty()).origin()).isEqualTo(ConfigurationOrigin.DEFAULT);
        assertThat(resolve(definition, value("shared"), Optional.empty()).value()).isEqualTo("shared");
        assertThat(resolve(definition, value("shared"), Optional.empty()).origin()).isEqualTo(ConfigurationOrigin.PLATFORM);
        var result = resolve(definition, value("shared"), value("tenant"));
        assertThat(result.value()).isEqualTo("tenant"); assertThat(result.origin()).isEqualTo(ConfigurationOrigin.TENANT_OVERRIDE); assertThat(result.revision()).isEqualTo(revision);
        assertThat(resolve(definition, value("shared next"), value("tenant")).value()).isEqualTo("tenant");
        assertThat(resolve(definition, value("shared next"), Optional.empty()).value()).isEqualTo("shared next");
    }

    @Test void nullAndEmptyAreExplicitValuesOnlyWhenTheDefinitionAllowsThem() {
        var definition = parameter(ParameterDefinition.ValueType.STRING, "default", new ParameterDefinition.Constraints(true, true, 100, null, null, Set.of()));
        assertThat(resolve(definition, value("shared"), value(null)).value()).isNull();
        assertThat(resolve(definition, value("shared"), value("")).value()).isEmpty();
        assertThat(resolve(definition, value("shared"), Optional.empty()).value()).isEqualTo("shared");
        var strict = parameter(ParameterDefinition.ValueType.STRING, "default");
        assertThatThrownBy(() -> resolve(strict, Optional.empty(), value(null))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> resolve(strict, Optional.empty(), value(""))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void lockedValuesAndPrivateParametersCannotAcquireTheWrongLayer() {
        var base = parameter(ParameterDefinition.ValueType.STRING, "default");
        var locked = new ParameterDefinition(base.owner(), base.key(), base.group(), base.label(), base.scope(), base.valueType(),
                ParameterDefinition.OverridePolicy.LOCKED, base.defaultValue(), base.constraints(), false, base.publicProjection());
        assertThatThrownBy(() -> resolve(locked, value("shared"), value("override"))).isInstanceOf(IllegalArgumentException.class);
        var privateDefinition = new ParameterDefinition(base.owner(), base.key(), base.group(), base.label(), ParameterDefinition.Scope.TENANT_ONLY,
                base.valueType(), base.overridePolicy(), base.defaultValue(), base.constraints(), false, base.publicProjection());
        assertThat(rules.parameter(privateDefinition, Optional.empty(), value("private"), new ConfigurationRevision(0, 2)).origin()).isEqualTo(ConfigurationOrigin.TENANT_ONLY);
        assertThatThrownBy(() -> resolve(privateDefinition, value("shared"), Optional.empty())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ConfigurationRevision(-1, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void valuesAreTypedBoundedAndDoNotExposeInvalidInputInDiagnostics() {
        var integer = parameter(ParameterDefinition.ValueType.INTEGER, "5", new ParameterDefinition.Constraints(false, false, 40, "1", "10", Set.of()));
        assertThat(resolve(integer, Optional.empty(), value("10")).value()).isEqualTo("10");
        for (String invalid : List.of("11", "0", "1.5", "9223372036854775808", "private-secret-input")) {
            assertThatThrownBy(() -> resolve(integer, Optional.empty(), value(invalid))).isInstanceOf(IllegalArgumentException.class).hasNoCause().hasMessageNotContaining(invalid);
        }
        var decimal = parameter(ParameterDefinition.ValueType.DECIMAL, "0");
        for (String invalid : List.of("1e99999999", "1e-99999999", "9".repeat(129), "NaN")) assertThatThrownBy(() -> resolve(decimal, Optional.empty(), value(invalid))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> resolve(parameter(ParameterDefinition.ValueType.BOOLEAN, "true"), Optional.empty(), value("TRUE"))).isInstanceOf(IllegalArgumentException.class);
        var choice = parameter(ParameterDefinition.ValueType.STRING, "one", new ParameterDefinition.Constraints(false, false, 8, null, null, Set.of("one", "two")));
        assertThatThrownBy(() -> resolve(choice, Optional.empty(), value("three"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void jsonReplacesTheWholeDocumentAndRejectsTrailingContentOrExcessiveStructure() {
        var definition = parameter(ParameterDefinition.ValueType.JSON, "{}");
        assertThat(resolve(definition, value("{\"a\":1,\"b\":2}"), value("{\"a\":3}")).value()).isEqualTo("{\"a\":3}");
        for (String invalid : List.of("{} {}", "{} trailing-private-value", "[".repeat(34) + "0" + "]".repeat(34), "[" + "0,".repeat(1024) + "0]", "null"))
            assertThatThrownBy(() -> resolve(definition, Optional.empty(), value(invalid))).isInstanceOf(IllegalArgumentException.class).hasNoCause().hasMessageNotContaining("trailing-private-value");
    }

    @Test void namespaceBrandOwnershipAndPublicProjectionCannotBeChangedByAnArbitraryDefinition() {
        var constraints = ParameterDefinition.Constraints.required();
        assertThatThrownBy(() -> rules.validate(new ParameterDefinition("shop", "auth.sessionTtlSeconds", "basic", label("Bad"), ParameterDefinition.Scope.PLATFORM_SHARED,
                ParameterDefinition.ValueType.INTEGER, ParameterDefinition.OverridePolicy.OVERRIDABLE, "60", constraints, false, ParameterDefinition.PublicProjection.NONE))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> rules.validate(new ParameterDefinition("config", "system.name", "basic", label("Bad"), ParameterDefinition.Scope.PLATFORM_SHARED,
                ParameterDefinition.ValueType.STRING, ParameterDefinition.OverridePolicy.OVERRIDABLE, "Brand", constraints, false, ParameterDefinition.PublicProjection.NONE))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> rules.validate(new ParameterDefinition("config", "config.secret", "basic", label("Bad"), ParameterDefinition.Scope.PLATFORM_SHARED,
                ParameterDefinition.ValueType.BOOLEAN, ParameterDefinition.OverridePolicy.OVERRIDABLE, "true", constraints, true, ParameterDefinition.PublicProjection.LOGIN_CAPTCHA))).isInstanceOf(IllegalArgumentException.class);
        rules.validate(new ParameterDefinition("config", "login.captchaEnabled", "login", label("Captcha"), ParameterDefinition.Scope.PLATFORM_SHARED,
                ParameterDefinition.ValueType.BOOLEAN, ParameterDefinition.OverridePolicy.OVERRIDABLE, "true", constraints, true, ParameterDefinition.PublicProjection.LOGIN_CAPTCHA));
    }

    @Test void hiddenAndDisabledTreesKeepHistoricalLabelsButAreNotSubmittable() {
        var definition = dictionary(DictionaryDefinition.Mode.EXTENDABLE, true, item("parent", null, true, 0), item("child", "parent", true, 1), item("disabled", null, false, 2));
        var result = dictionary(definition, List.of(new ItemOverride("parent", null, null, true), new ItemOverride("disabled", null, null, false)), List.of(item("local", "child", true, 3)));
        assertThat(result.options()).isEmpty(); assertThat(result.entries()).hasSize(4);
        assertThat(result.find("child").orElseThrow().label()).isEqualTo("Label child");
        for (String code : List.of("parent", "child", "disabled", "local")) assertThat(result.accepts(code)).isFalse();
        assertThat(dictionary(definition, List.of(new ItemOverride("parent", null, null, false)), List.of()).accepts("child")).isTrue();
    }

    @Test void extensionCanChangePresentationAndAddPrivateCodesWithoutChangingTheSharedInput() {
        var definition = dictionary(DictionaryDefinition.Mode.EXTENDABLE, true, item("root", null, true, 1), item("a", "root", true, 1));
        var labels = new HashMap<>(Map.of("en-US", "Customized"));
        var customLabel = new ConfigurationLabel("自定义", null, labels); labels.put("en-US", "mutated");
        var result = dictionary(definition, List.of(new ItemOverride("a", customLabel, 5, false)), List.of(item("private", "root", true, 2)));
        assertThat(result.find("a").orElseThrow().label()).isEqualTo("Customized");
        assertThat(result.find("a").orElseThrow().origin()).isEqualTo(ConfigurationOrigin.TENANT_OVERRIDE);
        assertThat(result.find("private").orElseThrow().origin()).isEqualTo(ConfigurationOrigin.TENANT_ONLY);
        assertThat(result.options().getFirst().children().stream().map(DictionaryOption::value)).containsExactly("private", "a");
        assertThat(definition.items().get(1).label().text()).isEqualTo("Label a");
        assertThatThrownBy(() -> result.entries().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> customLabel.translations().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test void lockedAndPrivateDictionaryModesRejectForeignLayers() {
        var locked = dictionary(DictionaryDefinition.Mode.PLATFORM_LOCKED, false, item("a", null, true, 0));
        assertThatThrownBy(() -> dictionary(locked, List.of(new ItemOverride("a", null, 1, null)), List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> dictionary(locked, List.of(), List.of(item("local", null, true, 1)))).isInstanceOf(IllegalArgumentException.class);
        var privateDefinition = dictionary(DictionaryDefinition.Mode.TENANT_ONLY, false);
        var result = rules.dictionary(privateDefinition, Optional.empty(), List.of(), List.of(item("local", null, true, 0)), new ConfigurationRevision(0, 3), "zh-CN", (key, locale) -> null);
        assertThat(result.accepts("local")).isTrue(); assertThat(result.entries().getFirst().origin()).isEqualTo(ConfigurationOrigin.TENANT_ONLY);
        assertThatThrownBy(() -> dictionary(privateDefinition, List.of(), List.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void cyclesMissingParentsDuplicateCodesAndNamespaceCollisionsAreRejected() {
        var definition = dictionary(DictionaryDefinition.Mode.EXTENDABLE, true, item("a", null, true, 0));
        assertThatThrownBy(() -> dictionary(definition, List.of(), List.of(item("a", null, true, 1)))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> dictionary(definition, List.of(), List.of(item("b", "other-type", true, 1)))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> dictionary(definition, List.of(new ItemOverride("missing", null, null, true)), List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> rules.validate(dictionary(DictionaryDefinition.Mode.EXTENDABLE, true, item("a", "b", true, 0), item("b", "a", true, 1)))).isInstanceOf(IllegalArgumentException.class);
        var tooDeep = new ArrayList<DictionaryDefinition.Item>();
        for (int i = 0; i < 33; i++) tooDeep.add(item("n" + i, i == 0 ? null : "n" + (i - 1), true, i));
        assertThatThrownBy(() -> rules.validate(new DictionaryDefinition("config", "deep", label("Deep"), DictionaryDefinition.Mode.EXTENDABLE, true, tooDeep))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> rules.validate(new DictionaryDefinition("shop", "config_owned", label("Bad owner"), DictionaryDefinition.Mode.EXTENDABLE, false, List.of()))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void publishingRetainsCodesAndParentsButCanDisableAndAddItems() {
        var old = List.of(item("root", null, true, 0), item("child", "root", true, 1));
        var definition = new DictionaryDefinition("config", "example", label("Example"), DictionaryDefinition.Mode.EXTENDABLE, true, old);
        assertThatThrownBy(() -> rules.publication(definition, old, List.of(old.getFirst()))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> rules.publication(definition, old, List.of(old.getFirst(), item("child", null, true, 1)))).isInstanceOf(IllegalArgumentException.class);
        rules.publication(definition, old, List.of(old.getFirst(), item("child", "root", false, 1), item("new", "root", true, 2)));
    }

    @Test void explicitTranslationsPrecedeMessageKeysAndFallbacks() {
        var item = new DictionaryDefinition.Item("a", null, new ConfigurationLabel("Fallback", "config.example.label", Map.of("zh-CN", "中文覆盖")), true, 0);
        var definition = dictionary(DictionaryDefinition.Mode.PLATFORM_LOCKED, false, item);
        assertThat(rules.dictionary(definition, Optional.empty(), List.of(), List.of(), new ConfigurationRevision(0, 0), "zh-CN", (key, language) -> "Message").find("a").orElseThrow().label()).isEqualTo("中文覆盖");
        assertThat(rules.dictionary(definition, Optional.empty(), List.of(), List.of(), new ConfigurationRevision(0, 0), "en-US", (key, language) -> "Message").find("a").orElseThrow().label()).isEqualTo("Message");
        assertThat(rules.dictionary(definition, Optional.empty(), List.of(), List.of(), new ConfigurationRevision(0, 0), "en-US", (key, language) -> null).find("a").orElseThrow().label()).isEqualTo("Fallback");
    }
}
