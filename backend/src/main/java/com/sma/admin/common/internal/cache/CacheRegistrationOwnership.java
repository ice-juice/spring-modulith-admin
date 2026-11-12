package com.sma.admin.common.internal.cache;

import java.util.regex.Pattern;
import org.springframework.aop.support.AopUtils;
import org.springframework.modulith.ApplicationModule;
import org.springframework.util.ClassUtils;
import com.sma.admin.common.cache.CacheDescriptor;

/** Startup ownership gate for trusted static modules. Not a sandbox for hostile Java code. */
public class CacheRegistrationOwnership {
    private static final Pattern MODULE=Pattern.compile("^(com\\.sma\\.(admin|business))\\.([a-z]+)(?:\\..*)?$");
    public void validate(Object provider,CacheDescriptor descriptor){
        var owner=owner(provider);
        if(!descriptor.owner().equals(owner.id()))throw new IllegalArgumentException("Cache catalog cannot register another module's region");
        if(owner.business()&&!descriptor.managePermission().startsWith(owner.id()+":"))
            throw new IllegalArgumentException("Business cache requires an owning business management permission");
    }
    public void validateLoader(Object provider,CacheDescriptor descriptor){
        var owner=owner(provider);
        if(!descriptor.owner().equals(owner.id()))throw new IllegalArgumentException("Cache warmup loader must belong to the registered region's module");
    }
    public void validateBusiness(Object provider,CacheDescriptor descriptor){
        if(!owner(provider).business())throw new IllegalArgumentException("Business cache catalog requires a business module");
        validate(provider,descriptor);
    }
    public void validateAdapter(Object provider,CacheDescriptor descriptor){
        if(!descriptor.owner().equals(owner(provider).id()))throw new IllegalArgumentException("Resource adapter must belong to its region's module");
    }
    private record Owner(String id,boolean business) {}
    private Owner owner(Object bean){
        var type=ClassUtils.getUserClass(AopUtils.getTargetClass(bean));var match=MODULE.matcher(type.getPackageName());
        if(!match.matches())throw new IllegalArgumentException("Cache provider must belong to a platform or business module");
        String root=match.group(1),id=match.group(3);
        try{
            var metadata=Class.forName(root+"."+id+".package-info",false,type.getClassLoader()).getAnnotation(ApplicationModule.class);
            if(metadata==null)throw new IllegalArgumentException("Cache provider module requires explicit Modulith metadata");
        }catch(ClassNotFoundException missing){throw new IllegalArgumentException("Cache provider module not declared",missing);}
        return new Owner(id,match.group(2).equals("business"));
    }
}
