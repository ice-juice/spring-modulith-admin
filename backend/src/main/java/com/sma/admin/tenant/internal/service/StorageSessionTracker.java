package com.sma.admin.tenant.internal.service;

import com.sma.admin.common.request.BusinessLedger;
import com.sma.admin.common.request.RequestCommand;
import com.sma.admin.common.request.RequestExecution;
import com.sma.admin.common.request.RequestReceipt;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/** Local admission and drain accounting; S7c requires durable peer evidence before a cross-node switch. */
@Component
public class StorageSessionTracker {
    private record Key(long tenantId, String storageGroup) {}
    private static final class Slot {
        volatile int active;
        boolean blocked;
    }
    private final ConcurrentHashMap<Key, Slot> slots = new ConcurrentHashMap<>();

    public BusinessLedger.Session track(long tenantId, String storageGroup, BusinessLedger.Session session) {
        return track(tenantId, storageGroup, session, () -> {});
    }

    public BusinessLedger.Session track(long tenantId, String storageGroup, BusinessLedger.Session session,
                                        Runnable afterClose) {
        if (session == null) throw new IllegalArgumentException("Business session is missing");
        var key = new Key(tenantId, storageGroup);
        try {
            slots.compute(key, (ignored, current) -> {
                var slot = current == null ? new Slot() : current;
                if (slot.blocked) throw new IllegalStateException("Business storage group is draining");
                slot.active++;
                return slot;
            });
        } catch (RuntimeException denied) {
            session.close();
            afterClose.run();
            throw denied;
        }
        return new Tracked(session, () -> {
            try { afterClose.run(); }
            finally { slots.compute(key, (ignored, slot) -> {
                if (slot == null || --slot.active < 0) throw new IllegalStateException("Business session lease underflow");
                return slot.active == 0 && !slot.blocked ? null : slot;
            }); }
        });
    }

    /** Rejects new local sessions before reporting the currently active count. */
    public int block(long tenantId, String storageGroup) {
        var key = new Key(tenantId, storageGroup);
        var count = new int[1];
        slots.compute(key, (ignored, current) -> {
            var slot = current == null ? new Slot() : current;
            slot.blocked = true;
            count[0] = slot.active;
            return slot;
        });
        return count[0];
    }

    public int active(long tenantId, String storageGroup) {
        var slot = slots.get(new Key(tenantId, storageGroup));
        return slot == null ? 0 : slot.active;
    }

    /** Only the migration coordinator may call after binding state is durably settled. */
    public void unblock(long tenantId, String storageGroup) {
        slots.computeIfPresent(new Key(tenantId, storageGroup), (ignored, slot) -> {
            slot.blocked = false;
            return slot.active == 0 ? null : slot;
        });
    }

    private static final class Tracked implements BusinessLedger.Session {
        private final BusinessLedger.Session session;
        private final Runnable release;
        private final AtomicBoolean closed = new AtomicBoolean();
        Tracked(BusinessLedger.Session session, Runnable release) {
            this.session = session; this.release = release;
        }
        private void requireOpen() {
            if (closed.get()) throw new IllegalStateException("Business storage session is closed");
        }
        @Override public <T> RequestExecution.Outcome<T> executeLocal(RequestCommand command, RequestExecution.Work<T> work) {
            requireOpen(); return session.executeLocal(command, work);
        }
        @Override public RequestReceipt receipt(String operationId) { requireOpen(); return session.receipt(operationId); }
        @Override public com.sma.admin.common.persistence.DataSourceIdentity resourceIdentity() {
            requireOpen(); return session.resourceIdentity();
        }
        @Override public RequestReceipt receiptByKey(String key) { requireOpen(); return session.receiptByKey(key); }
        @Override public void invalidate(String region) { requireOpen(); session.invalidate(region); }
        @Override public long authoritativeRevision(String region) { requireOpen(); return session.authoritativeRevision(region); }
        @Override public long enqueue(String eventType, String eventKey, byte[] payload) {
            requireOpen(); return session.enqueue(eventType, eventKey, payload);
        }
        @Override public <T> T query(Supplier<T> read) { requireOpen(); return session.query(read); }
        @Override public void close() {
            if (closed.compareAndSet(false, true)) {
                // A failed physical close keeps the durable lease outstanding for operator review.
                session.close();
                release.run();
            }
        }
    }
}
