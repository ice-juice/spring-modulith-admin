package com.sma.admin.tenant.internal.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.sma.admin.common.deployment.DeploymentNodes;
import com.sma.admin.tenant.storage.StorageMigrationEvidence;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Platform task and write gate. Physical copy is verified outside these short transactions. */
@Service
public class StorageSwitchState {
    public record Task(long id, long bindingId, long tenantId, String storageGroup,
                       String sourceId, long sourceRevision, String targetId, long targetRevision,
                       long bindingVersion, long generation, String state) {
        StorageMigrationEvidence.Target target() {
            return new StorageMigrationEvidence.Target(tenantId, storageGroup, sourceId, sourceRevision,
                    targetId, targetRevision);
        }
    }
    private record NodeProof(String nodeId, String processId, long epoch, Instant drainedAt) {}
    private final JdbcTemplate sql;
    private final StorageDirectory directory;
    private final PlatformOperators operators;
    private final DeploymentNodes nodes;
    private final StorageSessionTracker sessions;
    private final TenantPlatformProperties platform;

    public StorageSwitchState(JdbcTemplate sql, StorageDirectory directory, PlatformOperators operators,
                              DeploymentNodes nodes, StorageSessionTracker sessions, TenantPlatformProperties platform) {
        this.sql = sql; this.directory = directory; this.operators = operators;
        this.nodes = nodes; this.sessions = sessions; this.platform = platform;
    }

    @Transactional
    public Task begin(long bindingId, long expectedVersion, String targetId, long targetRevision, String operationKey) {
        operators.require();
        if (bindingId < 1 || expectedVersion < 1 || targetRevision < 1 || operationKey == null
                || !operationKey.matches("[a-zA-Z0-9_-]{16,64}"))
            throw new IllegalArgumentException("Invalid business storage switch request");
        var binding = directory.bindingForSwitch(bindingId);
        if (binding == null) throw new IllegalStateException("Business storage binding is unavailable");
        var replay = taskByKey(operationKey);
        if (replay != null) {
            if (replay.bindingId() != bindingId || replay.bindingVersion() != expectedVersion
                    || !replay.targetId().equals(targetId) || replay.targetRevision() != targetRevision)
                throw new IllegalStateException("Business storage switch key belongs to another intent");
            return replay;
        }
        if (!"ACTIVE".equals(binding.getState()) || binding.getBindingVersion() != expectedVersion)
            throw new IllegalStateException("Business storage binding is not active at the expected version");
        if (binding.getResourceId().equals(targetId) && binding.getResourceRevision() == targetRevision)
            throw new IllegalArgumentException("Business storage target is already active");
        var target = directory.resource(targetId, targetRevision);
        if (target == null || !"REGISTERED".equals(target.getState())
                || !"v1".equals(binding.getSchemaCompatibility()))
            throw new IllegalStateException("Business storage switch target is unavailable");
        if (sql.queryForObject("SELECT COUNT(*) FROM tenant_storage_switch WHERE binding_id=? "
                + "AND state NOT IN ('SWITCHED','ABORTED')", Long.class, bindingId) != 0)
            throw new IllegalStateException("Business storage binding has an unfinished switch");
        long id = IdWorker.getId();
        sql.update("INSERT INTO tenant_storage_switch (id,tenant_id,operation_id,binding_id,owner_tenant_id,"
                + "storage_group,source_resource_id,source_revision,target_resource_id,target_revision,"
                + "expected_binding_version,stop_generation,state,created_at,updated_at) "
                + "VALUES (?,?,?,?,?,?,?,?,?,?,?,0,'PREPARING',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))",
                id, platform.platformTenantId(), operationKey, bindingId, binding.getOwnerTenantId(),
                binding.getStorageGroup(), binding.getResourceId(), binding.getResourceRevision(),
                targetId, targetRevision, expectedVersion);
        return task(id, false);
    }

    @Transactional
    public Task copied(long id, StorageMigrationEvidence.Snapshot proof) {
        operators.require();
        var task = task(id, true);
        if (!"PREPARING".equals(task.state())) throw new IllegalStateException("Switch is not preparing");
        validate(proof);
        if (proof.sourceOutbox() != proof.targetOutbox())
            throw new IllegalStateException("Initial copy Outbox watermark differs");
        sql.update("UPDATE tenant_storage_switch SET backup_ref=?,copy_digest=?,copied_rows=?,source_outbox=?,"
                + "state='COPIED',updated_at=UTC_TIMESTAMP(3) WHERE id=?", proof.backupRef(), proof.sourceDigest(),
                proof.sourceRows(), proof.sourceOutbox(), id);
        return task(id, false);
    }

