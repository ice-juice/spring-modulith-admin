package com.sma.admin.tenant.internal.service;

import com.sma.admin.common.deployment.DeploymentNodes;
import com.sma.admin.tenant.internal.persistence.StorageBindingEntity;
import java.util.UUID;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Durable admission serialized with the binding stop; leases never expire on a timer. */
@Service
public class StorageSessionLeaseStore {
    private final JdbcTemplate sql;
    public StorageSessionLeaseStore(JdbcTemplate sql) { this.sql = sql; }

    @Transactional
    public String register(StorageBindingEntity observed, DeploymentNodes.Member local) {
        if (observed == null || local == null || !"ONLINE".equals(local.state())
                || local.epoch() < 1 || local.processId() == null
                || local.leaseUntil() == null || !local.leaseUntil().isAfter(Instant.now()))
            throw new IllegalStateException("Business storage node identity is unavailable");
        var current = sql.queryForList("SELECT owner_tenant_id,storage_group,binding_version,state "
                + "FROM tenant_storage_binding WHERE id=? AND deleted=0 FOR UPDATE", observed.getId());
        if (current.size() != 1 || !"ACTIVE".equals(current.getFirst().get("state"))
                || !observed.getOwnerTenantId().equals(((Number) current.getFirst().get("owner_tenant_id")).longValue())
                || !observed.getStorageGroup().equals(current.getFirst().get("storage_group"))
                || !observed.getBindingVersion().equals(((Number) current.getFirst().get("binding_version")).longValue()))
            throw new IllegalStateException("Business storage binding stopped or changed before session admission");
        var lease = UUID.randomUUID().toString();
        sql.update("INSERT INTO tenant_storage_session_lease "
                + "(lease_id,binding_id,owner_tenant_id,storage_group,node_id,process_id,binding_version,opened_at) "
                + "VALUES (?,?,?,?,?,?,?,UTC_TIMESTAMP(3))", lease, observed.getId(), observed.getOwnerTenantId(),
                observed.getStorageGroup(), local.nodeId(), local.processId(), observed.getBindingVersion());
        return lease;
    }

    @Transactional
    public void release(String lease) {
        if (sql.update("DELETE FROM tenant_storage_session_lease WHERE lease_id=?", lease) != 1)
            throw new IllegalStateException("Business storage session lease is missing");
    }
}
