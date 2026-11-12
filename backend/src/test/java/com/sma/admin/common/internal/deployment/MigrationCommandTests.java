package com.sma.admin.common.internal.deployment;

import static org.assertj.core.api.Assertions.*;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class MigrationCommandTests {
    private static final Class<?> APPLICATION = com.sma.admin.SpringbootModulithAdminApplication.class;
    @Test void onlyAnExactSingleCommandCanSelectMigrationMode() {
        assertThat(MigrationApplication.runIfRequested(APPLICATION, new String[]{"--server.port=0"})).isFalse();
        for (String[] args : List.of(new String[]{"--sma-migration"}, new String[]{"--sma-migration=repair"},
                new String[]{"--sma-migration=validate", "--sma-migration=migrate"},
                new String[]{"--sma-migration-mode=migrate"})) {
            assertThatThrownBy(() -> MigrationApplication.runIfRequested(APPLICATION, args)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test void discoversActualModulesWithoutCreatingBusinessBeans() {
        assertThat(ModuleSchemaOperations.modules(APPLICATION)).startsWith("__root")
                .contains("auth", "common", "file", "message", "tenant").doesNotHaveDuplicates();
    }

    @Test void refusesAlternateLocationsAndHistoryBeforeConnecting() {
        for (var base : List.of(Flyway.configure().locations("filesystem:untrusted").load(),
                Flyway.configure().locations("classpath:db/migration").table("other_history").load())) {
            assertThatThrownBy(() -> ModuleSchemaOperations.execute(base, ModuleSchemaOperations.Mode.VALIDATE, List.of("common")))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("standard module");
        }
    }

    @Test void rejectsAdditionalApplicationSourcesAndWebOverrideBeforeConnecting() {
        for (String override : List.of("--spring.main.sources=com.sma.admin.SpringbootModulithAdminApplication",
                "--spring.main.web-application-type=servlet")) {
            assertThatThrownBy(() -> MigrationApplication.application(ModuleSchemaOperations.Mode.VALIDATE, APPLICATION)
                    .run(override)).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("cannot add application sources or a web server");
        }
    }

    @Test void rejectsAlternateFlywayConnectionAndPartialTargets() {
        var properties = new org.springframework.boot.flyway.autoconfigure.FlywayProperties();
        MigrationApplication.verifyProperties(properties);
        properties.setTarget("1");
        assertThatThrownBy(() -> MigrationApplication.verifyProperties(properties)).isInstanceOf(IllegalArgumentException.class);
        properties.setTarget("latest"); properties.setUrl("jdbc:mysql://127.0.0.1/unexpected");
        assertThatThrownBy(() -> MigrationApplication.verifyProperties(properties)).isInstanceOf(IllegalArgumentException.class);
        properties.setUrl(null); properties.setEnabled(false);
        assertThatThrownBy(() -> MigrationApplication.verifyProperties(properties)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void rejectsConnectionInitializationSqlBeforeOpeningPool() {
        try (var pool = new com.zaxxer.hikari.HikariDataSource()) {
            pool.setConnectionInitSql("SET @schema_probe=1");
            var base = Flyway.configure().dataSource(pool).locations("classpath:db/migration").load();
            assertThatThrownBy(() -> ModuleSchemaOperations.execute(base, ModuleSchemaOperations.Mode.VALIDATE, List.of("common")))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("initialization SQL");
            assertThat(pool.isRunning()).isFalse();
        }
    }

    @Test void schemaModeIsBoundAndCannotDisableValidation() {
        var runner = new ApplicationContextRunner().withUserConfiguration(SchemaValidationConfiguration.class)
                .withBean(org.springframework.modulith.core.ApplicationModuleIdentifiers.class,
                        () -> org.springframework.modulith.core.ApplicationModuleIdentifiers.of(List.of()));
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(SchemaValidationConfiguration.Properties.class).schemaMode())
                    .isEqualTo(SchemaValidationConfiguration.Properties.SchemaMode.MIGRATE);
            assertThat(context).doesNotHaveBean("deploymentSchemaValidation");
        });
        runner.withPropertyValues("sma.deployment.schema-mode=validate").run(context ->
                assertThat(context).hasNotFailed().hasBean("deploymentSchemaValidation"));
        runner.withPropertyValues("sma.deployment.schema-mode=ignore").run(context -> assertThat(context).hasFailed());
        for (String flag : List.of("spring.flyway.enabled=false", "spring.modulith.runtime.flyway-enabled=false",
                "spring.main.lazy-initialization=true")) {
            runner.withPropertyValues("sma.deployment.schema-mode=validate", flag)
                    .run(context -> assertThat(context).hasFailed());
        }
    }
}
