package com.sma.admin.common.internal.service;

import com.sma.admin.common.internal.persistence.CacheRevisionMapper;
import com.sma.admin.common.internal.persistence.BusinessOutboxMapper;
import com.sma.admin.common.internal.persistence.BusinessOutboxEntity;
import com.sma.admin.common.internal.persistence.IdentifiedDataSource;
import com.sma.admin.common.internal.persistence.BusinessRouteScope;
import com.sma.admin.common.internal.SqlTelemetry;
import com.sma.admin.common.persistence.DataSourceIdentity;
import com.sma.admin.common.internal.persistence.RequestExecutionMapper;
import com.sma.admin.common.internal.persistence.RequestMutexMapper;
import com.sma.admin.common.internal.security.RequestGovernanceProperties;
import com.sma.admin.common.request.BusinessLedger;
import com.sma.admin.common.request.BusinessLedgerResourceFactory;
import com.sma.admin.common.request.BusinessOutboxConsumer;
import com.sma.admin.common.request.RequestCommand;
import com.sma.admin.common.request.RequestExecution;
import com.sma.admin.common.request.RequestReceipt;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.DataPermissionInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import java.util.Objects;
import java.util.List;
import java.util.Arrays;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.TransactionDefinition;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.sma.admin.common.security.CurrentUser;
import com.sma.admin.common.security.ActorContext;

/** A resource owns its mapper proxies, transactions and request admission capacity. */
@Service
public class BusinessLedgerResourceFactoryService implements BusinessLedgerResourceFactory {
    private final RequestGovernanceProperties properties;
    private final MybatisPlusInterceptor interceptor;
    private final MetaObjectHandler audit;
    private final SqlTelemetry telemetry;
    private final com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator identifiers;

    public BusinessLedgerResourceFactoryService(RequestGovernanceProperties properties) {
        this(properties, null, null, null, null);
    }

    @Autowired
    public BusinessLedgerResourceFactoryService(RequestGovernanceProperties properties,
            MybatisPlusInterceptor interceptor, MetaObjectHandler audit, SqlTelemetry telemetry,
            com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator identifiers) {
        this.properties = properties;
        this.interceptor = interceptor;
        this.audit = audit;
        this.telemetry = telemetry;
        this.identifiers = identifiers;
    }

    @Override public Resource preparePhysical(String resourceId, long revision, DataSource concretePool) {
        if (!(concretePool instanceof com.zaxxer.hikari.HikariDataSource)
                || interceptor == null || audit == null || identifiers == null)
            throw new IllegalArgumentException("A business ledger needs a host-owned concrete pool and persistence guards");
        var source = new IdentifiedDataSource(concretePool, new DataSourceIdentity(resourceId, revision));
        try {
            var bean = new MybatisSqlSessionFactoryBean();
            bean.setDataSource(source);
            var configuration = new MybatisConfiguration();
            configuration.setMapUnderscoreToCamelCase(true);
            bean.setConfiguration(configuration);
            bean.setGlobalConfig(new GlobalConfig().setMetaObjectHandler(audit).setIdentifierGenerator(identifiers));
            if (telemetry==null) bean.setPlugins(interceptor);
            else bean.setPlugins(interceptor,telemetry);
            bean.afterPropertiesSet();
            var mappers = Objects.requireNonNull(bean.getObject());
            mappers.getConfiguration().addMapper(RequestExecutionMapper.class);
            mappers.getConfiguration().addMapper(RequestMutexMapper.class);
            mappers.getConfiguration().addMapper(CacheRevisionMapper.class);
            mappers.getConfiguration().addMapper(BusinessOutboxMapper.class);
            return prepare(resourceId, revision, source, mappers, new DataSourceTransactionManager(source));
        } catch (Exception failure) {
            throw new IllegalStateException("Business ledger resource assembly failed", failure);
        }
    }

