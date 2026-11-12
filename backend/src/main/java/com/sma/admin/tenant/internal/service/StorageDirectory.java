package com.sma.admin.tenant.internal.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sma.admin.common.PageQuery;
import com.sma.admin.common.PageResult;
import com.sma.admin.common.BizException;
import com.sma.admin.common.security.ActorContext;
import com.sma.admin.tenant.TenantErrorCode;
import com.sma.admin.tenant.internal.persistence.StorageBindingEntity;
import com.sma.admin.tenant.internal.persistence.StorageBindingMapper;
import com.sma.admin.tenant.internal.persistence.StorageResourceEntity;
import com.sma.admin.tenant.internal.persistence.StorageResourceMapper;
import com.sma.admin.tenant.internal.persistence.TenantRecordMapper;
import java.util.Map;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Platform directory writes remain separate from business resources and their transactions. */
@Service
@EnableConfigurationProperties(TenantStorageProperties.class)
public class StorageDirectory {
    public record ResourceInput(String resourceId, long revision, String displayName,
                                String host, int port, String databaseName, String tlsMode,
                                String secretRef, int poolMax, int connectTimeoutMillis) {}
    public record BindingInput(long ownerTenantId, String storageGroup, String resourceId,
                               long resourceRevision, String schemaCompatibility) {}
    /** No credential reference or password is serialized by the management API. */
    public record ResourceView(Long id, String resourceId, long revision, String displayName,
                               String host, int port, String databaseName, String tlsMode,
                               int poolMax, int connectTimeoutMillis, String state, boolean credentialConfigured) {}
    public record BindingView(Long id, long ownerTenantId, String storageGroup, String resourceId,
                              long resourceRevision, String schemaCompatibility, long bindingVersion, String state) {}

    private final StorageResourceMapper resources;
    private final StorageBindingMapper bindings;
    private final TenantRecordMapper tenants;
    private final PlatformOperators operators;
    private final TenantStorageProperties properties;

