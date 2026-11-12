package com.sma.admin.common.internal.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.sma.admin.common.internal.persistence.*;
import com.sma.admin.common.internal.security.RequestFingerprintKeys;
import com.sma.admin.common.internal.security.RequestGovernanceProperties;
import com.sma.admin.common.request.*;
import com.sma.admin.common.security.ActorContext;
import com.sma.admin.common.security.CurrentUser;
import static com.sma.admin.common.request.RequestRejected.Reason.*;

/** Only local, single-resource DB work belongs here. A failed receipt is never silently rerun. */
@Service
@EnableConfigurationProperties(RequestGovernanceProperties.class)
public class RequestExecutionService implements RequestExecution, RequestAdmission, com.sma.admin.common.deployment.DeploymentEvidenceSource {
    private final RequestExecutionMapper executions;
    private final RequestMutexMapper mutexes;
    private final RequestBudgetMapper budgets;
    private final TransactionTemplate shortTx;
    private final TransactionTemplate workTx;
    private final RequestFingerprintKeys fingerprintKeys;
    private final long maxRecords;
    private final Semaphore slots;
    private final ThreadLocal<Boolean> admitted = new ThreadLocal<>();

    @Autowired
    public RequestExecutionService(RequestExecutionMapper executions, RequestMutexMapper mutexes, RequestBudgetMapper budgets,
            PlatformTransactionManager transactions, RequestGovernanceProperties properties) {
        this.executions = executions; this.mutexes = mutexes; this.budgets = budgets;
        this.fingerprintKeys = new RequestFingerprintKeys(properties.fingerprintKeyId(), properties.fingerprintKey(), properties.previousFingerprintKeys());
        this.maxRecords = properties.maxRecordsPerTenant();
        this.slots = new Semaphore(properties.maxConcurrent());
        shortTx = template(transactions, 3); workTx = template(transactions, 30);
    }
    public RequestExecutionService(RequestExecutionMapper executions, RequestMutexMapper mutexes, RequestBudgetMapper budgets,
            PlatformTransactionManager transactions, String fingerprintKey, long maxRecords) {
        this(executions, mutexes, budgets, transactions, new RequestGovernanceProperties(fingerprintKey, "v1", "", maxRecords, 8));
    }
    public RequestExecutionService(RequestExecutionMapper executions, RequestMutexMapper mutexes, RequestBudgetMapper budgets,
            PlatformTransactionManager transactions, String fingerprintKey, long maxRecords, String activeKeyId, String previousKeys) {
        this(executions, mutexes, budgets, transactions, new RequestGovernanceProperties(fingerprintKey, activeKeyId, previousKeys, maxRecords, 8));
    }
    private static TransactionTemplate template(PlatformTransactionManager manager, int timeout) {
        var tx = new TransactionTemplate(manager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED); tx.setTimeout(timeout); return tx;
    }

    @Override public String id() { return "common.request-fingerprint"; }
    @Override public com.sma.admin.common.deployment.DeploymentEvidence.Proof collect(
            com.sma.admin.common.deployment.DeploymentEvidence.Challenge challenge) {
        return fingerprintKeys.deploymentEvidence(challenge);
    }