    @Transactional
    public Task stop(long id) {
        operators.require();
        var task = task(id, true);
        if (!"COPIED".equals(task.state())) throw new IllegalStateException("Switch copy is not verified");
        var binding = directory.bindingForSwitch(task.bindingId());
        requireSource(task, binding);
        var cohort = nodes.snapshot();
        if (cohort.members().isEmpty() || cohort.members().size() > 32
                || cohort.manifestHash() == null || !cohort.manifestHash().matches("[0-9a-f]{64}")
                || cohort.manifestVersion() < 1)
            throw new IllegalStateException("Deployment node cohort is unavailable");
        for (var member : cohort.members()) {
            if (!"ONLINE".equals(member.state()) || member.processId() == null || member.epoch() < 1
                    || member.leaseUntil() == null || !member.leaseUntil().isAfter(cohort.capturedAt()))
                throw new IllegalStateException("Deployment node cohort is not fully online");
        }
        sql.update("UPDATE tenant_storage_binding SET state='DRAINING',updated_at=UTC_TIMESTAMP(3) WHERE id=?", task.bindingId());
        sql.update("UPDATE tenant_storage_switch SET state='DRAINING',stop_generation=stop_generation+1,"
                + "manifest_hash=?,manifest_version=?,updated_at=UTC_TIMESTAMP(3) WHERE id=?",
                cohort.manifestHash(), cohort.manifestVersion(), id);
        for (var member : cohort.members())
            sql.update("INSERT INTO tenant_storage_switch_node (switch_id,node_id,process_id,epoch) VALUES (?,?,?,?)",
                    id, member.nodeId(), member.processId(), member.epoch());
        return task(id, false);
    }

    /** Called by the host scheduler on each member; no caller-supplied node identity or drain flag. */
    @Transactional
    public void acknowledgeLocal(long id) {
        if (SecurityContextHolder.getContext().getAuthentication() != null)
            throw new AccessDeniedException("Host storage drain worker required");
        var task = task(id, true);
        if (!"DRAINING".equals(task.state())) return;
        var local = nodes.local();
        var matched = sql.queryForObject("SELECT COUNT(*) FROM tenant_storage_switch_node WHERE switch_id=? "
                        + "AND node_id=? AND process_id=? AND epoch=?", Long.class,
                id, local.nodeId(), local.processId(), local.epoch());
        if (matched == null || matched != 1) return;
        if (sessions.block(task.tenantId(), task.storageGroup()) != 0) return;
        if (sql.queryForObject("SELECT COUNT(*) FROM tenant_storage_session_lease WHERE binding_id=?",
                Long.class, task.bindingId()) != 0) return;
        sql.update("UPDATE tenant_storage_switch_node SET drained_at=UTC_TIMESTAMP(3) "
                + "WHERE switch_id=? AND node_id=? AND drained_at IS NULL", id, local.nodeId());
    }

    @Transactional
    public Task ready(long id, StorageMigrationEvidence.Snapshot proof) {
        operators.require();
        var task = task(id, true);
        if (!"DRAINING".equals(task.state())) throw new IllegalStateException("Switch is not draining");
        var binding = directory.bindingForSwitch(task.bindingId());
        if (binding == null || !"DRAINING".equals(binding.getState())
                || binding.getBindingVersion() != task.bindingVersion())
            throw new IllegalStateException("Switch write gate changed");
        var cohort = nodes.snapshot();
        var expected = sql.query("SELECT node_id,process_id,epoch,drained_at FROM tenant_storage_switch_node "
                + "WHERE switch_id=? ORDER BY node_id", (rs, index) -> {
                    var drained = rs.getObject("drained_at", LocalDateTime.class);
                    return new NodeProof(rs.getString("node_id"), rs.getString("process_id"), rs.getLong("epoch"),
                            drained == null ? null : drained.toInstant(ZoneOffset.UTC));
                }, id);
        var stored = sql.queryForMap("SELECT manifest_hash,manifest_version,source_outbox,backup_ref "
                + "FROM tenant_storage_switch WHERE id=?", id);
        if (!Objects.equals(stored.get("manifest_hash"), cohort.manifestHash())
                || ((Number) stored.get("manifest_version")).longValue() != cohort.manifestVersion()
                || expected.size() != cohort.members().size())
            throw new IllegalStateException("Deployment node cohort changed");
        for (int index = 0; index < expected.size(); index++) {
            var row = expected.get(index); var member = cohort.members().get(index);
            if (!Objects.equals(row.nodeId(), member.nodeId())
                    || !Objects.equals(row.processId(), member.processId())
                    || row.epoch() != member.epoch()
                    || row.drainedAt() == null || !"ONLINE".equals(member.state())
                    || member.leaseUntil() == null || !member.leaseUntil().isAfter(cohort.capturedAt()))
                throw new IllegalStateException("Deployment node has no current drain proof");
        }
        if (sql.queryForObject("SELECT COUNT(*) FROM tenant_storage_session_lease WHERE binding_id=?",
                Long.class, task.bindingId()) != 0)
            throw new IllegalStateException("Business storage sessions have not drained");
        validate(proof);
        if (!proof.backupRef().equals(stored.get("backup_ref")))
            throw new IllegalStateException("Physical storage backup reference changed");
        if (proof.sourceOutbox() != proof.targetOutbox()
                || proof.sourceOutbox() < ((Number) stored.get("source_outbox")).longValue())
            throw new IllegalStateException("Final copy Outbox watermark differs");
        for (var row : expected) {
            if (proof.verifiedAt().isBefore(row.drainedAt()))
                throw new IllegalStateException("Final copy predates node drain proof");
        }
        sql.update("UPDATE tenant_storage_switch SET final_digest=?,final_rows=?,final_outbox=?,ready_at=UTC_TIMESTAMP(3),"
                + "state='READY',updated_at=UTC_TIMESTAMP(3) WHERE id=?", proof.sourceDigest(), proof.sourceRows(),
                proof.sourceOutbox(), id);
        return task(id, false);
    }

