package com.sma.admin.common.security;

/** A module contributes navigation/resources without depending on IAM. */
public record PermissionSpec(String key, String parentKey, String nameKey, String defaultName,
        String type, String path, String icon, int sort, String portal, boolean superAdminDefault) {}
