package com.sma.admin.common.cache;

import static org.assertj.core.api.Assertions.*;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import com.sma.admin.common.internal.cache.CacheRegistry;

class CacheContractTests {
    @Test void registrationsCannotCrossAnOwnersNamespaceOrExposeProtectedMutations() {
        assertThatThrownBy(() -> descriptor("iam.parameters", "config", CacheDescriptor.Kind.DATA, Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> descriptor("config.parameters", "config", CacheDescriptor.Kind.PROTECTED,
                Set.of(CacheDescriptor.Operation.CLEAR))).isInstanceOf(IllegalArgumentException.class);
        assertThat(descriptor("config.parameters", "config", CacheDescriptor.Kind.PROTECTED, Set.of()).operations()).isEmpty();
    }

    @Test void registryRejectsDuplicateAndUnknownRegionsRatherThanCreatingImplicitCaches() {
        CacheCatalog catalog = () -> List.of(descriptor("config.parameters", "config", CacheDescriptor.Kind.DATA, Set.of()));
        assertThatThrownBy(() -> new CacheRegistry(List.of(catalog, catalog))).isInstanceOf(IllegalArgumentException.class);
        var registry = new CacheRegistry(List.of(catalog));
        assertThatThrownBy(() -> registry.require("shop.orders")).isInstanceOf(IllegalArgumentException.class);
        assertThat(registry.require("config.parameters").scope()).isEqualTo(CacheDescriptor.Scope.TENANT);
    }

    @Test void policyCannotCreateUnboundedOrNegativeCaches() {
        assertThatThrownBy(() -> new CachePolicy(Duration.ZERO, Duration.ZERO, 1, 1024, 1024)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CachePolicy(Duration.ofSeconds(1), Duration.ofSeconds(2), 1, 1024, 1024)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CachePolicy(Duration.ofSeconds(1), Duration.ZERO, 0, 1024, 1024)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CachePolicy(Duration.ofSeconds(1), Duration.ZERO, 1, 1024, 1025)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void registrationDoesNotRetainMutableCallerCollections() {
        var operations = new HashSet<>(Set.of(CacheDescriptor.Operation.CLEAR));
        var descriptor = descriptor("config.parameters", "config", CacheDescriptor.Kind.DATA, operations);
        operations.add(CacheDescriptor.Operation.PAUSE);
        assertThat(descriptor.operations()).containsExactly(CacheDescriptor.Operation.CLEAR);
        assertThatThrownBy(() -> descriptor.operations().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    private CacheDescriptor descriptor(String id, String owner, CacheDescriptor.Kind kind, Set<CacheDescriptor.Operation> operations) {
        return new CacheDescriptor(id, owner, "config.cache.parameters", "basic-config", "parameter-read", kind,
                CacheDescriptor.Scope.TENANT, 1, CachePolicy.defaults(), operations, Set.of("parameter-write"), "monitor:cache:clear");
    }
}
