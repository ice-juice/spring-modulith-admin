package com.sma.admin.common.internal.deployment;

import java.util.*;
import org.springframework.aop.support.AopUtils;
import org.springframework.modulith.ApplicationModule;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;
import com.sma.admin.common.deployment.DeploymentEvidence;
import com.sma.admin.common.deployment.SharedStorageWitness;

/** Lazy host-only discovery; default single-node assembly never probes a filesystem. */
@Component
public final class SharedStorageRegistry {
    static final int MAX_RESOURCES=32;
    private final List<SharedStorageWitness> providers;
    private Map<String,SharedStorageWitness> frozen;
    public SharedStorageRegistry(List<SharedStorageWitness> providers){
        if(providers.size()>32)throw new IllegalArgumentException("Too many shared storage providers");
        this.providers=List.copyOf(providers);
    }
    synchronized Map<String,SharedStorageWitness> resources(){
        DeploymentEvidence.requireHost();
        var found=new TreeMap<String,SharedStorageWitness>();
        for(var provider:providers){
            String owner=owner(provider);
            List<SharedStorageWitness.Resource> resources;
            try{resources=provider.resources();}catch(RuntimeException failure){throw unavailable();}
            if(resources==null||resources.size()>MAX_RESOURCES)throw unavailable();
            for(var resource:resources){
                if(resource==null||!resource.declaredShared()||!resource.id().startsWith(owner+".")
                        ||found.putIfAbsent(resource.id(),provider)!=null||found.size()>MAX_RESOURCES)throw unavailable();
            }
        }
        if(!found.containsKey("file.legacy"))throw unavailable();
        if(frozen==null)frozen=Collections.unmodifiableMap(found);
        else if(!frozen.keySet().equals(found.keySet())||found.entrySet().stream().anyMatch(entry->frozen.get(entry.getKey())!=entry.getValue()))throw unavailable();
        return frozen;
    }
    private static String owner(Object provider){
        var type=ClassUtils.getUserClass(AopUtils.getTargetClass(provider));
        var matcher=java.util.regex.Pattern.compile("^(com\\.sma\\.(admin|business))\\.([a-z]+)(?:\\..*)?$").matcher(type.getPackageName());
        if(!matcher.matches())throw unavailable();
        try{
            if(Class.forName(matcher.group(1)+"."+matcher.group(3)+".package-info",false,type.getClassLoader())
                    .getAnnotation(ApplicationModule.class)==null)throw unavailable();
        }catch(ClassNotFoundException failure){throw unavailable();}
        return matcher.group(3);
    }
    static IllegalStateException unavailable(){return new IllegalStateException("Shared storage witness unavailable");}
}