    @Override public <T> T execute(Work<T> work) {
        if (TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Native admission must not inherit an outer transaction");
        return admit(() -> { ActorContext.require(); work.authorize(); return work.execute(); });
    }

    @Override public <T> Outcome<T> executeLocal(RequestCommand command, Work<T> work) {
        return admit(() -> executeBounded(command, work));
    }
    private <T> T admit(java.util.function.Supplier<T> action) {
        // A native adapter may delegate to this same service's local protocol.
        // That nested synchronous call belongs to the already-admitted request.
        boolean owner = admitted.get() == null;
        if (owner) {
            if (!slots.tryAcquire()) throw new RequestRejected(UNAVAILABLE, 1);
            admitted.set(Boolean.TRUE);
        }
        try { return action.get(); }
        finally { if (owner) { admitted.remove(); slots.release(); } }
    }
    private <T> Outcome<T> executeBounded(RequestCommand command, Work<T> work) {
        if (TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Request execution must own the outermost local transaction");
        String actor = actorHash(); requirePermission(command.permission()); work.authorize();
        String resource = hash(command.resource()), key = hash(command.key());
        String input = fingerprint(command, actor, fingerprintKeys.activeId());
        var found = shortTx.execute(status -> existing(actor, command.operation(), resource, key));
        if (found != null) return replay(found, fingerprint(command, actor, found.getFingerprintKeyId()));

        long id = IdWorker.getId();
        RequestExecutionEntity claim;
        try {
            claim = shortTx.execute(status -> {
                String capacity = hash("ledger-capacity"); ensureMutex(capacity);
                // Capacity serializes a short reservation, not business work. Independent users may
                // wait within shortTx's existing three-second deadline; resource conflicts below
                // remain NOWAIT. Immediate rejection here makes ordinary cross-node writes fail.
                mutexes.selectOne(new LambdaQueryWrapper<RequestMutexEntity>().eq(RequestMutexEntity::getResourceHash, capacity).last("FOR UPDATE"));
                var current = existing(actor, command.operation(), resource, key);
                if (current != null) return current;
                if (executions.selectCount(new LambdaQueryWrapper<>()) >= maxRecords) throw new RequestRejected(UNAVAILABLE, 0);
                ensureMutex(hash(command.conflictGroup() + "\n" + command.resource()));
                var row = new RequestExecutionEntity(); row.setId(id); row.setPublicId(UUID.randomUUID().toString());
                row.setActorHash(actor); row.setOperation(command.operation()); row.setResourceHash(resource);
                row.setKeyHash(key); row.setInputHash(input); row.setFingerprintKeyId(fingerprintKeys.activeId()); row.setPermissionKey(command.permission()); row.setState("RUNNING");
                executions.insert(row); return executions.selectById(id);
            });
        } catch (DuplicateKeyException concurrent) {
            claim = shortTx.execute(status -> existing(actor, command.operation(), resource, key));
            if (claim == null) throw new RequestRejected(UNAVAILABLE, 1);
        } catch (DataAccessException unavailable) { throw new RequestRejected(UNAVAILABLE, 1); }
        if (claim.getId() != id) return replay(claim, fingerprint(command, actor, claim.getFingerprintKeyId()));

        var completion = new AtomicInteger(TransactionSynchronization.STATUS_UNKNOWN);
        var result = new AtomicReference<T>();
        try {
            return workTx.execute(status -> {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void afterCompletion(int state) { completion.set(state); }
                });
                try {
                    mutexes.selectOne(new LambdaQueryWrapper<RequestMutexEntity>()
                            .eq(RequestMutexEntity::getResourceHash, hash(command.conflictGroup() + "\n" + command.resource())).last("FOR UPDATE NOWAIT"));
                } catch (DataAccessException busy) { throw new RequestRejected(BUSY, 1); }
                var owned = executions.selectOne(new LambdaQueryWrapper<RequestExecutionEntity>().eq(RequestExecutionEntity::getId, id).last("FOR UPDATE NOWAIT"));
                if (owned == null || !owned.getState().equals("RUNNING")) throw new RequestRejected(CONFLICT, 0);
                requirePermission(command.permission()); work.authorize();
                result.set(work.execute());
                owned.setState("SUCCEEDED"); executions.updateById(owned);
                return new Outcome<>(receiptOf(executions.selectById(id)), false, result.get());
            });
        } catch (RuntimeException failure) {
            // Commit acknowledgement can be lost. The ledger and business write commit together.
            try {
                var outcome = shortTx.execute(status -> {
                    var row = executions.selectOne(new LambdaQueryWrapper<RequestExecutionEntity>().eq(RequestExecutionEntity::getId, id).last("FOR UPDATE NOWAIT"));
                    if (row.getState().equals("SUCCEEDED")) return new Outcome<T>(receiptOf(row), false, result.get());
                    row.setState(completion.get() == TransactionSynchronization.STATUS_ROLLED_BACK ? "FAILED_FINAL" : "UNKNOWN");
                    executions.updateById(row); return null;
                });
                if (outcome != null) return outcome;
            } catch (RuntimeException ignored) { /* Keep RUNNING rather than risk a duplicate after uncertain storage failure. */ }
            throw failure;
        }
    }

