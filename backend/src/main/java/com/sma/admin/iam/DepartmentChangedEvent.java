package com.sma.admin.iam;

public record DepartmentChangedEvent(long tenantId, long departmentId, String action) {}
