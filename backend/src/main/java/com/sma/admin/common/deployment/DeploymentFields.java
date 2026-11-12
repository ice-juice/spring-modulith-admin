package com.sma.admin.common.deployment;

import java.time.Duration;
import java.util.Collection;
import java.util.TreeMap;

/** Explicit non-secret whitelist builder. No Object/record/Environment serializer or raw-field getter. */
public final class DeploymentFields {
    private final TreeMap<String,String> fields = new TreeMap<>();
    public DeploymentFields text(String name, String value) {
        if (fields.size() >= 512 || name == null || !name.matches("[A-Za-z0-9_.-]{1,100}")
                || value == null || value.length() > 8192 || fields.putIfAbsent(name, value) != null)
            throw new IllegalArgumentException("Invalid deployment field");
        return this;
    }
    public DeploymentFields number(String name, long value) { return text(name, Long.toString(value)); }
    public DeploymentFields flag(String name, boolean value) { return text(name, Boolean.toString(value)); }
    public DeploymentFields duration(String name, Duration value) { return text(name, value.toString()); }
    public DeploymentFields enumeration(String name, Enum<?> value) { return text(name, value.name()); }
    public DeploymentFields values(String name, Collection<String> values) {
        if (values == null || values.size() > 256) throw new IllegalArgumentException("Invalid deployment collection");
        var sorted = values.stream().distinct().sorted().toList(); number(name + ".count", sorted.size());
        for (int i=0;i<sorted.size();i++) text(name + "." + i, sorted.get(i));
        return this;
    }
    public DeploymentEvidence.Proof proof(String domain, DeploymentEvidence.Challenge challenge) {
        return DeploymentEvidence.settings(domain, challenge, fields);
    }
}
