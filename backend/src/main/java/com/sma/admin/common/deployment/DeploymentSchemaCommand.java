package com.sma.admin.common.deployment;

/**
 * Host bootstrap contract, before SpringApplication.run. Not a business or HTTP operation.
 * The host supplies its actual annotated application type so static business modules are discovered too.
 */
public final class DeploymentSchemaCommand {
    private DeploymentSchemaCommand() { }

    public static boolean runIfRequested(Class<?> applicationType, String[] args) {
        return com.sma.admin.common.internal.deployment.MigrationApplication.runIfRequested(applicationType, args);
    }
}
