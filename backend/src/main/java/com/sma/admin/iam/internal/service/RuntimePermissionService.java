package com.sma.admin.iam.internal.service;

import java.util.Set;
import org.springframework.stereotype.Service;
import com.sma.admin.common.security.*;

@Service
public class RuntimePermissionService implements RuntimePermissionRegistrar {
    private final PermissionService permissions;
    public RuntimePermissionService(PermissionService permissions){this.permissions=permissions;}
    @Override public void extensionEntries(Set<String> keys) {
        if(keys.size()>10000 || keys.stream().anyMatch(key -> !key.matches("extension:invoke:[a-z][a-z0-9-]{0,79}")))
            throw new IllegalArgumentException("Invalid extension permission declarations");
        permissions.sync(keys.stream().sorted().map(key -> new PermissionSpec(key,null,null,key,"LOGIC",null,null,0,CurrentUser.portal(),false)).toList());
    }
}
