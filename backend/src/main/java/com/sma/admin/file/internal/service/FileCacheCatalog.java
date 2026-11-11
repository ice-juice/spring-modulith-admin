package com.sma.admin.file.internal.service;

import java.util.*;
import org.springframework.stereotype.Component;
import com.sma.admin.common.cache.*;

@Component
public class FileCacheCatalog implements CacheCatalog {
    public Collection<CacheDescriptor> regions(){return List.of(
        new CacheDescriptor("file.storage","file","file.cache.storage","file-resources","persistent-files",CacheDescriptor.Kind.RESOURCE,CacheDescriptor.Scope.TENANT_PORTAL,1,null,Set.of(),Set.of("owning-file-workflow"),"file:file:view"),
        new CacheDescriptor("file.storage-witnesses","file","file.cache.storage-witnesses","file-resources","deployment-witnesses",CacheDescriptor.Kind.PROTECTED,CacheDescriptor.Scope.NODE,1,null,Set.of(),Set.of("owning-deployment-lifecycle"),"file:file:view"));}
}