    private <T> Outcome<T> replay(RequestExecutionEntity row, String input) {
        if (!MessageDigest.isEqual(row.getInputHash().getBytes(StandardCharsets.US_ASCII), input.getBytes(StandardCharsets.US_ASCII)))
            throw new RequestRejected(CONFLICT, 0);
        requirePermission(row.getPermissionKey());
        return new Outcome<>(receiptOf(row), true, null);
    }
    private RequestExecutionEntity existing(String actor, String operation, String resource, String key) {
        var row = executions.selectOne(new LambdaQueryWrapper<RequestExecutionEntity>().eq(RequestExecutionEntity::getActorHash, actor).eq(RequestExecutionEntity::getKeyHash, key));
        if (row != null && (!row.getOperation().equals(operation) || !row.getResourceHash().equals(resource))) throw new RequestRejected(CONFLICT, 0);
        return row;
    }
    private void ensureMutex(String hash) {
        if (mutexes.selectCount(new LambdaQueryWrapper<RequestMutexEntity>().eq(RequestMutexEntity::getResourceHash, hash)) == 0)
            mutexes.ensure(IdWorker.getId(), ActorContext.tenantId(), hash);
    }
    @Override public RequestReceipt receipt(String operationId) {
        String actor = actorHash();
        if (operationId == null || !operationId.matches("[0-9a-fA-F-]{36}")) throw new RequestRejected(NOT_FOUND, 0);
        return shortTx.execute(status -> {
            var row = executions.selectOne(new LambdaQueryWrapper<RequestExecutionEntity>().eq(RequestExecutionEntity::getPublicId, operationId)
                    .eq(RequestExecutionEntity::getActorHash, actor));
            if (row == null) throw new RequestRejected(NOT_FOUND, 0);
            requirePermission(row.getPermissionKey()); return receiptOf(row);
        });
    }
    @Override public RequestReceipt receiptByKey(String key) {
        String actor = actorHash();
        if (key == null || !key.matches("[A-Za-z0-9_.:-]{16,128}")) throw new RequestRejected(NOT_FOUND, 0);
        return shortTx.execute(status -> {
            var row = executions.selectOne(new LambdaQueryWrapper<RequestExecutionEntity>().eq(RequestExecutionEntity::getActorHash, actor)
                    .eq(RequestExecutionEntity::getKeyHash, hash(key)));
            if (row == null) throw new RequestRejected(NOT_FOUND, 0);
            requirePermission(row.getPermissionKey()); return receiptOf(row);
        });
    }
    private RequestReceipt receiptOf(RequestExecutionEntity row) {
        var state = RequestReceipt.State.valueOf(row.getState());
        // No expiry releases execution rights. Stale work needs module-owned reconciliation.
        if (state == RequestReceipt.State.RUNNING && row.getCreatedAt().plusSeconds(60).isBefore(executions.now())) state = RequestReceipt.State.UNKNOWN;
        return new RequestReceipt(row.getPublicId(), row.getOperation(), state, row.getCreatedAt(), row.getUpdatedAt());
    }
    @Override public void claimBudget(String operation, int limit, Duration window) {
        claimFor(actorHash(), operation, limit, window);
    }
    @Override public void claimTenantBudget(String operation, int limit, Duration window) {
        ActorContext.require(); claimFor("TENANT_TOTAL", operation, limit, window);
    }
    private void claimFor(String actor, String operation, int limit, Duration window) {
        if (operation == null || !operation.matches("[a-z][a-z0-9.-]{1,95}") || limit < 1 || limit > 100000
                || window == null || window.toMillis() < 100 || window.compareTo(Duration.ofHours(1)) > 0)
            throw new IllegalArgumentException("Invalid request budget");
        try {
            shortTx.executeWithoutResult(status -> {
                String key = hash(actor + "\n" + operation);
                budgets.ensure(IdWorker.getId(), ActorContext.tenantId(), key);
                var row = budgets.selectOne(new LambdaQueryWrapper<RequestBudgetEntity>().eq(RequestBudgetEntity::getSubjectHash, key).last("FOR UPDATE NOWAIT"));
                var now = executions.now();
                if (!row.getExpiresAt().isAfter(now)) { row.setUsedCount(0); row.setExpiresAt(now.plus(window)); }
                if (row.getUsedCount() >= limit) throw new RequestRejected(RATE_LIMITED, Math.max(1, (Duration.between(now, row.getExpiresAt()).toMillis() + 999) / 1000));
                row.setUsedCount(row.getUsedCount() + 1); budgets.updateById(row);
                // Constant work per successful reservation. Native resource scopes
                // (for example upload chunks) must not accumulate expired rows forever.
                // Skip locked rows and retain a five-minute margin; never touch receipts/mutexes.
                var cutoff=now.minusSeconds(300);
                var expired=budgets.selectList(new LambdaQueryWrapper<RequestBudgetEntity>().select(RequestBudgetEntity::getId)
                        .lt(RequestBudgetEntity::getExpiresAt,cutoff).orderByAsc(RequestBudgetEntity::getExpiresAt)
                        .last("LIMIT 2 FOR UPDATE SKIP LOCKED"));
                for(var old:expired)budgets.deleteExpired(ActorContext.tenantId(),old.getId(),cutoff);
            });
        } catch (DataAccessException unavailable) { throw new RequestRejected(UNAVAILABLE, 1); }
    }
    private String actorHash() {
        var actor = ActorContext.require();
        String portal = switch (actor.kind()) { case ADMIN_USER, APPLICATION -> CurrentUser.portal(); default -> ""; };
        return hash(actor.kind() + "\n" + actor.tenantId() + "\n" + actor.subject().length() + ":" + actor.subject()
                + "\n" + actor.realmId() + "\n" + actor.clientId() + "\n" + portal);
    }
    private void requirePermission(String permission) {
        if (!permission.isEmpty() && !CurrentUser.require().permissions().contains(permission)) throw new AccessDeniedException("Request permission no longer granted");
    }
    private String fingerprint(RequestCommand command, String actor, String keyId) {
        return fingerprintKeys.digest(keyId, actor + "\n" + command.operation() + "\n" + hash(command.resource()) + "\n", command.input());
    }
    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
