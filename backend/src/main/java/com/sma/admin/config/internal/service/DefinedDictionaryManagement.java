package com.sma.admin.config.internal.service;

import java.util.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.sma.admin.common.*;
import com.sma.admin.common.security.CurrentUser;
import com.sma.admin.config.*;
import com.sma.admin.config.internal.service.ConfigurationCatalogRegistry.Kind;
import com.sma.admin.config.internal.service.DefinedDictionaryService.*;

/** Tenant management of registered dictionaries; runtime callers keep DictionaryQuery and need no management permission. */
@Service @Transactional(readOnly=true)
public class DefinedDictionaryManagement {
    private final ConfigurationCatalogRegistry catalog;
    private final DefinedDictionaryService dictionaries;
    private final ConfigNames names;
    public DefinedDictionaryManagement(ConfigurationCatalogRegistry catalog,DefinedDictionaryService dictionaries,ConfigNames names){
        this.catalog=catalog;this.dictionaries=dictionaries;this.names=names;
    }
    public record Summary(String key,String owner,DictionaryDefinition.Mode mode,boolean tree,String name,boolean enabled,ConfigurationRevision revision,
            int entries,int selectable,int overridden,int privateItems) {}
    public record View(String key,String owner,DictionaryDefinition.Mode mode,boolean tree,String name,ConfigurationLabel label,boolean enabled,
            ConfigurationRevision revision,List<DictionarySnapshot.Entry> entries,List<LayerEntry> layer) {
        @Override public String toString(){return "EffectiveDictionaryView[key="+key+", revision="+revision+"]";}
    }
    public PageResult<Summary> page(PageQuery page,String keyword) {
        require("config:dictionary:view");
        if(page.size()!=null&&page.size()>50||keyword!=null&&keyword.length()>128)throw new BizException(CommonErrorCode.BAD_REQUEST);
        var pagination=page.<Object>toPage(Map.of("key","key"));
        String search=keyword==null?"":keyword.trim().toLowerCase(Locale.ROOT);
        var keys=catalog.entries().stream().filter(entry->entry.kind()==Kind.DICTIONARY)
                .filter(entry->search.isEmpty()||entry.key().toLowerCase(Locale.ROOT).contains(search))
                .map(ConfigurationCatalogRegistry.Entry::key).sorted().toList();
        if("desc".equals(page.direction()))keys=keys.reversed();
        long offset=(pagination.getCurrent()-1)*pagination.getSize();
        // Definition metadata is filtered first; only this page's (at most 50) layers are read.
        var items=keys.stream().skip(offset).limit(pagination.getSize()).map(key->{
            var state=dictionaries.get(key);var snapshot=state.snapshot();
            return new Summary(key,state.definition().owner(),state.definition().mode(),state.definition().tree(),name(state.label()),state.enabled(),
                    snapshot.revision(),snapshot.entries().size(),(int)snapshot.entries().stream().filter(DictionarySnapshot.Entry::selectable).count(),
                    state.overriddenCodes().size(),state.privateCodes().size());
        }).toList();
        return new PageResult<>(items,keys.size(),pagination.getCurrent(),pagination.getSize());
    }
    public View get(String key){require("config:dictionary:view");return view(dictionaries.get(key));}
    @Transactional public View overrideItem(String key,ConfigurationRevision expected,String code,ItemChange change){
        require("config:dictionary:update");return view(dictionaries.overrideItem(key,expected,code,change));
    }
    @Transactional public View restoreItem(String key,ConfigurationRevision expected,String code){
        require("config:dictionary:update");return view(dictionaries.restoreItem(key,expected,code));
    }
    @Transactional public View savePrivate(String key,ConfigurationRevision expected,PrivateItem item){
        require("config:dictionary:update");return view(dictionaries.savePrivate(key,expected,item));
    }
    @Transactional public View enabled(String key,ConfigurationRevision expected,boolean enabled){
        require("config:dictionary:update");return view(dictionaries.enabled(key,expected,enabled));
    }
    public void require(String permission){
        if(!Set.of("config:dictionary:view","config:dictionary:update").contains(permission)||!CurrentUser.require().permissions().contains(permission))
            throw new AccessDeniedException("Dictionary management denied");
    }
    private View view(State state){
        var definition=state.definition();
        return new View(definition.key(),definition.owner(),definition.mode(),definition.tree(),name(state.label()),state.label(),state.enabled(),
                state.snapshot().revision(),state.snapshot().entries(),state.layer());
    }
    private String name(ConfigurationLabel label){return names.name(label.text(),label.messageKey(),label.translations());}
}
