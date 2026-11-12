package com.sma.admin.iam.internal.service;

import java.util.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import com.sma.admin.common.security.*;

@Component
public class PermissionCatalogSync {
    private final List<PermissionCatalog> catalogs;
    private final RequestMappingHandlerMapping mappings;
    private final PermissionService service;
    private final PlatformTenantQuery platform;
    public PermissionCatalogSync(List<PermissionCatalog> catalogs,@Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mappings,PermissionService service,PlatformTenantQuery platform) {
        this.catalogs=catalogs; this.mappings=mappings; this.service=service;this.platform=platform;
    }
    @EventListener(ApplicationReadyEvent.class)
    public void synchronize() {
        TenantScope.within(platform.platformTenantId(),()->{synchronizePlatform();return null;});
    }
    private void synchronizePlatform() {
        List<PermissionSpec> declared=new ArrayList<>();
        catalogs.forEach(catalog -> declared.addAll(catalog.permissions()));
        Set<String> keys=new HashSet<>(); declared.forEach(spec -> { if(!keys.add(spec.portal()+"/"+spec.key())) throw new IllegalStateException("Duplicate declared permission: "+spec.key()); });
        Set<String> annotated=new TreeSet<>();
        mappings.getHandlerMethods().values().forEach(handler -> {
            var permission=AnnotatedElementUtils.findMergedAnnotation(handler.getMethod(),RequiresPermission.class);
            if(permission==null) permission=AnnotatedElementUtils.findMergedAnnotation(handler.getBeanType(),RequiresPermission.class);
            if(permission!=null) annotated.add(permission.value());
        });
        for(String key:annotated) if(keys.add("admin/"+key)) declared.add(new PermissionSpec(key,null,null,key,"LOGIC",null,null,100,"admin",true));
        service.sync(declared);
    }
}
