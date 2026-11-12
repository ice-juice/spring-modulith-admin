package com.sma.admin.common.internal.deployment;

import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.LivenessState;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;

/** Admission snapshot only: no database, filesystem, credential lookup or active probing on HTTP threads. */
class DeploymentTrafficGate implements ApplicationListener<ContextClosedEvent> {
    record Status(String state) { boolean accepts() { return state.equals("ACCEPTING"); } }
    private final ApplicationAvailability availability;
    private final DeploymentEvidenceRuntime evidence;
    private final SharedStorageRuntime storage;
    private final ApplicationContext context;
    private final DeploymentWorkCoordinator work;

    DeploymentTrafficGate(ApplicationAvailability availability, DeploymentEvidenceRuntime evidence,
                          SharedStorageRuntime storage, ApplicationContext context, DeploymentWorkCoordinator work) {
        this.availability = availability; this.evidence = evidence; this.storage = storage; this.context = context; this.work = work;
    }

    Status status() {
        if (work.isDraining()) return new Status("DRAINING");
        if (availability.getLivenessState() != LivenessState.CORRECT
                || availability.getReadinessState() != ReadinessState.ACCEPTING_TRAFFIC)
            return new Status("NOT_READY");
        try {
            if (!evidence.status().matches()) return new Status("WAITING_CONFIGURATION");
            if (!storage.status().ready()) return new Status("WAITING_STORAGE");
        } catch (RuntimeException unavailable) {
            return new Status("UNAVAILABLE");
        }
        // A close event during observation must not re-open admission.
        return new Status(work.isDraining() ? "DRAINING" : "ACCEPTING");
    }

    @Override public void onApplicationEvent(ContextClosedEvent event) {
        if (event.getApplicationContext() == context) work.onApplicationEvent(event);
    }
}
