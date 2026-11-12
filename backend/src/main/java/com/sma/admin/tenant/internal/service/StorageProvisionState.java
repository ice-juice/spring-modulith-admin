package com.sma.admin.tenant.internal.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sma.admin.tenant.internal.persistence.StorageBindingEntity;
import com.sma.admin.tenant.internal.persistence.StorageBindingMapper;
import com.sma.admin.tenant.internal.persistence.StorageOperationEntity;
import com.sma.admin.tenant.internal.persistence.StorageOperationMapper;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Short platform transactions. Physical migration always runs after begin commits. */
@Service
public class StorageProvisionState {
    public record Work(long operationId, long bindingId, long fence, String resourceId,
                       long revision, boolean run,String storageGroup) {}
    private final StorageBindingMapper bindings;
    private final StorageOperationMapper operations;
    private final PlatformOperators operators;

    public StorageProvisionState(StorageBindingMapper bindings, StorageOperationMapper operations,
                                 PlatformOperators operators) {
        this.bindings = bindings;
        this.operations = operations;
        this.operators = operators;
    }

    @Transactional
    public Work begin(long bindingId, long expectedVersion, String operationKey) {
        operators.require();
        if (bindingId < 1 || expectedVersion < 1 || operationKey == null
                || !operationKey.matches("[a-zA-Z0-9_-]{16,64}"))
            throw new IllegalArgumentException("Invalid storage provision request");
        StorageBindingEntity binding = bindings.lockById(bindingId);
        if (binding == null) throw new IllegalStateException("Business storage binding is unavailable");
        var existing = operations.selectOne(new LambdaQueryWrapper<StorageOperationEntity>()
                .eq(StorageOperationEntity::getOperationId, operationKey));
        if (existing != null) {
            requireIntent(existing, binding, expectedVersion);
            if ("SUCCEEDED".equals(existing.getState())) {
                if (!"ACTIVE".equals(binding.getState())
                        || !binding.getBindingVersion().equals(Math.addExact(expectedVersion, 1)))
                    throw new IllegalStateException("Business storage operation result changed");
                return work(existing, false);
            }
            if ("RUNNING".equals(existing.getState()) && existing.getLeaseUntil() != null
                    && existing.getLeaseUntil().isAfter(Instant.now()))
                throw new IllegalStateException("Business storage provision is already running");
            throw new IllegalStateException("Business storage provision requires operator review before retry");
        }
        if (!"PENDING".equals(binding.getState()) || !binding.getBindingVersion().equals(expectedVersion))
            throw new IllegalStateException("Business storage binding is not pending");
        if (operations.selectCount(new LambdaQueryWrapper<StorageOperationEntity>()
                .eq(StorageOperationEntity::getBindingId, bindingId)
                .ne(StorageOperationEntity::getState, "SUCCEEDED")) > 0)
            throw new IllegalStateException("Business storage binding has an unfinished operation");
        var row = new StorageOperationEntity();
        row.setOperationId(operationKey);
        row.setBindingId(bindingId);
        row.setOwnerTenantId(binding.getOwnerTenantId());
        row.setStorageGroup(binding.getStorageGroup());
        row.setResourceId(binding.getResourceId());
        row.setResourceRevision(binding.getResourceRevision());
        row.setExpectedBindingVersion(expectedVersion);
        row.setFence(1L);
        row.setState("RUNNING");
        row.setLeaseUntil(Instant.now().plusSeconds(300));
        operations.insert(row);
        return work(row, true);
    }

