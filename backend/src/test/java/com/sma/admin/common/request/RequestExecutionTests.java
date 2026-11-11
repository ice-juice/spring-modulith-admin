package com.sma.admin.common.request;

import static org.assertj.core.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.*;
import com.sma.admin.common.internal.persistence.*;
import com.sma.admin.common.internal.service.RequestExecutionService;
import com.sma.admin.common.security.*;
import com.sma.admin.config.internal.service.ParameterService;
import com.sma.admin.support.*;

@IntegrationTest
@TestPropertySource(properties = "sma.security.requests.fingerprint-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
class RequestExecutionTests {
    private static final String TEST_KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";
    @Autowired RequestExecution execution;
    @Autowired RequestExecutionMapper executions;
    @Autowired RequestMutexMapper mutexes;
    @Autowired RequestBudgetMapper budgets;
    @Autowired PlatformTransactionManager manager;
    @Autowired ParameterService parameters;

    @Test void expiredBudgetMaintenanceIsBoundedTenantScopedAndNeverRefundsLiveBudgets() {
        var staleIds=new java.util.ArrayList<Long>();
        long foreign=Users.as(actor(79117),()->expiredBudget('f'));
        Users.as(actor(79116),()->{
            execution.claimBudget("config.live",1,Duration.ofMinutes(1));
            for(char letter:new char[]{'a','b','c','d'})staleIds.add(expiredBudget(letter));
            execution.claimBudget("config.fresh",1,Duration.ofMinutes(1));
            assertThat(budgets.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RequestBudgetEntity>()
                    .in(RequestBudgetEntity::getId,staleIds))).isEqualTo(2);
            assertThatThrownBy(()->execution.claimBudget("config.live",1,Duration.ofMinutes(1)))
                    .isInstanceOfSatisfying(RequestRejected.class,error->assertThat(error.reason()).isEqualTo(RequestRejected.Reason.RATE_LIMITED));
            assertThat(budgets.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RequestBudgetEntity>()
                    .in(RequestBudgetEntity::getId,staleIds))).isEqualTo(2);return null;
        });
        Users.as(actor(79117),()->{assertThat(budgets.selectById(foreign)).isNotNull();return null;});
    }
    private long expiredBudget(char letter) {
        long id=com.baomidou.mybatisplus.core.toolkit.IdWorker.getId();
        budgets.ensure(id,CurrentUser.tenantId(),String.valueOf(letter).repeat(64));
        var row=budgets.selectById(id);row.setUsedCount(1);row.setExpiresAt(executions.now().minusSeconds(600));budgets.updateById(row);return id;
    }

    @Test void nativeAdapterCanDelegateToLocalProtocolWithASingleAdmissionSlot() {
        Users.as(actor(79115),()->{
            var single=new RequestExecutionService(executions,mutexes,budgets,manager,
                    new com.sma.admin.common.internal.security.RequestGovernanceProperties(TEST_KEY,"v1","",100000L,1));
            var command=command("native-delegate","input","native-delegate-key-01","");
            var first=single.execute(work(()->{},()->single.executeLocal(command,work(()->{},()->{group("native-delegate");return "done";}))));
            assertThat(first.receipt().state()).isEqualTo(RequestReceipt.State.SUCCEEDED);
            var replay=single.execute(work(()->{},()->single.executeLocal(command,work(()->{},()->{throw new AssertionError("must not repeat");}))));
            assertThat(replay.replayed()).isTrue();assertThat(replay.receipt().operationId()).isEqualTo(first.receipt().operationId());return null;
        });
    }

    @Test void localCommitIsDurableAndReplayNeverRepeatsTheMutationOrStoresBusinessPayload() {
        Users.as(actor(79101), () -> {
            var count = new AtomicInteger(); var authorizations = new AtomicInteger();
            var command = command("create", "original-input", "operation-key-0001", "");
            var work = work(authorizations::incrementAndGet, () -> { count.incrementAndGet(); group("once"); return "sensitive-result"; });
            var first = execution.executeLocal(command, work);
            var replay = secondNode(manager, 100000).executeLocal(command, work);
            assertThat(count.get()).isEqualTo(1); assertThat(authorizations.get()).isEqualTo(3);
            assertThat(first.result()).isEqualTo("sensitive-result"); assertThat(replay.result()).isNull();
            assertThat(replay.replayed()).isTrue(); assertThat(replay.receipt().state()).isEqualTo(RequestReceipt.State.SUCCEEDED);
            assertThat(replay.receipt().operationId()).isEqualTo(first.receipt().operationId());
            assertThat(execution.receipt(first.receipt().operationId()).state()).isEqualTo(RequestReceipt.State.SUCCEEDED);
            assertThat(execution.receiptByKey(command.key()).operationId()).isEqualTo(first.receipt().operationId());
            assertThatThrownBy(() -> execution.executeLocal(command("different-operation", "original-input", command.key(), ""), work))
                    .isInstanceOfSatisfying(RequestRejected.class, e -> assertThat(e.reason()).isEqualTo(RequestRejected.Reason.CONFLICT));
            assertThatThrownBy(() -> execution.executeLocal(command("create", "changed-input", "operation-key-0001", ""), work))
                    .isInstanceOfSatisfying(RequestRejected.class, e -> assertThat(e.reason()).isEqualTo(RequestRejected.Reason.CONFLICT));
            return null;
        });
    }
    @Test void transactionRollbackAlsoRollsBackTheBusinessWriteAndDoesNotAutomaticallyRetry() {
        Users.as(actor(79102), () -> {
            var command = command("rollback", "input", "operation-key-0002", "");
            assertThatThrownBy(() -> execution.executeLocal(command, work(() -> {}, () -> { group("rollback"); throw new IllegalStateException("deliberate business failure"); })))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(parameters.groups()).isEmpty();
            var replay = execution.executeLocal(command, work(() -> {}, () -> { throw new AssertionError("must not retry"); }));
            assertThat(replay.receipt().state()).isEqualTo(RequestReceipt.State.FAILED_FINAL); return null;
        });
    }
    @Test void concurrentNodesReturnTheSameRunningReceiptAndConflictingWorkCannotEnter() throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1); var count = new AtomicInteger();
        var command = command("concurrent", "input", "operation-key-0003", "");
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = pool.submit(() -> Users.as(actor(79103), () -> execution.executeLocal(command, work(() -> {}, () -> {
                count.incrementAndGet(); entered.countDown();
                try { if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("test release timeout"); }
                catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException(ex); }
                group("concurrent"); return "ok";
            }))));
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
            try {
                Users.as(actor(79103), () -> {
                    var replay = secondNode(manager, 100000).executeLocal(command, work(() -> {}, () -> { throw new AssertionError("duplicate entered"); }));
                    assertThat(replay.receipt().state()).isEqualTo(RequestReceipt.State.RUNNING);
                    assertThatThrownBy(() -> secondNode(manager, 100000).executeLocal(command("concurrent", "input", "operation-key-OTHER", ""), work(() -> {}, () -> "must-not-run")))
                            .isInstanceOfSatisfying(RequestRejected.class, e -> assertThat(e.reason()).isEqualTo(RequestRejected.Reason.BUSY));
                    return null;
                });
            } finally { release.countDown(); }
            assertThat(first.get(10, TimeUnit.SECONDS).receipt().state()).isEqualTo(RequestReceipt.State.SUCCEEDED);
            assertThat(count.get()).isEqualTo(1);
        }
    }
    @Test void tenantActorAndCurrentPermissionsProtectReceiptsAndReplay() {
        var granted = new SmaPrincipal(79104, "request-user", 79104, "admin", 10L, false, DataAccess.unrestricted(), Set.of("config:parameter:create"), "UTC");
        var cmd = command("authorization", "input", "operation-key-0004", "config:parameter:create");
        String id = Users.as(granted, () -> execution.executeLocal(cmd, work(() -> {}, () -> "ok")).receipt().operationId());
        Users.as(actor(79105), () -> { assertThatThrownBy(() -> execution.receipt(id)).isInstanceOf(RequestRejected.class); return null; });
        Users.as(actor(79104), () -> {
            assertThatThrownBy(() -> execution.receipt(id)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> execution.executeLocal(cmd, work(() -> {}, () -> "forbidden"))).isInstanceOf(AccessDeniedException.class); return null;
        });
        Users.as(granted, () -> {
            assertThatThrownBy(() -> execution.executeLocal(cmd, work(() -> { throw new AccessDeniedException("object no longer visible"); }, () -> "forbidden")))
                    .isInstanceOf(AccessDeniedException.class); return null;
        });
    }
    @Test void aLostCommitAcknowledgementFindsTheCommittedLedgerAndNeverReexecutes() {
        var loseNext = new AtomicBoolean();
        PlatformTransactionManager loss = new PlatformTransactionManager() {
            @Override public TransactionStatus getTransaction(TransactionDefinition definition) { return manager.getTransaction(definition); }
            @Override public void commit(TransactionStatus status) {
                manager.commit(status); if (loseNext.compareAndSet(true, false)) throw new TransactionSystemException("simulated lost acknowledgement");
            }
            @Override public void rollback(TransactionStatus status) { manager.rollback(status); }
        };
        Users.as(actor(79106), () -> {
            var service = secondNode(loss, 100000); var cmd = command("lost-ack", "input", "operation-key-0006", "");
            var first = service.executeLocal(cmd, work(() -> {}, () -> { group("lost-ack"); loseNext.set(true); return "ok"; }));
            assertThat(first.receipt().state()).isEqualTo(RequestReceipt.State.SUCCEEDED);
            assertThat(first.result()).isEqualTo("ok");
            assertThat(execution.executeLocal(cmd, work(() -> {}, () -> { throw new AssertionError("duplicate commit"); })).replayed()).isTrue();
            assertThat(parameters.groups()).hasSize(1); return null;
        });
    }
    @Test void committedBudgetsAreSharedAcrossNodesAndNotRefundedByBusinessFailure() {
        Users.as(actor(79107), () -> {
            execution.claimBudget("config.write", 2, Duration.ofMinutes(1));
            secondNode(manager, 100000).claimBudget("config.write", 2, Duration.ofMinutes(1));
            assertThatThrownBy(() -> execution.claimBudget("config.write", 2, Duration.ofMinutes(1)))
                    .isInstanceOfSatisfying(RequestRejected.class, e -> { assertThat(e.reason()).isEqualTo(RequestRejected.Reason.RATE_LIMITED); assertThat(e.retryAfterSeconds()).isBetween(1L, 60L); });
            execution.claimBudget("config.read", 2, Duration.ofMinutes(1)); return null;
        });
    }
    @Test void capacityAndMissingFingerprintKeyFailBeforeRunningAnyBusinessWork() {
        Users.as(actor(79108), () -> {
            var bounded = secondNode(manager, 1);
            bounded.executeLocal(command("capacity", "input", "operation-key-0008", ""), work(() -> {}, () -> "ok"));
            assertThatThrownBy(() -> bounded.executeLocal(command("capacity", "input", "operation-key-0009", ""), work(() -> {}, () -> { throw new AssertionError("admission must fail"); })))
                    .isInstanceOfSatisfying(RequestRejected.class, e -> assertThat(e.reason()).isEqualTo(RequestRejected.Reason.UNAVAILABLE));
            var missing = new RequestExecutionService(executions, mutexes, budgets, manager, "", 10);
            assertThatThrownBy(() -> missing.executeLocal(command("missing", "input", "operation-key-0010", ""), work(() -> {}, () -> { throw new AssertionError("missing key"); })))
                    .isInstanceOf(RequestRejected.class); return null;
        });
    }
    @Test void outerRollbackDoesNotRefundReservedRateBudget() {
        Users.as(actor(79109), () -> {
            var outer = new org.springframework.transaction.support.TransactionTemplate(manager);
            assertThatThrownBy(() -> outer.executeWithoutResult(status -> {
                execution.claimBudget("config.rollback-budget", 1, Duration.ofMinutes(1));
                throw new IllegalStateException("rollback outer transaction");
            })).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> execution.claimBudget("config.rollback-budget", 1, Duration.ofMinutes(1)))
                    .isInstanceOfSatisfying(RequestRejected.class, e -> assertThat(e.reason()).isEqualTo(RequestRejected.Reason.RATE_LIMITED));
            return null;
        });
    }
    @Test void oldKeyReceiptsRemainVerifiableAfterFingerprintKeyRotation() {
        Users.as(actor(79110), () -> {
            var cmd = command("rotation", "input", "operation-key-rotation", "");
            execution.executeLocal(cmd, work(() -> {}, () -> "ok"));
            var rotated = new RequestExecutionService(executions, mutexes, budgets, manager,
                    "AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=", 100000, "v2", "v1=" + TEST_KEY);
            assertThat(rotated.executeLocal(cmd, work(() -> {}, () -> { throw new AssertionError("must not execute again"); })).replayed()).isTrue();
            return null;
        });
    }
    @Test void tenantBudgetIsSharedByDifferentUsersButNotByDifferentTenants() {
        Users.as(actor(79111), () -> { execution.claimTenantBudget("config.tenant-write", 1, Duration.ofMinutes(1)); return null; });
        Users.as(Users.principal(79112, 79111, DataAccess.unrestricted()), () -> {
            assertThatThrownBy(() -> execution.claimTenantBudget("config.tenant-write", 1, Duration.ofMinutes(1))).isInstanceOf(RequestRejected.class); return null;
        });
        Users.as(actor(79112), () -> { execution.claimTenantBudget("config.tenant-write", 1, Duration.ofMinutes(1)); return null; });
    }
    @Test void staleRunningReceiptRemainsUnknownAndNeverReleasesItsExecutionRight() {
        Users.as(actor(79113), () -> {
            var cmd = command("unknown", "input", "operation-key-unknown", "");
            var completed = execution.executeLocal(cmd, work(() -> {}, () -> "ok"));
            // Persisted crash fixture: process died after reservation without a reliable completion record.
            executions.update(new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<RequestExecutionEntity>()
                    .eq(RequestExecutionEntity::getPublicId, completed.receipt().operationId())
                    .set(RequestExecutionEntity::getState, "RUNNING")
                    .set(RequestExecutionEntity::getCreatedAt, executions.now().minusSeconds(120)));
            assertThat(execution.receiptByKey(cmd.key()).state()).isEqualTo(RequestReceipt.State.UNKNOWN);
            assertThat(execution.executeLocal(cmd, work(() -> {}, () -> { throw new AssertionError("stale is not permission to retry"); })).receipt().state())
                    .isEqualTo(RequestReceipt.State.UNKNOWN); return null;
        });
    }
    private RequestExecutionService secondNode(PlatformTransactionManager transactions, int capacity) {
        return new RequestExecutionService(executions, mutexes, budgets, transactions, TEST_KEY, capacity);
    }
    private static SmaPrincipal actor(long tenant) { return Users.principal(tenant, tenant, DataAccess.unrestricted()); }
    private RequestCommand command(String operation, String input, String key, String permission) {
        return new RequestCommand("config." + operation, "config.groups", "one-object", key, input.getBytes(StandardCharsets.UTF_8), permission);
    }
    private <T> RequestExecution.Work<T> work(Runnable authorize, Supplier<T> action) {
        return new RequestExecution.Work<>() { public void authorize() { authorize.run(); } public T execute() { return action.get(); } };
    }
    private void group(String key) { parameters.saveGroup(null, new ParameterService.GroupInput(key, key, null, 0, Map.of())); }
}
