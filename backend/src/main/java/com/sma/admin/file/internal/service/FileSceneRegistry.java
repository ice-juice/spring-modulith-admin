package com.sma.admin.file.internal.service;

import java.util.*;
import java.util.regex.Pattern;
import com.sma.admin.common.security.*;
import com.sma.admin.file.*;
import org.springframework.aop.support.AopUtils;
import org.springframework.modulith.ApplicationModule;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;

/** Registration ownership for trusted static Java modules, not a hostile-code sandbox. */
@Component
public class FileSceneRegistry {
    private static final Pattern MODULE=Pattern.compile("^(com\\.sma\\.(?:admin|business))\\.([a-z]+)(?:\\..*)?$");
    private final Map<String,FileSceneCatalog.Definition> scenes;private final Map<String,FileScenePolicy> policies;
    public FileSceneRegistry(List<FileSceneCatalog> catalogs,List<FileScenePolicy> providers){
        var registered=new TreeMap<String,FileSceneCatalog.Definition>();var policyMap=new HashMap<String,FileScenePolicy>();
        for(var catalog:catalogs){String owner=owner(catalog);var definitions=catalog.scenes();if(definitions==null || definitions.size()>1000)throw new IllegalArgumentException("Invalid scene catalog size");
            for(var scene:definitions){if(scene==null || !scene.application().equals(owner))throw new IllegalArgumentException("File catalog cannot register another module's scene");
                if(registered.putIfAbsent(scene.code(),scene)!=null)throw new IllegalStateException("Duplicate file scene");}}
        for(var policy:providers){if(!owner(policy).equals(policy.application()))throw new IllegalArgumentException("File policy belongs to another module");
            if(policyMap.putIfAbsent(policy.application(),policy)!=null)throw new IllegalStateException("Duplicate file scene policy");}
        scenes=Map.copyOf(registered);policies=Map.copyOf(policyMap);
    }
    public List<FileSceneCatalog.Definition> definitions(){return scenes.values().stream().sorted(Comparator.comparing(FileSceneCatalog.Definition::code)).toList();}
    public FileSceneCatalog.Definition require(String code){var scene=scenes.get(code);if(scene==null || !policies.containsKey(scene.application()))throw StorageSceneService.invalid();return scene;}
    public void authorize(FileSceneAccess request){
        var actor=ActorContext.require();var scene=require(request.scene());if(!scene.application().equals(request.application()))throw new AccessDeniedException("File application denied");
        if(actor.kind()==ActorRef.Kind.APP_USER)ActorContext.requireApplicationUser(request.application());
        if(!policies.get(request.application()).canUse(actor,request))throw new AccessDeniedException("File scene denied");
    }
    private String owner(Object provider){
        var type=ClassUtils.getUserClass(AopUtils.getTargetClass(provider));var match=MODULE.matcher(type.getPackageName());
        if(!match.matches())throw new IllegalArgumentException("File scene provider must belong to a declared module");
        try{if(Class.forName(match.group(1)+"."+match.group(2)+".package-info",false,type.getClassLoader()).getAnnotation(ApplicationModule.class)==null)throw new IllegalArgumentException("File scene module metadata required");}
        catch(ClassNotFoundException missing){throw new IllegalArgumentException("File scene module metadata missing",missing);}return match.group(2);
    }
}
