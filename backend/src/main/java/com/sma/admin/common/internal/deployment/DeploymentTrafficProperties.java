package com.sma.admin.common.internal.deployment;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Restart-only deployment policy. Never a tenant-editable or dynamically refreshed setting. */
@ConfigurationProperties("sma.deployment.traffic")
record DeploymentTrafficProperties(boolean enabled) {}
