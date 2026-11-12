package com.sma.admin.common.internal.deployment;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.sql.SQLException;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationState;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.modulith.core.ApplicationModules;

/** Deployment infrastructure only. No application context or business module is instantiated. */
final class ModuleSchemaOperations {
    enum Mode { MIGRATE, VALIDATE }

    private ModuleSchemaOperations() { }

    static List<String> modules(Class<?> applicationType) {
        var result = new ArrayList<String>();
        result.add("__root");
        ApplicationModules.of(applicationType)
                .forEach(module -> result.add(module.getIdentifier().toString()));
        return List.copyOf(result);
    }

    static void execute(Flyway base, Mode mode, List<String> modules) {
        var config = base.getConfiguration();
        if (config.getLocations().length != 1
                || !config.getLocations()[0].getDescriptor().equals("classpath:db/migration")
                || !config.getTable().equals("flyway_schema_history")) {
            throw new IllegalArgumentException("Controlled schema operations require the standard module locations and history tables");
        }
        if (config.getDataSource() instanceof com.zaxxer.hikari.HikariDataSource pool
                && (pool.getCatalog() != null || pool.getSchema() != null || pool.getConnectionInitSql() != null)) {
            throw new IllegalArgumentException("Controlled schema operations reject pool catalog, schema and initialization SQL overrides");
        }
        var migrations = new ArrayList<Flyway>();
        var knownHistories = new HashSet<String>();
        var nonEmpty = new HashSet<Flyway>();
        for (String module : modules) {
            if (!module.matches("__root|[a-z]+")) throw new IllegalArgumentException("Invalid schema module identifier");
            String history = module.equals("__root") ? "flyway_schema_history" : "flyway_schema_history_" + module;
            if (!knownHistories.add(history)) throw new IllegalArgumentException("Duplicate schema module identifier");
            var migration = Flyway.configure().configuration(config)
                    .locations("classpath:db/migration/" + module)
                    .table(history)
                    // Same history layout and baseline as Spring Modulith 2.1. Never repair/clean.
                    .baselineVersion("0").baselineOnMigrate(true).cleanDisabled(true)
                    .validateOnMigrate(true).outOfOrder(false).ignoreMigrationPatterns(new String[0])
                    .target("latest").callbacks(new org.flywaydb.core.api.callback.Callback[0])
                    .skipDefaultCallbacks(true).load();
            migrations.add(migration);
            if (hasSql(module)) nonEmpty.add(migration);
        }
        if (nonEmpty.isEmpty()) throw new IllegalStateException("No module migrations found in the artifact");
        // Detect a removed entire module as well as a missing script within a still-present module.
        try (var connection = config.getDataSource().getConnection(); var statement = connection.prepareStatement(
                "SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE()")) {
            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    String table = rows.getString(1);
                    if ((table.equals("flyway_schema_history") || table.startsWith("flyway_schema_history_"))
                            && !knownHistories.contains(table))
                        throw new IllegalStateException("Database contains a module history absent from the artifact");
                }
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("Cannot inspect module schema histories");
        }
        if (mode == Mode.MIGRATE) {
            for (Flyway migration : migrations) if (nonEmpty.contains(migration)) migration.migrate();
        }
        for (Flyway migration : migrations) {
            migration.validate();
            for (var info : migration.info().all()) {
                if (info.getState() != MigrationState.SUCCESS && info.getState() != MigrationState.BASELINE
                        && info.getState() != MigrationState.OUTDATED && info.getState() != MigrationState.SUPERSEDED) {
                    throw new IllegalStateException("Module schema is not fully applied: " + migration.getConfiguration().getTable());
                }
            }
        }
    }

    private static boolean hasSql(String module) {
        try {
            return new PathMatchingResourcePatternResolver().getResources(
                    "classpath*:db/migration/" + module + "/**/*.sql").length > 0;
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot inspect packaged module migrations");
        }
    }
}
