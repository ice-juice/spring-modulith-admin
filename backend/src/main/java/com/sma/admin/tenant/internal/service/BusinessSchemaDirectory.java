package com.sma.admin.tenant.internal.service;

import java.util.*;
import java.util.regex.Pattern;
import com.sma.admin.common.persistence.BusinessSchemaCatalog;
import org.springframework.aop.support.AopUtils;
import org.springframework.modulith.ApplicationModule;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;

/** Verified static source ownership, not client-provided paths or arbitrary Flyway locations. */
@Component
public class BusinessSchemaDirectory {
    private static final Pattern MODULE=Pattern.compile("^com\\.sma\\.business\\.([a-z]+)(?:\\..*)?$");
    public record Schema(String module,String storageGroup) {
        public Schema {
            if(module==null||!module.matches("[a-z]{1,31}")||module.equals("ledger")||storageGroup==null
                    ||!storageGroup.matches("[a-z][a-z0-9-]{1,63}")||!(storageGroup.equals(module)||storageGroup.startsWith(module+"-")))
                throw new IllegalArgumentException("Invalid business schema descriptor");
        }
        public String location(){return "classpath:db/business/"+module;}
        public String history(){return "flyway_schema_history_business_"+module;}
    }
    private final Map<String,List<Schema>> groups;
    public BusinessSchemaDirectory(List<BusinessSchemaCatalog> catalogs) {
        var registered=new TreeMap<String,List<Schema>>();var modules=new HashSet<String>();
        for(var catalog:catalogs){
            var type=ClassUtils.getUserClass(AopUtils.getTargetClass(catalog));var match=MODULE.matcher(type.getPackageName());
            if(!match.matches())throw new IllegalArgumentException("Business schema catalog must belong to a business module");
            String module=match.group(1),group=catalog.storageGroup();
            if(module.length()>31||module.equals("ledger")||group==null||!group.matches("[a-z][a-z0-9-]{1,63}")
                    ||!(group.equals(module)||group.startsWith(module+"-")))throw new IllegalArgumentException("Invalid business schema ownership");
            try{
                if(Class.forName("com.sma.business."+module+".package-info",false,type.getClassLoader()).getAnnotation(ApplicationModule.class)==null)
                    throw new IllegalArgumentException("Business schema module metadata missing");
            }catch(ClassNotFoundException missing){throw new IllegalArgumentException("Business schema module metadata missing",missing);}
            if(!modules.add(module))throw new IllegalArgumentException("Duplicate business schema module");
            if(modules.size()>64)throw new IllegalArgumentException("Business schema directory capacity exceeded");
            registered.computeIfAbsent(group,ignored->new ArrayList<>()).add(new Schema(module,group));
        }
        var immutable=new TreeMap<String,List<Schema>>();registered.forEach((group,schemas)->immutable.put(group,List.copyOf(schemas)));
        groups=Map.copyOf(immutable);
    }
    public List<Schema> require(String group){
        if(group==null)throw new IllegalStateException("Business storage group has no declared schema catalog");
        var schemas=groups.get(group);
        if(schemas==null||schemas.isEmpty())throw new IllegalStateException("Business storage group has no declared schema catalog");
        return schemas;
    }
}
