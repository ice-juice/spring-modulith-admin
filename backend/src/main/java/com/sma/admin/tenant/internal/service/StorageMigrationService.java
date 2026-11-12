package com.sma.admin.tenant.internal.service;

import com.sma.admin.tenant.storage.StorageCredentialResolver;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.HexFormat;
import java.util.concurrent.Semaphore;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** S7a physical schema preparation. Task ownership and ACTIVE transition belong to S7b. */
@Service
public class StorageMigrationService {
    private static final Semaphore MIGRATIONS = new Semaphore(1);
    private final StorageDirectory directory;
    private final PlatformOperators operators;
    private final ObjectProvider<StorageCredentialResolver> credentials;
    private final BusinessSchemaDirectory schemas;

    public StorageMigrationService(StorageDirectory directory, PlatformOperators operators,
                                   ObjectProvider<StorageCredentialResolver> credentials) {
        this(directory,operators,credentials,new BusinessSchemaDirectory(java.util.List.of()));
    }
    @org.springframework.beans.factory.annotation.Autowired
    public StorageMigrationService(StorageDirectory directory,PlatformOperators operators,
                                   ObjectProvider<StorageCredentialResolver> credentials,BusinessSchemaDirectory schemas) {
        this.directory = directory;
        this.operators = operators;
        this.credentials = credentials;
        this.schemas = schemas;
    }

    /** A physical MySQL advisory lock serializes all nodes using the same target server. */
    public void migrate(String resourceId, long revision) {
        migrate(resourceId,revision,java.util.List.of());
    }
    /** Formal provisioning requires the persisted binding's declared business schema group. */
    public void migrate(String resourceId,long revision,String storageGroup){
        operators.require();
        migrate(resourceId,revision,schemas.require(storageGroup));
    }
    private void migrate(String resourceId,long revision,java.util.List<BusinessSchemaDirectory.Schema> businessSchemas){
        operators.require();
        if (TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Business schema migration cannot join a platform transaction");
        var resource = directory.resource(resourceId, revision);
        if (resource == null || !"REGISTERED".equals(resource.getState()))
            throw new IllegalStateException("Business storage resource is unavailable");
        var resolver = credentials.getIfAvailable();
        if (resolver == null) throw new IllegalStateException("Business storage credential resolver is not configured");
        var secret = resolver.resolve(resource.getSecretRef());
        if (secret == null) throw new IllegalStateException("Business storage secret is unavailable");
        if (!MIGRATIONS.tryAcquire()) throw new IllegalStateException("Business schema migration capacity is exhausted");
        try {
            var configuration = new HikariConfig();
            configuration.setJdbcUrl(StorageJdbcUrl.of(resource));
            configuration.setUsername(secret.username());
            configuration.setPassword(secret.password());
            configuration.setPoolName("business-migration-" + resourceId + "-" + revision);
            configuration.setMaximumPoolSize(2);
            configuration.setMinimumIdle(0);
            configuration.setConnectionTimeout(Math.max(250, resource.getConnectTimeoutMillis()));
            configuration.setInitializationFailTimeout(-1);
            try (var pool = new HikariDataSource(configuration);
                 var lock = pool.getConnection()) {
                String key = lockKey(resource);
                try (var statement = lock.prepareStatement("SELECT GET_LOCK(?, 0)")) {
                    statement.setString(1, key);
                    try (var result = statement.executeQuery()) {
                        if (!result.next() || result.getInt(1) != 1 || result.wasNull())
                            throw new IllegalStateException("Business schema migration is already running");
                    }
                }
                try {
                    var flyway = Flyway.configure().dataSource(pool).locations("classpath:db/business-ledger")
                            .table("flyway_schema_history_business_ledger").baselineOnMigrate(true)
                            .baselineVersion("0").cleanDisabled(true).load();
                    flyway.migrate();
                    flyway.validate();
                    requireLedgerSchema(lock);
                    for(var schema:businessSchemas){
                        var business=Flyway.configure().dataSource(pool).locations(schema.location()).table(schema.history())
                                .baselineOnMigrate(true).baselineVersion("0").cleanDisabled(true).outOfOrder(false)
                                .ignoreMigrationPatterns(new String[0]).failOnMissingLocations(true).load();
                        if(business.info().all().length==0)throw new IllegalStateException("Business schema has no versioned migrations");
                        business.migrate();business.validate();
                        for(var info:business.info().all())if(info.getState()!=org.flywaydb.core.api.MigrationState.SUCCESS
                                && info.getState()!=org.flywaydb.core.api.MigrationState.BASELINE)
                            throw new IllegalStateException("Business schema migration is not fully applied");
                    }
                } finally {
                    try (var release = lock.prepareStatement("SELECT RELEASE_LOCK(?)")) {
                        release.setString(1, key);
                        release.executeQuery().close();
                    }
                }
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("Business schema migration connection is unavailable", failure);
        } finally {
            MIGRATIONS.release();
        }
    }

    private static void requireLedgerSchema(java.sql.Connection connection) throws SQLException {
        for (String table : new String[] {"common_request_execution", "common_request_mutex",
                "common_cache_revision", "common_business_outbox"}) {
            try (var rows = connection.getMetaData().getTables(connection.getCatalog(), null, table,
                    new String[] {"TABLE"})) {
                if (!rows.next()) throw new IllegalStateException("Business ledger schema is incomplete");
            }
        }
        for (String[] column : new String[][] {{"common_cache_revision", "revision"},
                {"common_business_outbox", "state"}, {"common_request_execution", "state"}}) {
            try (var rows = connection.getMetaData().getColumns(connection.getCatalog(), null, column[0], column[1])) {
                if (!rows.next()) throw new IllegalStateException("Business ledger schema is incomplete");
            }
        }
    }

    static String lockKey(com.sma.admin.tenant.internal.persistence.StorageResourceEntity resource) {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(
                    (resource.getDbHost() + ":" + resource.getDbPort() + "/" + resource.getDatabaseName())
                            .getBytes(StandardCharsets.UTF_8));
            return "sma-business-migration-" + HexFormat.of().formatHex(digest, 0, 16);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