    public StorageDirectory(StorageResourceMapper resources, StorageBindingMapper bindings,
                            TenantRecordMapper tenants, PlatformOperators operators, TenantStorageProperties properties) {
        this.resources = resources;
        this.bindings = bindings;
        this.tenants = tenants;
        this.operators = operators;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public PageResult<ResourceView> resources(PageQuery page, String resourceId) {
        operators.require();
        if (resourceId != null && !resourceId.matches("[a-z][a-z0-9_-]{0,63}"))
            throw new BizException(TenantErrorCode.INVALID);
        var query = new LambdaQueryWrapper<StorageResourceEntity>().orderByAsc(StorageResourceEntity::getResourceId)
                .orderByAsc(StorageResourceEntity::getRevision);
        if (resourceId != null) query.eq(StorageResourceEntity::getResourceId, resourceId);
        var result = resources.selectPage(page.<StorageResourceEntity>toPage(Map.of("id", "id", "resourceId", "resource_id",
                "revision", "revision")), query);
        return new PageResult<>(result.getRecords().stream().map(StorageDirectory::view).toList(),
                result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Transactional(readOnly = true)
    public PageResult<BindingView> bindings(PageQuery page, long tenantId) {
        operators.require();
        if (tenantId < 1) throw new BizException(TenantErrorCode.INVALID);
        var query = new LambdaQueryWrapper<StorageBindingEntity>()
                .eq(StorageBindingEntity::getOwnerTenantId, tenantId)
                .orderByAsc(StorageBindingEntity::getStorageGroup);
        var result = bindings.selectPage(page.<StorageBindingEntity>toPage(Map.of("id", "id",
                "storageGroup", "storage_group")), query);
        return new PageResult<>(result.getRecords().stream().map(StorageDirectory::view).toList(),
                result.getTotal(), result.getCurrent(), result.getSize());
    }

    public static ResourceView view(StorageResourceEntity row) {
        return new ResourceView(row.getId(), row.getResourceId(), row.getRevision(), row.getDisplayName(),
                row.getDbHost(), row.getDbPort(), row.getDatabaseName(), row.getTlsMode(), row.getPoolMax(),
                row.getConnectTimeoutMillis(), row.getState(), row.getSecretRef() != null);
    }

    public static BindingView view(StorageBindingEntity row) {
        return new BindingView(row.getId(), row.getOwnerTenantId(), row.getStorageGroup(), row.getResourceId(),
                row.getResourceRevision(), row.getSchemaCompatibility(), row.getBindingVersion(), row.getState());
    }

    /** Revisions are immutable. A new revision may be registered only after its predecessor. */
    @Transactional
    public StorageResourceEntity register(ResourceInput input) {
        operators.require();
        validate(input);
        if (resource(input.resourceId(), input.revision()) != null)
            throw new BizException(TenantErrorCode.DUPLICATE);
        if (input.revision() > 1 && resource(input.resourceId(), input.revision() - 1) == null)
            throw new BizException(TenantErrorCode.STATE);
        if (input.revision() == 1 && resources.selectCount(new LambdaQueryWrapper<StorageResourceEntity>()
                .eq(StorageResourceEntity::getResourceId, input.resourceId())) != 0)
            throw new BizException(TenantErrorCode.DUPLICATE);
        var row = new StorageResourceEntity();
        row.setResourceId(input.resourceId()); row.setRevision(input.revision());
        row.setDisplayName(input.displayName()); row.setDbHost(input.host()); row.setDbPort(input.port());
        row.setDatabaseName(input.databaseName()); row.setTlsMode(input.tlsMode()); row.setSecretRef(input.secretRef());
        row.setPoolMax(input.poolMax()); row.setConnectTimeoutMillis(input.connectTimeoutMillis());
        row.setState("REGISTERED");
        try { resources.insert(row); }
        catch (DuplicateKeyException duplicate) { throw new BizException(TenantErrorCode.DUPLICATE); }
        return row;
    }

    /** Initial binding remains PENDING until S7 provisions and validates the target schema. */
    @Transactional
    public StorageBindingEntity bindInitial(BindingInput input) {
        operators.require();
        if (input == null) throw new BizException(TenantErrorCode.INVALID);
        if (input.ownerTenantId() < 1 || !group(input.storageGroup())
                || input.resourceRevision() < 1 || input.schemaCompatibility() == null
                || !input.schemaCompatibility().matches("[a-z0-9][a-z0-9_.-]{0,31}"))
            throw new BizException(TenantErrorCode.INVALID);
        var tenant = tenants.selectById(input.ownerTenantId());
        if (tenant == null || "RETIRED".equals(tenant.getStatus()))
            throw new BizException(TenantErrorCode.STATE);
        var resource = resource(input.resourceId(), input.resourceRevision());
        if (resource == null || !"REGISTERED".equals(resource.getState()))
            throw new BizException(TenantErrorCode.STATE);
        if (binding(input.ownerTenantId(), input.storageGroup()) != null)
            throw new BizException(TenantErrorCode.DUPLICATE);
        var row = new StorageBindingEntity();
        row.setOwnerTenantId(input.ownerTenantId()); row.setStorageGroup(input.storageGroup());
        row.setResourceId(input.resourceId()); row.setResourceRevision(input.resourceRevision());
        row.setSchemaCompatibility(input.schemaCompatibility()); row.setBindingVersion(1L); row.setState("PENDING");
        try { bindings.insert(row); }
        catch (DuplicateKeyException duplicate) { throw new BizException(TenantErrorCode.DUPLICATE); }
        return row;
    }

    StorageBindingEntity bindingForCurrentTenant(String storageGroup) {
        if (!group(storageGroup)) throw new IllegalArgumentException("Invalid business storage group");
        return binding(ActorContext.tenantId(), storageGroup);
    }

    StorageBindingEntity binding(long tenantId, String storageGroup) {
        return bindings.selectOne(new LambdaQueryWrapper<StorageBindingEntity>()
                .eq(StorageBindingEntity::getOwnerTenantId, tenantId)
                .eq(StorageBindingEntity::getStorageGroup, storageGroup));
    }

    /** Caller holds a platform transaction; this row serializes admission with maintenance stop. */
    StorageBindingEntity bindingForSwitch(long bindingId) { return bindings.lockById(bindingId); }

    StorageResourceEntity resource(String resourceId, long revision) {
        if (resourceId == null || !resourceId.matches("[a-z][a-z0-9_-]{0,63}") || revision < 1) return null;
        return resources.selectOne(new LambdaQueryWrapper<StorageResourceEntity>()
                .eq(StorageResourceEntity::getResourceId, resourceId)
                .eq(StorageResourceEntity::getRevision, revision));
    }

    private void validate(ResourceInput input) {
        if (input == null) throw new BizException(TenantErrorCode.INVALID);
        if (input.resourceId() == null || !input.resourceId().matches("[a-z][a-z0-9_-]{0,63}")
                || input.resourceId().equals("unknown") || input.resourceId().equals("platform") || input.revision() < 1
                || input.displayName() == null || input.displayName().isBlank() || input.displayName().length() > 96
                || input.host() == null || !properties.allowedHosts().contains(input.host())
                || input.port() < 1 || input.port() > 65535
                || input.databaseName() == null || !input.databaseName().matches("[a-z][a-z0-9_]{1,63}")
                || input.secretRef() == null || !input.secretRef().matches("[a-z][a-z0-9/_-]{1,159}")
                || input.poolMax() < 1 || input.poolMax() > 8
                || input.connectTimeoutMillis() < 100 || input.connectTimeoutMillis() > 5000
                || !("VERIFY_IDENTITY".equals(input.tlsMode()) || "DISABLED".equals(input.tlsMode())))
            throw new BizException(TenantErrorCode.INVALID);
        if ("DISABLED".equals(input.tlsMode()) && (!properties.allowInsecureLoopback()
                || !(input.host().equals("localhost") || input.host().equals("127.0.0.1"))))
            throw new BizException(TenantErrorCode.INVALID);
    }

    private static boolean group(String group) { return group != null && group.matches("[a-z][a-z0-9-]{1,63}"); }
}
