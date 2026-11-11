package com.sma.admin.common.cache;

import java.util.Objects;
import java.util.Set;

/** Stable, versioned registration; governance must never infer permissions from a group. */
public record CacheDescriptor(String id, String owner, String nameKey, String group, String scenario,
        Kind kind, Scope scope, int schemaVersion, CachePolicy policy,
        Set<Operation> operations, Set<String> invalidatedBy, String managePermission) {
    public enum Kind { DATA, RUNTIME, RESOURCE, PROTECTED }
    public enum Scope { TENANT, TENANT_PORTAL, TENANT_USER, TENANT_PORTAL_USER, NODE, PLATFORM }
    public enum Operation { CLEAR, PREWARM, PAUSE, RESET_STATS, EDIT_POLICY }

    public CacheDescriptor {
        if (owner == null || !owner.matches("[a-z]+") || id == null
                || id.length() > 96 || !id.matches("[a-z]+\\.[a-z][a-z0-9-]{0,79}") || !id.startsWith(owner + ".")) {
            throw new IllegalArgumentException("Cache region must belong to its declared module");
        }
        if (nameKey == null || !nameKey.startsWith(owner + ".") || nameKey.length() > 160
                || group == null || !group.matches("[a-z][a-z0-9.-]{0,79}")
                || scenario == null || !scenario.matches("[a-z][a-z0-9.-]{0,79}") || schemaVersion < 1) {
            throw new IllegalArgumentException("Invalid cache region metadata");
        }
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(scope, "scope");
        operations = Set.copyOf(operations);
        invalidatedBy = Set.copyOf(invalidatedBy);
        if (invalidatedBy.isEmpty() || invalidatedBy.stream().anyMatch(value -> value.isBlank() || value.length() > 160)) {
            throw new IllegalArgumentException("Cache invalidation sources must be documented");
        }
        if (managePermission == null || !managePermission.matches("[a-z]+(:[a-z][a-z0-9-]*){1,3}")) {
            throw new IllegalArgumentException("A cache region requires an explicit management permission");
        }
        if (kind == Kind.PROTECTED && !operations.isEmpty()) {
            throw new IllegalArgumentException("Protected state cannot expose cache mutations");
        }
        if (kind == Kind.DATA) {
            Objects.requireNonNull(policy, "policy");
            if (scope == Scope.NODE || scope == Scope.PLATFORM) {
                throw new IllegalArgumentException("Shared platform data requires a separate reviewed contract");
            }
        } else if (operations.contains(Operation.EDIT_POLICY) || operations.contains(Operation.PAUSE)
                || operations.contains(Operation.PREWARM)) {
            throw new IllegalArgumentException("Runtime/resource operations require their owning adapter");
        }
    }
}