    /** No physical I/O here: coordinator verified the target immediately before this transaction. */
    @Transactional
    public Task commit(long id, StorageMigrationEvidence.Snapshot proof) {
        operators.require();
        var task = task(id, true);
        if ("SWITCHED".equals(task.state())) return task;
        if (!"READY".equals(task.state())) throw new IllegalStateException("Switch is not ready");
        var binding = directory.bindingForSwitch(task.bindingId());
        if (binding == null || !"DRAINING".equals(binding.getState())
                || binding.getBindingVersion() != task.bindingVersion()
                || !binding.getResourceId().equals(task.sourceId())
                || binding.getResourceRevision() != task.sourceRevision()
                || binding.getOwnerTenantId() != task.tenantId()
                || !binding.getStorageGroup().equals(task.storageGroup()))
            throw new IllegalStateException("Switch binding or write gate changed");
        var target = directory.resource(task.targetId(), task.targetRevision());
        if (target == null || !"REGISTERED".equals(target.getState()))
            throw new IllegalStateException("Switch target resource is unavailable");
        if (sql.queryForObject("SELECT COUNT(*) FROM tenant_storage_session_lease WHERE binding_id=?",
                Long.class, task.bindingId()) != 0)
            throw new IllegalStateException("Business storage sessions reappeared");
        var stored = sql.queryForMap("SELECT manifest_hash,manifest_version,backup_ref,final_digest,final_rows,final_outbox,ready_at "
                + "FROM tenant_storage_switch WHERE id=?", id);
        validate(proof);
        var readyAt = sql.queryForObject("SELECT ready_at FROM tenant_storage_switch WHERE id=?",
                (rs, index) -> rs.getObject(1, LocalDateTime.class).toInstant(ZoneOffset.UTC), id);
        if (!proof.backupRef().equals(stored.get("backup_ref"))
                || !proof.sourceDigest().equals(stored.get("final_digest"))
                || proof.sourceRows() != ((Number) stored.get("final_rows")).longValue()
                || proof.sourceOutbox() != ((Number) stored.get("final_outbox")).longValue()
                || proof.targetOutbox() != proof.sourceOutbox()
                || !proof.verifiedAt().isAfter(readyAt))
            throw new IllegalStateException("Final physical copy proof changed");
        var members = sql.query("SELECT node_id,process_id,epoch,drained_at FROM tenant_storage_switch_node "
                + "WHERE switch_id=? ORDER BY node_id", (rs, index) -> {
                    if (rs.getObject("drained_at", LocalDateTime.class) == null)
                        throw new IllegalStateException("Deployment node has not drained");
                    return new DeploymentNodes.Member(rs.getString("node_id"), rs.getString("process_id"),
                            rs.getLong("epoch"), null, "ONLINE");
                }, id);
        var frozen = new DeploymentNodes.Snapshot((String) stored.get("manifest_hash"),
                ((Number) stored.get("manifest_version")).longValue(), Instant.now(), members);
        nodes.requireCurrent(frozen);
        sql.update("UPDATE tenant_storage_binding SET resource_id=?,resource_revision=?,binding_version=binding_version+1,"
                + "state='ACTIVE',updated_at=UTC_TIMESTAMP(3) WHERE id=?", task.targetId(), task.targetRevision(),
                task.bindingId());
        sql.update("UPDATE tenant_storage_switch SET state='SWITCHED',updated_at=UTC_TIMESTAMP(3) WHERE id=?", id);
        return task(id, false);
    }

