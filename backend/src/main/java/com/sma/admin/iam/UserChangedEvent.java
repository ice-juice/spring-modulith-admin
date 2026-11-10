package com.sma.admin.iam;

public record UserChangedEvent(long tenantId,long userId,String action,long credentialVersion) {}
