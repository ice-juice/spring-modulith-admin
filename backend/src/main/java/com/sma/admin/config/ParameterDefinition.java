package com.sma.admin.config;

import java.util.Set;
import java.util.Collections;
import java.util.TreeSet;

/** Declarative metadata; it cannot carry credentials or executable validation code. */
public record ParameterDefinition(String owner, String key, String group, ConfigurationLabel label,
        Scope scope, ValueType valueType, OverridePolicy overridePolicy, String defaultValue,
        Constraints constraints, boolean securitySensitive, PublicProjection publicProjection) {
    public enum Scope { TENANT_ONLY, PLATFORM_SHARED }
    public enum ValueType { STRING, BOOLEAN, INTEGER, DECIMAL, JSON }
    public enum OverridePolicy { LOCKED, OVERRIDABLE }
    public enum PublicProjection { NONE, LOGIN_CAPTCHA }
    public record Constraints(boolean nullable, boolean allowEmpty, int maxLength, String minimum, String maximum, Set<String> choices) {
        public Constraints { choices = Collections.unmodifiableSet(new TreeSet<>(choices == null ? Set.of() : choices)); }
        public static Constraints required() { return new Constraints(false, false, 4000, null, null, Set.of()); }
    }
    @Override public String toString() { return "ParameterDefinition[owner=" + owner + ", key=" + key + ", scope=" + scope + ", valueType=" + valueType + "]"; }
}
