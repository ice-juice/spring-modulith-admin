package com.sma.admin.common.internal.deployment;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.RequestContextHolder;
import com.sma.admin.common.deployment.DeploymentEvidence;
import com.sma.admin.common.deployment.DeploymentWork;
import com.sma.admin.common.security.ActorContext;
import com.sma.admin.common.security.ActorRef;
import com.sma.admin.common.security.TenantEntryScope;

/** One admission/drain linearization point. Non-final for Modulith lifecycle-listener observability. */
class DeploymentWorkCoordinator implements DeploymentWork, ApplicationListener<ContextClosedEvent> {
    static final int MAX_ACTIVE_WORK = 1024;
    private final ApplicationContext context;
    private final BooleanSupplier qualified;
    private boolean draining;
    private int active;

    DeploymentWorkCoordinator(ApplicationContext context, BooleanSupplier qualified) {
        this.context = context; this.qualified = qualified;
    }

    @Override public Optional<Permit> tryStart() {
        requireWorker();
        synchronized (this) {
            if (!accepting()) return Optional.empty();
            active++;
            return Optional.of(new WorkPermit());
        }
    }

    @Override public synchronized Snapshot snapshot() { return new Snapshot(accepting(), draining, active); }

    synchronized boolean isDraining() { return draining; }

    /** Internal deployment control only. No HTTP endpoint and no authenticated/job/transaction caller. */
    boolean beginDrain() {
        DeploymentEvidence.requireHost();
        return stopAdmission();
    }

    private synchronized boolean stopAdmission() {
        if (draining) return false;
        draining = true;
        return true;
    }

    @Override public void onApplicationEvent(ContextClosedEvent event) {
        if (event.getApplicationContext() == context) stopAdmission();
    }

    /** Caller holds the monitor; qualification reads only bounded in-memory deployment observations. */
    private boolean accepting() {
        if (draining || active >= MAX_ACTIVE_WORK) return false;
        try { return qualified.getAsBoolean(); }
        catch (RuntimeException unavailable) { return false; }
    }

    private static void requireWorker() {
        if (TenantEntryScope.isHttpRequest() || RequestContextHolder.getRequestAttributes() != null)
            throw new AccessDeniedException("Deployment work requires trusted host execution");
        if (SecurityContextHolder.getContext().getAuthentication() != null
                && ActorContext.require().kind() != ActorRef.Kind.SYSTEM_JOB)
            throw new AccessDeniedException("Deployment work requires trusted host execution");
        if (TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Deployment work admission must precede the transaction");
    }

    private final class WorkPermit implements Permit {
        private final AtomicBoolean released = new AtomicBoolean();
        @Override public void close() {
            if (!released.compareAndSet(false, true)) return;
            synchronized (DeploymentWorkCoordinator.this) { active--; }
        }
    }
}
