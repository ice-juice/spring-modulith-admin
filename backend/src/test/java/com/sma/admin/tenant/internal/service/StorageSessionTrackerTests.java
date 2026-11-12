package com.sma.admin.tenant.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;

import com.sma.admin.common.request.BusinessLedger;
import org.junit.jupiter.api.Test;

class StorageSessionTrackerTests {
    @Test void blockRejectsNewSessionsAndWaitsForAnExistingLease() {
        var tracker = new StorageSessionTracker();
        var original = mock(BusinessLedger.Session.class);
        var session = tracker.track(11, "shop", original);
        assertThat(tracker.active(11, "shop")).isEqualTo(1);
        assertThat(tracker.block(11, "shop")).isEqualTo(1);
        var refused = mock(BusinessLedger.Session.class);
        assertThatThrownBy(() -> tracker.track(11, "shop", refused))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("draining");
        verify(refused).close();
        assertThat(tracker.active(11, "shop")).isEqualTo(1);
        session.close(); session.close();
        verify(original).close();
        assertThat(tracker.active(11, "shop")).isZero();
        tracker.unblock(11, "shop");
        try (var next = tracker.track(11, "shop", mock(BusinessLedger.Session.class))) {
            assertThat(tracker.active(11, "shop")).isEqualTo(1);
        }
        assertThat(tracker.active(11, "shop")).isZero();
    }

    @Test void failedPhysicalCloseKeepsTheSessionInDrainAccounting() {
        var tracker = new StorageSessionTracker();
        var physical = mock(BusinessLedger.Session.class);
        doThrow(new IllegalStateException("close unknown")).when(physical).close();
        var released = new java.util.concurrent.atomic.AtomicBoolean();
        var session = tracker.track(11, "shop", physical, () -> released.set(true));
        assertThatThrownBy(session::close).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unknown");
        assertThat(released).isFalse();
        assertThat(tracker.block(11, "shop")).isEqualTo(1);
    }
}