    @Override public Resource prepare(String resourceId, long revision, DataSource source,
            SqlSessionFactory mappers, PlatformTransactionManager transactions) {
        if (resourceId == null || !resourceId.matches("[a-z][a-z0-9_-]{0,63}") || revision < 1)
            throw new IllegalArgumentException("Invalid business ledger resource identity");
        Objects.requireNonNull(source);
        Objects.requireNonNull(mappers);
        if (!(transactions instanceof DataSourceTransactionManager local) || local.getDataSource() != source
                || mappers.getConfiguration().getEnvironment() == null
                || mappers.getConfiguration().getEnvironment().getDataSource() != source)
            throw new IllegalArgumentException("Business ledger mappers and transaction manager must share one data source");
        boolean governed = mappers.getConfiguration().getInterceptors().stream()
                .filter(MybatisPlusInterceptor.class::isInstance)
                .map(MybatisPlusInterceptor.class::cast)
                .anyMatch(interceptor -> interceptor.getInterceptors().stream().anyMatch(TenantLineInnerInterceptor.class::isInstance)
                        && interceptor.getInterceptors().stream().anyMatch(DataPermissionInterceptor.class::isInstance)
                        && interceptor.getInterceptors().stream().anyMatch(BlockAttackInnerInterceptor.class::isInstance));
        if (!governed || GlobalConfigUtils.getMetaObjectHandler(mappers.getConfiguration()).isEmpty())
            throw new IllegalArgumentException("Business ledger requires tenant, data scope, block attack and audit interceptors");
        var template = new SqlSessionTemplate(mappers);
        var execution = new RequestExecutionService(template.getMapper(RequestExecutionMapper.class),
                template.getMapper(RequestMutexMapper.class), null, transactions, properties);
        var revisions = new CacheRevisionStore(template.getMapper(CacheRevisionMapper.class));
        var revisionMapper = template.getMapper(CacheRevisionMapper.class);
        var outbox = template.getMapper(BusinessOutboxMapper.class);
        var outboxSql = new JdbcTemplate(source);
        return new Resource() {
            @Override public String resourceId() { return resourceId; }
            @Override public long revision() { return revision; }
            @Override public long authoritativeRevision(long tenantId, String region) {
                validateRegion(region);
                if (tenantId < 1) throw new IllegalArgumentException("Invalid business tenant");
                if (TransactionSynchronizationManager.isActualTransactionActive())
                    throw new IllegalStateException("Business revision poll cannot join a transaction");
                return ActorContext.withinJob(tenantId, "business-revision:" + resourceId, () -> {
                    var read = new TransactionTemplate(transactions);
                    read.setReadOnly(true);
                    read.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
                    return BusinessRouteScope.within(source, () -> read.execute(status -> {
                        var row = revisionMapper.selectOne(new LambdaQueryWrapper<com.sma.admin.common.internal.persistence.CacheRevisionEntity>()
                                .eq(com.sma.admin.common.internal.persistence.CacheRevisionEntity::getRegion, region));
                        return row == null ? 0L : row.getRevision();
                    }));
                });
            }
            @Override public boolean deliverOne(List<BusinessOutboxConsumer> consumers) {
                if (consumers == null || consumers.isEmpty()) return false;
                var claimTx = new TransactionTemplate(transactions);
                var selected = claimTx.execute(status -> {
                    var rows = outboxSql.query("SELECT id,tenant_id,event_type,key_hash,payload,delivery_fence "
                            + "FROM common_business_outbox WHERE state IN ('PENDING','UNKNOWN') "
                            + "AND (lease_until IS NULL OR lease_until<UTC_TIMESTAMP(3)) AND deleted=0 "
                            + "ORDER BY id LIMIT 1 FOR UPDATE SKIP LOCKED", (rs, index) ->
                            new ClaimedDelivery(rs.getLong("id"), rs.getLong("tenant_id"), rs.getString("event_type"),
                                    rs.getString("key_hash"), rs.getBytes("payload"), rs.getLong("delivery_fence") + 1));
                    if (rows.isEmpty()) return null;
                    var row = rows.getFirst();
                    if (outboxSql.update("UPDATE common_business_outbox SET state='UNKNOWN',"
                            + "delivery_fence=delivery_fence+1,attempts=attempts+1,"
                            + "lease_until=DATE_ADD(UTC_TIMESTAMP(3),INTERVAL 30 SECOND),updated_at=UTC_TIMESTAMP(3) "
                            + "WHERE id=? AND tenant_id=? AND delivery_fence=? AND deleted=0",
                            row.id(), row.tenantId(), row.fence() - 1) != 1)
                        throw new IllegalStateException("Business outbox claim changed");
                    return row;
                });
                if (selected == null) return false;
                var matches = consumers.stream().filter(consumer -> consumer.supports(selected.eventType())).toList();
                if (matches.size() != 1) throw new IllegalStateException("Business outbox consumer is unavailable or ambiguous");
                var event = new BusinessOutboxConsumer.Delivery(selected.id(), selected.tenantId(), resourceId, revision,
                        selected.eventType(), selected.keyHash(), selected.payload());
                ActorContext.withinJob(selected.tenantId(), "business-outbox:" + resourceId,
                        () -> { matches.getFirst().deliver(event); return null; });
                var finishTx = new TransactionTemplate(transactions);
                var finished = finishTx.execute(status -> outboxSql.update(
                        "UPDATE common_business_outbox SET state='DONE',lease_until=NULL,updated_at=UTC_TIMESTAMP(3) "
                                + "WHERE id=? AND tenant_id=? AND delivery_fence=? AND state='UNKNOWN' AND deleted=0",
                        selected.id(), selected.tenantId(), selected.fence()));
                if (finished != 1) throw new IllegalStateException("Business outbox delivery fence changed");
                return true;
            }
            @Override public BusinessLedger.Session open(Runnable release) {
                Objects.requireNonNull(release);
                return new BusinessLedger.Session() {
                    private final AtomicBoolean closed = new AtomicBoolean();
                    private void requireOpen() {
                        if (closed.get()) throw new IllegalStateException("Business ledger resource lease is closed");
                    }
                    @Override public DataSourceIdentity resourceIdentity() {
                        requireOpen(); return new DataSourceIdentity(resourceId,revision);
                    }
                    @Override public <T> RequestExecution.Outcome<T> executeLocal(RequestCommand command, RequestExecution.Work<T> work) {
                        requireOpen();
                        return BusinessRouteScope.within(source, () -> execution.executeLocal(command, work));
                    }
                    @Override public RequestReceipt receipt(String operationId) {
                        requireOpen();
                        return execution.receipt(operationId);
                    }
                    @Override public RequestReceipt receiptByKey(String key) {
                        requireOpen();
                        return execution.receiptByKey(key);
                    }
                    @Override public void invalidate(String region) {
                        requireOpen();
                        requireWritableResource(source);
                        revisions.advance(region);
                    }
                    @Override public long authoritativeRevision(String region) {
                        requireOpen();
                        validateRegion(region);
                        if (TransactionSynchronizationManager.isActualTransactionActive())
                            throw new IllegalStateException("Authoritative revision requires a committed read outside a transaction");
                        var read = new TransactionTemplate(transactions);
                        read.setReadOnly(true);
                        read.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
                        Long result = read.execute(status -> {
                            var row = revisionMapper.selectOne(new LambdaQueryWrapper<com.sma.admin.common.internal.persistence.CacheRevisionEntity>()
                                    .eq(com.sma.admin.common.internal.persistence.CacheRevisionEntity::getRegion, region));
                            return row == null ? 0L : row.getRevision();
                        });
                        return Objects.requireNonNull(result);
                    }
                    @Override public long enqueue(String eventType, String eventKey, byte[] payload) {
                        requireOpen();
                        requireWritableResource(source);
                        if (eventType == null || eventType.length() > 120 || !eventType.matches("[a-z][a-z0-9-]{0,39}\\.[a-z][a-z0-9.-]{0,79}")
                                || eventKey == null || !eventKey.matches("[A-Za-z0-9_-]{16,128}")
                                || payload == null || payload.length == 0 || payload.length > 16384)
                            throw new IllegalArgumentException("Invalid business outbox event");
                        long tenant = CurrentUser.tenantId();
                        String keyHash = hex(sha256(eventKey.getBytes(StandardCharsets.UTF_8)));
                        byte[] content = payload.clone();
                        String payloadHash = hex(sha256(content));
                        long id = IdWorker.getId();
                        outbox.insertIntent(id, tenant, eventType, keyHash, payloadHash, content, CurrentUser.userId());
                        var row = outbox.selectOne(new LambdaQueryWrapper<BusinessOutboxEntity>()
                                .eq(BusinessOutboxEntity::getEventType, eventType)
                                .eq(BusinessOutboxEntity::getKeyHash, keyHash));
                        if (row == null || !payloadHash.equals(row.getPayloadHash()) || !Arrays.equals(content, row.getPayload()))
                            throw new IllegalStateException("Business outbox key already belongs to different content");
                        return row.getId();
                    }
                    @Override public <T> T query(java.util.function.Supplier<T> read) {
                        requireOpen();
                        Objects.requireNonNull(read);
                        if (TransactionSynchronizationManager.isActualTransactionActive())
                            throw new IllegalStateException("Business query must start outside an existing transaction");
                        var transaction=new TransactionTemplate(transactions);
                        transaction.setReadOnly(true);
                        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
                        return BusinessRouteScope.within(source, () -> transaction.execute(status -> read.get()));
                    }
                    @Override public void close() {
                        if (closed.compareAndSet(false, true)) release.run();
                    }
                };
            }
        };
    }
    private record ClaimedDelivery(long id, long tenantId, String eventType, String keyHash, byte[] payload, long fence) {}
    private static void requireWritableResource(DataSource source) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                || !TransactionSynchronizationManager.hasResource(source))
            throw new IllegalStateException("Business ledger write requires this resource's writable transaction");
    }
    private static void validateRegion(String region) {
        if (region == null || region.length() > 96 || !region.matches("[a-z]+\\.[a-z][a-z0-9-]{0,79}"))
            throw new IllegalArgumentException("Invalid cache region");
    }
    private static byte[] sha256(byte[] value) {
        try { return MessageDigest.getInstance("SHA-256").digest(value); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static String hex(byte[] value) { return java.util.HexFormat.of().formatHex(value); }
}