    /** An operator must inspect the physical schema and original intent before invoking this distinct path. */
    @Transactional
    public Work retryAfterReview(long bindingId, long expectedVersion, String operationKey) {
        operators.require();
        if (bindingId < 1 || expectedVersion < 1 || operationKey == null
                || !operationKey.matches("[a-zA-Z0-9_-]{16,64}"))
            throw new IllegalArgumentException("Invalid storage provision request");
        var binding = bindings.lockById(bindingId);
        if (binding == null) throw new IllegalStateException("Business storage binding is unavailable");
        var existing = operations.selectOne(new LambdaQueryWrapper<StorageOperationEntity>()
                .eq(StorageOperationEntity::getOperationId, operationKey));
        if (existing == null) throw new IllegalStateException("Business storage operation is unavailable");
        requireIntent(existing, binding, expectedVersion);
        if (!"PENDING".equals(binding.getState()) || !binding.getBindingVersion().equals(expectedVersion)
                || !("NEEDS_REVIEW".equals(existing.getState())
                || ("RUNNING".equals(existing.getState()) && existing.getLeaseUntil() != null
                    && !existing.getLeaseUntil().isAfter(Instant.now()))))
            throw new IllegalStateException("Business storage operation is not eligible for reviewed retry");
        existing.setFence(Math.addExact(existing.getFence(), 1));
        existing.setState("RUNNING");
        existing.setLeaseUntil(Instant.now().plusSeconds(300));
        operations.updateById(existing);
        return work(existing, true);
    }

    @Transactional
    public void finish(Work work) {
        operators.require();
        var binding = bindings.lockById(work.bindingId());
        var row = operations.selectById(work.operationId());
        if (binding == null || row == null || !"RUNNING".equals(row.getState())
                || !row.getFence().equals(work.fence()) || !binding.getResourceId().equals(work.resourceId())
                || !binding.getResourceRevision().equals(work.revision())
                || !"PENDING".equals(binding.getState())
                || !binding.getBindingVersion().equals(row.getExpectedBindingVersion()))
            throw new IllegalStateException("Business storage provision fence or binding changed");
        binding.setState("ACTIVE");
        binding.setBindingVersion(Math.addExact(binding.getBindingVersion(), 1));
        bindings.updateById(binding);
        row.setState("SUCCEEDED");
        row.setLeaseUntil(null);
        row.setCompletedAt(Instant.now());
        operations.updateById(row);
    }

    @Transactional(readOnly = true)
    public void requireCurrent(Work work) {
        operators.require();
        var binding = bindings.selectById(work.bindingId());
        var row = operations.selectById(work.operationId());
        if (binding == null || row == null || !"RUNNING".equals(row.getState())
                || !row.getFence().equals(work.fence())
                || !"PENDING".equals(binding.getState())
                || !binding.getResourceId().equals(work.resourceId())
                || !binding.getResourceRevision().equals(work.revision())
                || !binding.getBindingVersion().equals(row.getExpectedBindingVersion()))
            throw new IllegalStateException("Business storage provision is no longer current");
    }

    @Transactional
    public void failed(Work work) {
        operators.require();
        var binding = bindings.lockById(work.bindingId());
        var row = operations.selectById(work.operationId());
        if (binding == null || row == null || !"RUNNING".equals(row.getState())
                || !row.getFence().equals(work.fence())) return;
        row.setState("NEEDS_REVIEW");
        row.setLeaseUntil(null);
        operations.updateById(row);
    }

    @Transactional(readOnly = true)
    public String state(String operationKey) {
        operators.require();
        var row = operations.selectOne(new LambdaQueryWrapper<StorageOperationEntity>()
                .eq(StorageOperationEntity::getOperationId, operationKey));
        return row == null ? null : row.getState();
    }

    private static void requireIntent(StorageOperationEntity row, StorageBindingEntity binding, long version) {
        if (!row.getBindingId().equals(binding.getId()) || !row.getOwnerTenantId().equals(binding.getOwnerTenantId())
                || !row.getStorageGroup().equals(binding.getStorageGroup())
                || !row.getResourceId().equals(binding.getResourceId())
                || !row.getResourceRevision().equals(binding.getResourceRevision())
                || !row.getExpectedBindingVersion().equals(version))
            throw new IllegalStateException("Business storage operation key belongs to another intent");
    }

    private static Work work(StorageOperationEntity row, boolean run) {
        return new Work(row.getId(), row.getBindingId(), row.getFence(), row.getResourceId(),
                row.getResourceRevision(), run,row.getStorageGroup());
    }
}
