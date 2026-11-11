package com.sma.admin.audit.internal.service;

import org.springframework.stereotype.Component;
import org.springframework.security.access.AccessDeniedException;
import com.sma.admin.common.cache.CacheResourceAdapter;
import com.sma.admin.common.security.CurrentUser;

@Component
public class IpLibraryCacheAdapter implements CacheResourceAdapter {
    // Successful construction of the owning resource is the only readiness claim; never close it for cache management.
    public IpLibraryCacheAdapter(IpLocationService locations){}
    public String region(){return "audit.ip-library";}
    public Snapshot inspect(){
        if(!CurrentUser.require().permissions().contains("audit:login:view"))throw new AccessDeniedException("IP library view denied");
        return new Snapshot(2,2,-1,2,"audit.cache.lifecycle.ip-library");
    }
}
