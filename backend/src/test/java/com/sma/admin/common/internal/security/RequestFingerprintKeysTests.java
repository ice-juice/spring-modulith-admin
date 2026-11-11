package com.sma.admin.common.internal.security;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sma.admin.common.request.RequestRejected;

class RequestFingerprintKeysTests {
    private static final String OLD = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";
    private static final String NEW = "AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=";
    @Test void rotationPreservesOldFingerprintsAndBindsScopeAndInput() {
        var old = new RequestFingerprintKeys("one", OLD, "");
        var rotated = new RequestFingerprintKeys("two", NEW, "one=" + OLD);
        assertThat(rotated.digest("one", "actor-A", new byte[]{1})).isEqualTo(old.digest("one", "actor-A", new byte[]{1}));
        assertThat(rotated.digest("two", "actor-A", new byte[]{1})).isNotEqualTo(old.digest("one", "actor-A", new byte[]{1}));
        assertThat(rotated.digest("one", "actor-B", new byte[]{1})).isNotEqualTo(old.digest("one", "actor-A", new byte[]{1}));
        assertThat(rotated.digest("one", "actor-A", new byte[]{2})).isNotEqualTo(old.digest("one", "actor-A", new byte[]{1}));
        assertThatThrownBy(() -> rotated.digest("ONE", "actor-A", new byte[]{1})).isInstanceOf(RequestRejected.class);
        assertThat(rotated.toString()).doesNotContain(OLD, NEW);
    }
    @Test void invalidOrDuplicateKeyMaterialIsNotAcceptedOrPrinted() {
        assertThatThrownBy(() -> new RequestFingerprintKeys("one", "bad-secret", "")).isInstanceOf(IllegalArgumentException.class).hasMessageNotContaining("bad-secret");
        assertThatThrownBy(() -> new RequestFingerprintKeys("one", OLD, "one=" + NEW)).isInstanceOf(IllegalArgumentException.class);
    }
}
