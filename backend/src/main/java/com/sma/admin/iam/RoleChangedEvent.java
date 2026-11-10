package com.sma.admin.iam;

public record RoleChangedEvent(long tenantId, long roleId, String action) {}
