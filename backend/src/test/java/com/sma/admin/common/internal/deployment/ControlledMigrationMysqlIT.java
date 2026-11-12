package com.sma.admin.common.internal.deployment;

import static org.assertj.core.api.Assertions.*;
import java.sql.DriverManager;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import com.sma.admin.support.TestDatabase;

/** Explicitly selected, standalone destructive suite; never runs beside a serving test context. */
class ControlledMigrationMysqlIT {
    @org.junit.jupiter.api.AfterEach void cleanOwnedDatabase() { TestDatabase.clean(); }
    @Test void migratesAllModulesWithoutBusinessStartupAndValidationDoesNotWrite() throws Exception {
        TestDatabase.clean();
        assertThatThrownBy(() -> MigrationApplication.application(ModuleSchemaOperations.Mode.VALIDATE, com.sma.admin.SpringbootModulithAdminApplication.class).run(arguments()))
                .isInstanceOf(Exception.class);
        assertThat(tableRows()).isEmpty();
        try (var context = MigrationApplication.application(ModuleSchemaOperations.Mode.MIGRATE, com.sma.admin.SpringbootModulithAdminApplication.class).run(arguments())) {
            assertThat(java.util.Arrays.stream(context.getBeanDefinitionNames()).filter(name -> !name.equals("controlledSchemaOperation"))
                    .map(name -> context.getType(name, false))
                    .filter(java.util.Objects::nonNull).map(Class::getName).filter(name -> name.startsWith("com.sma.admin.")).toList())
                    .containsExactly(MigrationApplication.class.getName());
            assertThat(context.getBeansOfType(org.springframework.scheduling.TaskScheduler.class)).isEmpty();
            assertThat(context.getBeansOfType(org.springframework.web.servlet.DispatcherServlet.class)).isEmpty();
            assertThat(context.getBeansOfType(org.springframework.boot.ApplicationRunner.class)).isEmpty();
            assertThat(context.getBeansOfType(org.springframework.boot.CommandLineRunner.class)).isEmpty();
        }
        var before = tableRows();
        assertThat(before).containsKeys("flyway_schema_history", "flyway_schema_history_common", "flyway_schema_history_auth");
        assertThat(before.get("auth_session")).isZero();
        try (var ignored = MigrationApplication.application(ModuleSchemaOperations.Mode.MIGRATE, com.sma.admin.SpringbootModulithAdminApplication.class).run(arguments())) { }
        try (var ignored = MigrationApplication.application(ModuleSchemaOperations.Mode.VALIDATE, com.sma.admin.SpringbootModulithAdminApplication.class).run(arguments())) { }
        new ApplicationContextRunner().withUserConfiguration(SchemaValidationConfiguration.class)
                .withBean(org.springframework.modulith.core.ApplicationModuleIdentifiers.class,
                        () -> org.springframework.modulith.core.ApplicationModuleIdentifiers.of(
                                org.springframework.modulith.core.ApplicationModules.of(com.sma.admin.SpringbootModulithAdminApplication.class)))
                .withPropertyValues("sma.deployment.schema-mode=validate").run(context -> {
                    assertThat(context).hasNotFailed();
                    context.getBean(FlywayMigrationStrategy.class).migrate(base());
                });
        assertThat(tableRows()).isEqualTo(before);
        // The upstream strategy can still consume the exact same history layout without reapplying scripts.
        new org.springframework.modulith.runtime.flyway.SpringModulithFlywayMigrationStrategy(
                org.springframework.modulith.core.ApplicationModuleIdentifiers.of(
                        org.springframework.modulith.core.ApplicationModules.of(com.sma.admin.SpringbootModulithAdminApplication.class)),
                (id, flyway) -> before.containsKey(id.toString().equals("__root") ? "flyway_schema_history"
                        : "flyway_schema_history_" + id)).migrate(base());
        assertThat(tableRows()).isEqualTo(before);
    }

    @Test void refusesPendingFutureAndChecksumMismatchWithoutRepairOrDdl() throws Exception {
        TestDatabase.clean();
        Flyway.configure().dataSource(TestDatabase.URL, TestDatabase.USERNAME, TestDatabase.PASSWORD)
                .locations("classpath:db/migration/schemaprobe").table("flyway_schema_history_schemaprobe").target("1").load().migrate();
        var pending = tableRows();
        assertThatThrownBy(() -> probe(ModuleSchemaOperations.Mode.VALIDATE)).isInstanceOf(org.flywaydb.core.api.exception.FlywayValidateException.class)
                .hasMessageContaining("not applied");
        assertThat(tableRows()).isEqualTo(pending);
        probe(ModuleSchemaOperations.Mode.MIGRATE);
        probe(ModuleSchemaOperations.Mode.VALIDATE);
        // Test-owned history corruption only, simulating a changed already-applied artifact.
        try (var connection = DriverManager.getConnection(TestDatabase.URL, TestDatabase.USERNAME, TestDatabase.PASSWORD);
             var statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE flyway_schema_history_schemaprobe SET checksum=42 WHERE version='1'");
        }
        assertThatThrownBy(() -> probe(ModuleSchemaOperations.Mode.VALIDATE)).isInstanceOf(org.flywaydb.core.api.exception.FlywayValidateException.class)
                .hasMessageContaining("checksum mismatch");
        try (var connection = DriverManager.getConnection(TestDatabase.URL, TestDatabase.USERNAME, TestDatabase.PASSWORD);
             var statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE flyway_schema_history_schemaprobe SET version='999' WHERE version='1'");
        }
        assertThatThrownBy(() -> probe(ModuleSchemaOperations.Mode.VALIDATE)).isInstanceOf(org.flywaydb.core.api.exception.FlywayValidateException.class)
                .hasMessageContaining("not resolved locally");
        assertThatThrownBy(() -> probe(ModuleSchemaOperations.Mode.MIGRATE)).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> ModuleSchemaOperations.execute(base(), ModuleSchemaOperations.Mode.VALIDATE, List.of("common")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("history absent from the artifact");
    }

    private void probe(ModuleSchemaOperations.Mode mode) {
        ModuleSchemaOperations.execute(base(), mode, List.of("schemaprobe"));
    }
    private Flyway base() {
        return Flyway.configure().dataSource(TestDatabase.URL, TestDatabase.USERNAME, TestDatabase.PASSWORD)
                .locations("classpath:db/migration").load();
    }
    private String[] arguments() {
        return new String[]{"--spring.profiles.active=test", "--spring.datasource.url=" + TestDatabase.URL,
                "--spring.datasource.username=" + TestDatabase.USERNAME, "--spring.datasource.password=" + TestDatabase.PASSWORD,
                "--spring.datasource.hikari.maximum-pool-size=2", "--spring.datasource.hikari.minimum-idle=0"};
    }
    private Map<String, Long> tableRows() throws Exception {
        var result = new TreeMap<String, Long>();
        try (var connection = DriverManager.getConnection(TestDatabase.URL, TestDatabase.USERNAME, TestDatabase.PASSWORD);
             var statement = connection.createStatement();
             var tables = statement.executeQuery("SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE()")) {
            var names = new java.util.ArrayList<String>();
            while (tables.next()) names.add(tables.getString(1));
            for (var name : names) {
                if (!name.matches("[A-Za-z0-9_]+")) throw new IllegalStateException("Unexpected fixture table");
                try (var counter = connection.createStatement(); var rows = counter.executeQuery("SELECT COUNT(*) FROM `" + name + "`")) {
                    rows.next(); result.put(name, rows.getLong(1));
                }
            }
        }
        return result;
    }
}
