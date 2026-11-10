package com.sma.admin.config;
public record ConfigurationChangedEvent(long tenantId,String category,String key) {}
