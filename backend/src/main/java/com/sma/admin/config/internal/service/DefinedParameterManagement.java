package com.sma.admin.config.internal.service;

import java.util.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.sma.admin.common.*;
import com.sma.admin.common.security.CurrentUser;
import com.sma.admin.config.*;
import com.sma.admin.config.internal.persistence.ParameterMapper;
import com.sma.admin.config.internal.service.ConfigurationCatalogRegistry.Kind;

/** Tenant management projections; runtime callers use ParameterQuery and do not need management permissions. */
@Service @Transactional(readOnly=true)
public class DefinedParameterManagement {
    private final ConfigurationCatalogRegistry catalog;
    private final DefinedParameterService layers;
    private final SharedConfigurationService shared;
    private final ParameterMapper rows;
    private final ConfigNames names;
    public DefinedParameterManagement(ConfigurationCatalogRegistry catalog,DefinedParameterService layers,SharedConfigurationService shared,ParameterMapper rows,ConfigNames names) {
        this.catalog=catalog;this.layers=layers;this.shared=shared;this.rows=rows;this.names=names;
    }
    public record View(String key,String owner,String groupKey,String name,String defaultName,String nameKey,Map<String,String> translations,int sort,
            ParameterDefinition.Scope scope,ParameterDefinition.ValueType valueType,ParameterDefinition.OverridePolicy overridePolicy,
            ParameterDefinition.Constraints constraints,boolean securitySensitive,String value,ConfigurationOrigin origin,
            ConfigurationRevision revision,Long rowId,boolean overridden) {
        @Override public String toString(){return "EffectiveParameterView[key="+key+", origin="+origin+", revision="+revision+"]";}
    }
    public PageResult<View> page(PageQuery page,String group,String keyword) {
        require("config:parameter:view");
        if(page.size()!=null&&page.size()>50 || group!=null&&group.length()>64 || keyword!=null&&keyword.length()>128)
            throw new BizException(CommonErrorCode.BAD_REQUEST);
        var pagination=page.<Object>toPage(Map.of("key","key"));
        String search=keyword==null?"":keyword.trim().toLowerCase(Locale.ROOT);
        var selected=catalog.entries().stream().filter(entry->entry.kind()==Kind.PARAMETER)
                .filter(entry->group==null||group.isBlank()||group.equals(entry.parameter().group()))
                .filter(entry->search.isEmpty()||entry.key().toLowerCase(Locale.ROOT).contains(search))
                .sorted(Comparator.comparing(ConfigurationCatalogRegistry.Entry::key)).map(ConfigurationCatalogRegistry.Entry::key).toList();
        // Filter immutable definition metadata first; read values for this page only, never all registered bodies.
        if("desc".equals(page.direction()))selected=selected.reversed();
        long offset=(pagination.getCurrent()-1)*pagination.getSize();
        var items=selected.stream().skip(offset).limit(pagination.getSize()).map(this::view).toList();
        return new PageResult<>(items,selected.size(),pagination.getCurrent(),pagination.getSize());
    }
    public View get(String key) {require("config:parameter:view");return view(key);}
    @Transactional public View override(String key,ConfigurationRevision expected,String value) {
        require("config:parameter:update");layers.override(key,expected,value);return view(key);
    }
    @Transactional public View inherit(String key,ConfigurationRevision expected) {
        require("config:parameter:update");layers.inherit(key,expected);return view(key);
    }
    public void require(String permission) {
        if(!Set.of("config:parameter:view","config:parameter:update").contains(permission)
                ||!CurrentUser.require().permissions().contains(permission)) throw new AccessDeniedException("Parameter management denied");
    }
    private View view(String key) {
        var state=layers.get(key);var definition=state.definition();var row=rows.current(definition.key());
        var label=row==null||!Boolean.TRUE.equals(row.getPresentationOverridden())?shared.published(Kind.PARAMETER,definition.key()).map(value->value.parameter().label()).orElse(definition.label())
                :new ConfigurationLabel(row.getName(),row.getNameKey(),names.translations("parameter",row.getId()));
        return new View(definition.key(),definition.owner(),definition.group(),names.name(label.text(),label.messageKey(),label.translations()),
                label.text(),label.messageKey(),label.translations(),row==null?0:row.getSort(),definition.scope(),definition.valueType(),definition.overridePolicy(),definition.constraints(),
                definition.securitySensitive(),state.snapshot().value(),state.snapshot().origin(),state.snapshot().revision(),state.rowId(),state.overridden());
    }
}
