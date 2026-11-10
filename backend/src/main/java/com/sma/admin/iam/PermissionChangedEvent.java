package com.sma.admin.iam;

public record PermissionChangedEvent(long tenantId, String portal, Long roleId, String action) {}