    /** Before switch only; after target writes, use a new reverse migration task. */
    @Transactional
    public Task abort(long id) {
        operators.require();
        var task = task(id, true);
        if ("ABORTED".equals(task.state())) return task;
        if ("SWITCHED".equals(task.state()))
            throw new IllegalStateException("Switched storage requires reverse migration");
        if (!List.of("PREPARING", "COPIED", "DRAINING", "READY").contains(task.state()))
            throw new IllegalStateException("Switch cannot be aborted without review");
        var binding = directory.bindingForSwitch(task.bindingId());
        if (binding == null || binding.getBindingVersion() != task.bindingVersion()
                || !binding.getResourceId().equals(task.sourceId())
                || binding.getResourceRevision() != task.sourceRevision())
            throw new IllegalStateException("Switch source changed");
        if ("DRAINING".equals(task.state()) || "READY".equals(task.state())) {
            if (!"DRAINING".equals(binding.getState()))
                throw new IllegalStateException("Switch write gate changed");
            sql.update("UPDATE tenant_storage_binding SET state='ACTIVE',binding_version=binding_version+1,"
                    + "updated_at=UTC_TIMESTAMP(3) WHERE id=?", task.bindingId());
        } else if (!"ACTIVE".equals(binding.getState()))
            throw new IllegalStateException("Switch source is unavailable");
        sql.update("UPDATE tenant_storage_switch SET state='ABORTED',updated_at=UTC_TIMESTAMP(3) WHERE id=?", id);
        return task(id, false);
    }

    @Transactional(readOnly = true)
    public List<Long> draining(long afterId) {
        return sql.queryForList("SELECT id FROM tenant_storage_switch WHERE state='DRAINING' "
                + "AND id>? ORDER BY id LIMIT 20", Long.class, afterId);
    }


    @Transactional(readOnly = true)
    public Task inspect(long id) { operators.require(); return task(id, false); }

    private Task taskByKey(String operationKey) {
        var rows = sql.query("SELECT * FROM tenant_storage_switch WHERE tenant_id=? AND operation_id=?",
                this::map, platform.platformTenantId(), operationKey);
        return rows.isEmpty() ? null : rows.getFirst();
    }
    private Task task(long id, boolean lock) {
        var rows = sql.query("SELECT * FROM tenant_storage_switch WHERE id=?" + (lock ? " FOR UPDATE" : ""), this::map, id);
        if (rows.size() != 1) throw new IllegalStateException("Business storage switch is unavailable");
        return rows.getFirst();
    }
    private Task map(ResultSet rs, int ignored) throws SQLException {
        return new Task(rs.getLong("id"), rs.getLong("binding_id"), rs.getLong("owner_tenant_id"),
                rs.getString("storage_group"), rs.getString("source_resource_id"), rs.getLong("source_revision"),
                rs.getString("target_resource_id"), rs.getLong("target_revision"),
                rs.getLong("expected_binding_version"), rs.getLong("stop_generation"), rs.getString("state"));
    }
    private static void requireSource(Task task, com.sma.admin.tenant.internal.persistence.StorageBindingEntity binding) {
        if (binding == null || !"ACTIVE".equals(binding.getState())
                || binding.getBindingVersion() != task.bindingVersion()
                || !binding.getResourceId().equals(task.sourceId())
                || binding.getResourceRevision() != task.sourceRevision()
                || binding.getOwnerTenantId() != task.tenantId()
                || !binding.getStorageGroup().equals(task.storageGroup()))
            throw new IllegalStateException("Business storage switch source changed");
    }
    private static void validate(StorageMigrationEvidence.Snapshot proof) {
        if (proof == null || proof.backupRef() == null || !proof.backupRef().matches("[a-zA-Z0-9/_:.@-]{8,160}")
                || proof.sourceDigest() == null || !proof.sourceDigest().matches("[0-9a-f]{64}")
                || !proof.sourceDigest().equals(proof.targetDigest())
                || proof.sourceRows() < 0 || proof.sourceRows() != proof.targetRows()
                || proof.sourceOutbox() < 0 || proof.targetOutbox() < 0
                || proof.verifiedAt() == null || proof.verifiedAt().isAfter(Instant.now().plusSeconds(1)))
            throw new IllegalStateException("Physical storage copy evidence is unavailable");
    }
}
