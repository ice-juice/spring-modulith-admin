package com.sma.admin.common.internal.cache;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import com.sma.admin.common.cache.CacheDescriptor;
import com.sma.admin.common.security.CurrentUser;

/** Storage-independent identity. No caller-supplied tenant/user/portal and no retained raw business key. */
record CacheLookup(CacheDescriptor descriptor, long tenant, String portal, long user, String digest, Class<?> valueType) {
    static CacheLookup resolve(CacheRegistry registry, String region, String key, Class<?> type) {
        var descriptor = registry.require(region);
        if (descriptor.kind() != CacheDescriptor.Kind.DATA) throw new IllegalArgumentException("Region does not support data caching");
        if (key == null || key.length() > 2048 || type == null) throw new IllegalArgumentException("Invalid cache lookup");
        if (!(type == String.class || type == Boolean.class || type == Long.class || type == Integer.class
                || type == java.math.BigDecimal.class || type.isRecord())) {
            throw new IllegalArgumentException("Cache values must be explicit records or scalar values");
        }
        String portal = switch (descriptor.scope()) {
            case TENANT_PORTAL, TENANT_PORTAL_USER -> CurrentUser.portal();
            default -> "";
        };
        long user = switch (descriptor.scope()) {
            case TENANT_USER, TENANT_PORTAL_USER -> CurrentUser.require().userId();
            default -> 0;
        };
        return new CacheLookup(descriptor, CurrentUser.tenantId(), portal, user, hash(key), type);
    }

    static String hash(String key) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
