package com.sma.admin.iam.internal.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.sma.admin.common.cache.*;
import com.sma.admin.common.security.CurrentUser;

/** IAM-owned display snapshots only; account and authorization queries never use this adapter. */
@Component
public class IamDisplayCaches {
    public static final String DEPARTMENTS="iam.department-display", POSTS="iam.post-options",
            PERMISSIONS="iam.menu-metadata", NAVIGATION="iam.navigation-metadata", USERS="iam.user-display";
    private final ManagedCache cache;
    private final TransactionTemplate reads;
    public IamDisplayCaches(ManagedCache cache, PlatformTransactionManager transactions) {
        this.cache=cache; reads=new TransactionTemplate(transactions); reads.setReadOnly(true);
    }
    public <T> T get(String region,String key,Class<T> type,Supplier<T> loader) {
        return cache.get(region,digest(key),type,()->reads.execute(status->loader.get()));
    }
    public void invalidate(String region) { cache.invalidate(region); }
    /** Mirrors the actual DepartmentMapper grant predicate; never accepts a browser-supplied scope. */
    public String departmentScope() {
        return CurrentUser.optional().map(user -> {
            if(user.superAdmin() || user.dataAccess().all()) return "human:all";
            return "human:"+user.userId()+":"+user.dataAccess().departmentIds().stream().sorted().toList();
        }).orElse("trusted-system");
    }
    private String digest(String key) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8))); }
        catch(NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
