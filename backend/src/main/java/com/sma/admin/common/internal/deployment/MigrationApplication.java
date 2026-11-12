package com.sma.admin.common.internal.deployment;

import java.util.Arrays;
import java.util.Map;
import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.flyway.autoconfigure.FlywayProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.GenericApplicationContext;

/** Explicit CLI source, deliberately NOT a component/configuration or an auto-scanning application. */
@ImportAutoConfiguration(DataSourceAutoConfiguration.class)
@EnableConfigurationProperties(FlywayProperties.class)
public final class MigrationApplication {
    private MigrationApplication() { }

    /** Exact opt-in; malformed/duplicate switches cannot fall through into a serving process. */
    public static boolean runIfRequested(Class<?> applicationType, String[] args) {
        var commands = Arrays.stream(args).filter(value -> value.startsWith("--sma-migration")).toList();
        if (commands.isEmpty()) return false;
        if (commands.size() != 1 || !(commands.getFirst().equals("--sma-migration=migrate")
                || commands.getFirst().equals("--sma-migration=validate"))) {
            throw new IllegalArgumentException("Use exactly one --sma-migration=migrate or --sma-migration=validate");
        }
        var mode = commands.getFirst().endsWith("=migrate")
                ? ModuleSchemaOperations.Mode.MIGRATE : ModuleSchemaOperations.Mode.VALIDATE;
        var app = application(mode, applicationType);
        try (var context = app.run(args)) {
            context.getBean("controlledSchemaOperation"); // Also execute when an external config requests lazy beans.
        }
        return true;
    }

    static SpringApplication application(ModuleSchemaOperations.Mode mode, Class<?> applicationType) {
        var app = new SpringApplication(MigrationApplication.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        app.setApplicationContextFactory(type -> new GenericApplicationContext());
        app.setRegisterShutdownHook(false);
        app.setLogStartupInfo(false);
        app.setDefaultProperties(Map.of("spring.main.banner-mode", "off"));
        app.addInitializers(context -> {
            if (app.getAllSources().size() != 1 || !app.getAllSources().contains(MigrationApplication.class)
                    || app.getWebApplicationType() != WebApplicationType.NONE) {
                throw new IllegalArgumentException("Migration command cannot add application sources or a web server");
            }
            context.getBeanFactory().registerSingleton("controlledSchemaMode", mode);
            context.getBeanFactory().registerSingleton("controlledSchemaModules", new Modules(ModuleSchemaOperations.modules(applicationType)));
        });
        return app;
    }

    record Modules(java.util.List<String> names) { }

    @Bean
    InitializingBean controlledSchemaOperation(DataSource dataSource, FlywayProperties properties,
                                               ModuleSchemaOperations.Mode mode, Modules modules) {
        verifyProperties(properties);
        return () -> ModuleSchemaOperations.execute(Flyway.configure().dataSource(dataSource)
                .locations(properties.getLocations().toArray(String[]::new)).table(properties.getTable())
                .connectRetries(0).lockRetryCount(10).load(), mode, modules.names());
    }

    static void verifyProperties(FlywayProperties properties) {
        // Deployment credentials come only from the normal datasource configuration. No second target.
        if (!properties.isEnabled() || properties.getUrl() != null || properties.getUser() != null || properties.getPassword() != null
                || !properties.getSchemas().isEmpty() || properties.getDefaultSchema() != null
                || (properties.getTarget() != null && !properties.getTarget().equals("latest")) || properties.isOutOfOrder()) {
            throw new IllegalArgumentException("Controlled migration does not accept alternate Flyway connections, schemas or targets");
        }
    }
}
