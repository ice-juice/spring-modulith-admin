package com.sma.admin.rule.internal.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sma.admin.common.BizException;
import com.sma.admin.common.security.CurrentUser;
import com.sma.admin.rule.RuleCommands.*;
import com.sma.admin.rule.RuleErrorCode;
import com.sma.admin.rule.internal.persistence.*;

/** Synchronous bounded batches. Each draft change and its receipt commit atomically in one transaction. */
@Service
public class RuleBatchService {
    private final MigrationBatchMapper batches;
    private final WriteLockMapper locks;
    private final RuleService rules;
    private final RuleJson json;
    private final TransactionTemplate transaction;
    private final Set<Long> live = ConcurrentHashMap.newKeySet();
    public RuleBatchService(MigrationBatchMapper batches, WriteLockMapper locks, RuleService rules, RuleJson json, PlatformTransactionManager transactions) {
        this.batches = batches; this.locks = locks; this.rules = rules; this.json = json;
        this.transaction = new TransactionTemplate(transactions);
        this.transaction.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    private record Ticket(long id, boolean created) {}
    public BatchReport migrate(BatchMigration command) {
        CurrentUser.optional().ifPresent(user -> {
            if (!user.superAdmin() && !user.dataAccess().all() && !user.dataAccess().self())
                throw new AccessDeniedException("Batch data scope denied");
        });
        validate(command); String hash = json.hash(json.write(command));
        var owned = new java.util.concurrent.atomic.AtomicReference<Long>(); Ticket ticket;
        try {
            ticket = Objects.requireNonNull(transaction.execute(status -> {
                locks.lock(CurrentUser.tenantId());
                var existing = batches.selectOne(new LambdaQueryWrapper<MigrationBatchEntity>().eq(MigrationBatchEntity::getPortal, CurrentUser.portal())
                        .eq(MigrationBatchEntity::getOperationKey, command.operationKey()));
                if (existing != null) {
                    if (!existing.getRequestHash().equals(hash) || !existing.getCreatedBy().equals(CurrentUser.userId())) throw new BizException(RuleErrorCode.CONFLICT);
                    return new Ticket(existing.getId(), false);
                }
                var row = new MigrationBatchEntity(); row.setPortal(CurrentUser.portal()); row.setOperationKey(command.operationKey());
                row.setRequestHash(hash); row.setState("RUNNING"); row.setReportJson("{}"); batches.insert(row);
                row.setReportJson(json.writeReport(new BatchReport(row.getId(), "RUNNING", command.items().size(), false, List.of(),
                        command.items().stream().map(MigrationItem::instanceId).toList()))); batches.updateById(row);
                owned.set(row.getId()); live.add(row.getId()); return new Ticket(row.getId(), true);
            }));
        } catch (RuntimeException ex) { if (owned.get() != null) live.remove(owned.get()); throw ex; }
        if (!ticket.created()) return get(ticket.id());
        try {
            for (var item : command.items()) {
                try {
                    transaction.executeWithoutResult(status -> {
                        locks.lock(CurrentUser.tenantId()); var row = require(ticket.id());
                        var saved = rules.migrateDraft(item.instanceId(), item.migration());
                        append(row, new BatchItem(item.instanceId(), true, saved.revision(), null));
                    });
                } catch (RuntimeException failure) {
                    String code = failure instanceof BizException business ? business.getErrorCode().key()
                            : failure instanceof AccessDeniedException ? "common.error.forbidden" : "rule.batch.unexpected";
                    transaction.executeWithoutResult(status -> {
                        locks.lock(CurrentUser.tenantId()); append(require(ticket.id()), new BatchItem(item.instanceId(), false, null, code));
                    });
                }
            }
            transaction.executeWithoutResult(status -> {
                locks.lock(CurrentUser.tenantId()); var row = require(ticket.id()); var prior = json.readReport(row.getReportJson(), BatchReport.class);
                boolean passed = prior.results().stream().allMatch(BatchItem::passed);
                row.setState(passed ? "COMPLETED" : "COMPLETED_WITH_ERRORS");
                row.setReportJson(json.writeReport(new BatchReport(row.getId(), row.getState(), prior.total(), passed, prior.results(), prior.pendingInstanceIds())));
                batches.updateById(row);
            });
            return get(ticket.id());
        } finally { live.remove(ticket.id()); }
    }
    public BatchReport get(long id) {
        var row = require(id); var report = json.readReport(row.getReportJson(), BatchReport.class);
        if (row.getState().equals("RUNNING") && !live.contains(id))
            return new BatchReport(id, "RECOVERY_REQUIRED", report.total(), false, report.results(), report.pendingInstanceIds());
        return report;
    }
    /** Does not replay migrate or turn an unobserved operation into a failed one. */
    public BatchLookup byOperationKey(String operationKey) {
        if (operationKey == null || !operationKey.matches("[a-zA-Z0-9_-]{8,64}")) throw new BizException(RuleErrorCode.NOT_FOUND);
        if (!CurrentUser.require().permissions().contains("rule:batch:migrate")) throw new AccessDeniedException("Batch action permission revoked");
        var row = batches.selectOne(new LambdaQueryWrapper<MigrationBatchEntity>()
                .eq(MigrationBatchEntity::getPortal, CurrentUser.portal())
                .eq(MigrationBatchEntity::getOperationKey, operationKey)
                .eq(MigrationBatchEntity::getCreatedBy, CurrentUser.userId()));
        if (row == null) throw new BizException(RuleErrorCode.NOT_FOUND);
        return new BatchLookup(operationKey, get(row.getId()));
    }
    private void append(MigrationBatchEntity row, BatchItem item) {
        var prior = json.readReport(row.getReportJson(), BatchReport.class);
        if (!prior.pendingInstanceIds().contains(item.instanceId())) throw new BizException(RuleErrorCode.CONFLICT);
        var results = new ArrayList<>(prior.results()); results.add(item);
        var pending = prior.pendingInstanceIds().stream().filter(id -> id != item.instanceId()).toList();
        row.setReportJson(json.writeReport(new BatchReport(row.getId(), row.getState(), prior.total(), false, List.copyOf(results), pending)));
        batches.updateById(row);
    }
    private MigrationBatchEntity require(long id) {
        var row = batches.selectById(id);
        if (row == null || !CurrentUser.portal().equals(row.getPortal())) throw new BizException(RuleErrorCode.NOT_FOUND);
        CurrentUser.optional().ifPresent(user -> {
            if (!user.superAdmin() && !user.dataAccess().all() && !(user.dataAccess().self() && row.getCreatedBy().equals(user.userId())))
                throw new AccessDeniedException("Batch access denied");
        });
        return row;
    }
    private void validate(BatchMigration command) {
        if (command == null || command.operationKey() == null || !command.operationKey().matches("[a-zA-Z0-9_-]{8,64}")
                || command.items() == null || command.items().isEmpty() || command.items().size() > 50) throw new BizException(RuleErrorCode.INVALID, "BATCH_LIMIT");
        var ids = new HashSet<Long>();
        for (var item : command.items())
            if (item == null || item.instanceId() <= 0 || item.migration() == null || !ids.add(item.instanceId())) throw new BizException(RuleErrorCode.INVALID, "INVALID_BATCH_ITEM");
    }
}
