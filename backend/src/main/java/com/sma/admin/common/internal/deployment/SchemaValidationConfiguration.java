package com.sma.admin.common.internal.deployment;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.boot.flyway.autoconfigure.FlywayProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.modulith.core.ApplicationModuleIdentifiers;

/** Explicit serving-node validation; the legacy single-node migrate default remains unchanged. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({SchemaValidationConfiguration.Properties.class, DeploymentTrafficProperties.class})
class SchemaValidationConfiguration {
    @ConfigurationProperties("sma.deployment")
    record Properties(SchemaMode schemaMode) {
        enum SchemaMode { MIGRATE, VALIDATE }
        Properties { if (schemaMode == null) schemaMode = SchemaMode.MIGRATE; }
    }

    @Bean
    @Primary
    @Lazy(false)
    @ConditionalOnProperty(name = "sma.deployment.schema-mode", havingValue = "validate")
    FlywayMigrationStrategy deploymentSchemaValidation(Properties properties, Environment environment,
                                                        ApplicationModuleIdentifiers identifiers) {
        if (!environment.getProperty("spring.flyway.enabled", Boolean.class, true)
                || !environment.getProperty("spring.modulith.runtime.flyway-enabled", Boolean.class, true)
                || environment.getProperty("spring.main.lazy-initialization", Boolean.class, false)) {
            throw new IllegalArgumentException("Schema validation requires eager startup with Flyway and module migrations enabled");
        }
        MigrationApplication.verifyProperties(Binder.get(environment).bind("spring.flyway", FlywayProperties.class)
                .orElseGet(FlywayProperties::new));
        var modules = java.util.stream.Stream.concat(java.util.stream.Stream.of("__root"),
                identifiers.stream().map(Object::toString)).toList();
        return flyway -> ModuleSchemaOperations.execute(flyway, ModuleSchemaOperations.Mode.VALIDATE, modules);
    }
}
