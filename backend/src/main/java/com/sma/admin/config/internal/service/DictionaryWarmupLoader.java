package com.sma.admin.config.internal.service;

import java.time.Duration;
import java.util.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.*;
import com.sma.admin.common.cache.CacheWarmupLoader;
import com.sma.admin.common.security.CurrentUser;
import com.sma.admin.config.internal.persistence.*;

/** Version 2 enumerates registered shared/tenant-only definitions first (they may have no tenant row), then legacy private types. */
@Component
public class DictionaryWarmupLoader implements CacheWarmupLoader {
    private final DictionaryTypeMapper types;
    private final DictionaryService dictionaries;
    private final TransactionTemplate reads;
    private final ConfigurationCatalogRegistry catalog;
    public DictionaryWarmupLoader(DictionaryTypeMapper types,DictionaryService dictionaries,PlatformTransactionManager transactions,ConfigurationCatalogRegistry catalog){
        this.types=types;this.dictionaries=dictionaries;this.catalog=catalog;reads=new TransactionTemplate(transactions);reads.setReadOnly(true);reads.setTimeout(3);
    }
    @Override public Spec specification(){return new Spec(ConfigCacheCatalog.DICTIONARIES,2,"config:dictionary:view",new Limits(16,1000,20,Duration.ofMinutes(2)));}
    @Override public Page enumerate(String cursor,int limit){
        require();if(limit<1||limit>16)throw new IllegalArgumentException("Invalid warmup page limit");
        if(cursor==null||!cursor.matches("(?:|c[0-9]{1,3}:[01]|[0-9]{1,19}:[01])"))throw new IllegalArgumentException("Invalid dictionary cursor");
        var registered=catalog.entries().stream().filter(entry->entry.kind()==ConfigurationCatalogRegistry.Kind.DICTIONARY)
                .map(ConfigurationCatalogRegistry.Entry::key).sorted().toList();
        var keys=new ArrayList<String>();
        if(cursor.isEmpty()||cursor.startsWith("c")) {
            int position=cursor.isEmpty()?0:Integer.parseInt(cursor.substring(1,cursor.indexOf(':')))*2+(cursor.endsWith(":1")?1:0)+1;
            String next=cursor;
            for(;position<registered.size()*2;position++) {
                if(keys.size()==limit)return new Page(keys,next,false);
                keys.add(registered.get(position/2)+"|"+(position%2==0?"zh-CN":"en-US"));next="c"+(position/2)+":"+(position%2);
            }
            if(keys.size()==limit)return new Page(keys,"0:1",false);
            cursor="0:1"; // Legacy keyset start: after id 0, both languages done.
        }
        String legacyCursor=cursor;
        long after=cursor.isEmpty()?0:Long.parseLong(cursor.substring(0,cursor.indexOf(':')));
        boolean second=!cursor.isEmpty()&&cursor.endsWith(":0");
        return reads.execute(status->{
            var query=new LambdaQueryWrapper<DictionaryTypeEntity>().eq(DictionaryTypeEntity::getEnabled,true);
            if(!registered.isEmpty())query.notIn(DictionaryTypeEntity::getTypeKey,registered);
            if(second)query.ge(DictionaryTypeEntity::getId,after);else query.gt(DictionaryTypeEntity::getId,after);
            var rows=types.selectList(query.orderByAsc(DictionaryTypeEntity::getId).last("LIMIT 9"));
            String next=legacyCursor;boolean exhausted=true;
            for(var row:rows)for(int language=0;language<2;language++){
                if(second&&row.getId()==after&&language==0)continue;
                if(keys.size()==limit){exhausted=false;break;}
                keys.add(row.getTypeKey()+"|"+(language==0?"zh-CN":"en-US"));next=row.getId()+":"+language;
            }
            return new Page(keys,next,exhausted&&rows.size()<9);
        });
    }
    @Override public void load(String key){
        require();if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Warmup cache reads must not be transactional");
        if(key==null||!key.matches("[a-z][a-z0-9_-]{0,63}\\|(zh-CN|en-US)"))throw new IllegalArgumentException("Invalid dictionary warmup key");
        var previous=LocaleContextHolder.getLocaleContext();int split=key.indexOf('|');
        try{LocaleContextHolder.setLocale(Locale.forLanguageTag(key.substring(split+1)));dictionaries.warmOptions(key.substring(0,split));}
        finally{LocaleContextHolder.setLocaleContext(previous);}
    }
    private void require(){if(!CurrentUser.require().permissions().contains("config:dictionary:view"))throw new AccessDeniedException("Dictionary warmup denied");}
}
