package com.sma.admin.audit.internal.service;

import java.util.*;
import org.springframework.stereotype.Component;
import com.sma.admin.common.cache.*;

@Component
public class AuditCacheCatalog implements CacheCatalog {
    public Collection<CacheDescriptor> regions(){return List.of(new CacheDescriptor("audit.ip-library","audit","audit.cache.ip-library","reference-resources","ip-location",CacheDescriptor.Kind.RESOURCE,CacheDescriptor.Scope.NODE,1,null,Set.of(),Set.of("application-resource-upgrade"),"audit:login:view"));}
}
