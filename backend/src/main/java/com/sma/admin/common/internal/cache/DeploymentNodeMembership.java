package com.sma.admin.common.internal.cache;

import com.sma.admin.common.deployment.DeploymentNodes;
import com.sma.admin.common.internal.service.CacheNodeLeaseStore;
import com.sma.admin.common.cache.CacheNodeScope;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Exposes the configured cohort without granting tenant code access to cache internals. */
@Component
public class DeploymentNodeMembership implements DeploymentNodes {
    private final CacheNodeIdentity identity;
    private final CacheNodeLeaseStore leases;
    private final CacheNodeRuntime runtime;

    public DeploymentNodeMembership(CacheNodeIdentity identity, CacheNodeLeaseStore leases, CacheNodeRuntime runtime) {
        this.identity = identity; this.leases = leases; this.runtime = runtime;
    }

    @Override public Snapshot snapshot() {
        if (!identity.properties().enabled()) throw new IllegalStateException("Deployment node membership is disabled");
        var source = leases.snapshot(identity);
        return new Snapshot(source.manifestHash(), source.manifestVersion(), source.capturedAt(),
                source.members().stream().map(member -> new Member(member.nodeId(), member.processId(),
                        member.epoch() == null ? 0 : member.epoch(), member.leaseUntil(), member.stateAtCapture())).toList());
    }

    @Override public Member local() {
        var lease = runtime.currentLease();
        if (lease == null || !identity.properties().id().equals(lease.nodeId())
                || !identity.processId().equals(lease.processId()))
            throw new IllegalStateException("Local deployment node has no current lease");
        return new Member(lease.nodeId(), lease.processId(), lease.epoch(), lease.until(), "ONLINE");
    }

    @Override public void requireCurrent(Snapshot expected) {
        if (expected == null || expected.members().isEmpty())
            throw new IllegalStateException("Deployment node cohort is missing");
        var frozen = new CacheNodeScope(expected.manifestHash(), expected.manifestVersion(), expected.capturedAt(),
                expected.members().stream().map(member -> new CacheNodeScope.Member(member.nodeId(),
                        member.processId(), member.epoch(), member.leaseUntil(), member.state())).toList());
        leases.validateFrozen(identity, frozen);
        var current = snapshot();
        if (current.members().size() != expected.members().size())
            throw new IllegalStateException("Deployment node cohort changed");
        for (int index = 0; index < current.members().size(); index++) {
            var before = expected.members().get(index); var after = current.members().get(index);
            if (!Objects.equals(before.nodeId(), after.nodeId())
                    || !Objects.equals(before.processId(), after.processId()) || before.epoch() != after.epoch()
                    || !"ONLINE".equals(after.state()) || after.leaseUntil() == null
                    || !after.leaseUntil().isAfter(current.capturedAt()))
                throw new IllegalStateException("Deployment node is no longer online");
        }
    }
}
