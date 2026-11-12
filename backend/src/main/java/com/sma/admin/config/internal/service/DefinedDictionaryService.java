package com.sma.admin.config.internal.service;

import java.util.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import tools.jackson.databind.json.JsonMapper;
import com.sma.admin.common.*;
import com.sma.admin.common.cache.ManagedCache;
import com.sma.admin.common.security.*;
import com.sma.admin.config.*;
import com.sma.admin.config.internal.persistence.*;
import com.sma.admin.config.internal.service.ConfigurationCatalogRegistry.Entry;
import com.sma.admin.config.internal.service.ConfigurationCatalogRegistry.Kind;
import com.sma.admin.config.internal.service.SharedConfigurationRules.ItemOverride;

/** Tenant dictionary layers in the platform database. Shared defaults are inherited, never copied per tenant. */
@Service @Transactional(readOnly=true)
public class DefinedDictionaryService {
    private static final String OVERRIDE="OVERRIDE", PRIVATE="PRIVATE";
    private final ConfigurationCatalogRegistry catalog;
    private final SharedConfigurationService shared;
    private final SharedHeadMapper heads;
    private final DictionaryCodeRegistry codes;
    private final DictionaryLayerMapper layers;
    private final DictionaryEntryMapper entries;
    private final DictionaryTypeMapper legacyTypes;
    private final DictionaryItemMapper legacyItems;
    private final ConfigNames names;
    private final WriteLockMapper locks;
    private final ManagedCache cache;
    private final ApplicationEventPublisher events;
    private final MessageSource messages;
    private final JsonMapper json;
    private final SharedConfigurationRules rules;
    public DefinedDictionaryService(ConfigurationCatalogRegistry catalog,SharedConfigurationService shared,SharedHeadMapper heads,DictionaryCodeRegistry codes,
            DictionaryLayerMapper layers,DictionaryEntryMapper entries,DictionaryTypeMapper legacyTypes,DictionaryItemMapper legacyItems,ConfigNames names,
            WriteLockMapper locks,ManagedCache cache,ApplicationEventPublisher events,MessageSource messages,JsonMapper json) {
        this.catalog=catalog;this.shared=shared;this.heads=heads;this.codes=codes;this.layers=layers;this.entries=entries;this.legacyTypes=legacyTypes;
        this.legacyItems=legacyItems;this.names=names;this.locks=locks;this.cache=cache;this.events=events;this.messages=messages;this.json=json;
        this.rules=new SharedConfigurationRules(json);
    }
    /** Null fields inherit; hidden=false is an explicit restoration of a platform-visible item. */
    public record ItemChange(ConfigurationLabel label,Integer sort,Boolean hidden) {}
    public record PrivateItem(String code,String parentCode,ConfigurationLabel label,int sort,boolean enabled) {}
    /** The tenant's explicit layer values for editing; null fields inherit the platform publication. */
    public record LayerEntry(String kind,String code,String parentCode,ConfigurationLabel label,Integer sort,Boolean hidden,Boolean enabled) {}
    public record State(DictionarySnapshot snapshot,DictionaryDefinition definition,boolean enabled,ConfigurationLabel label,
            Long layerId,Set<String> overriddenCodes,Set<String> privateCodes,List<LayerEntry> layer) {
        public State { overriddenCodes=Set.copyOf(overriddenCodes);privateCodes=Set.copyOf(privateCodes);layer=List.copyOf(layer); }
    }
    private record Layer(DictionaryLayerEntity row,List<DictionaryEntryEntity> rows) {}
    /** Authoritative versions only; no mutable entity or customer text. */
    public record ReadFrame(long tenantId,Entry definition,SharedConfigurationService.PublishedPointer platform,long tenantRevision) {
        public ConfigurationRevision revision(){return new ConfigurationRevision(platform==null?0:platform.revision(),tenantRevision);}
        public String cacheKey(String language){
            return definition.key()+":"+definition.fingerprint()+":"+(platform==null?0:platform.revisionId())+":"+tenantRevision+":"+language;
        }
    }
    public ReadFrame capture(String key) {
        var entry=entry(key);long revision=revision();
        return new ReadFrame(CurrentUser.tenantId(),entry,shared.pointer(Kind.DICTIONARY,entry.key()).orElse(null),revision);
    }
    public DictionarySnapshot resolve(ReadFrame frame,String language) {
        if(CurrentUser.tenantId()!=frame.tenantId()||!entry(frame.definition().key()).equals(frame.definition()))throw new BizException(ConfigErrorCode.SHARED_UNAVAILABLE);
        long current=revision();
        // A newer tenant write is returned coherently; the reader's admission check keeps it out of the older cache address.
        if(current!=frame.tenantRevision())return compose(frame.definition(),load(frame.definition()),published(frame.definition()),current,language,false).snapshot();
        var publication=frame.platform()==null?Optional.<SharedConfigurationService.Published>empty():Optional.of(shared.publication(frame.platform()));
        return compose(frame.definition(),load(frame.definition()),publication,current,language,false).snapshot();
    }

    public State get(String key) {
        var entry=entry(key);long revision=revision();var layer=load(entry);
        return compose(entry,layer,published(entry),revision,language(),false);
    }

    @Transactional public State overrideItem(String key,ConfigurationRevision expected,String code,ItemChange change) {
        requireEdit();var entry=entry(key);mode(entry,DictionaryDefinition.Mode.EXTENDABLE);
        if(change==null||change.label()==null&&change.sort()==null&&change.hidden()==null)throw new BizException(ConfigErrorCode.SHARED_INVALID);
        locks.lock(CurrentUser.tenantId());long revision=revision();var layer=load(entry);var publication=published(entry);
        stale(expected,publication,revision);
        if(publication.orElseThrow().dictionary().items().stream().noneMatch(item->item.code().equals(code)))throw new BizException(CommonErrorCode.DATA_NOT_FOUND);
        var row=find(layer,code).orElse(null);
        if(row!=null&&!OVERRIDE.equals(row.getEntryKind()))throw new BizException(ConfigErrorCode.SHARED_UNAVAILABLE);
        long layerId=ensureLayer(entry,layer);
        if(row==null){row=new DictionaryEntryEntity();row.setLayerId(layerId);row.setEntryKind(OVERRIDE);row.setCode(code);}
        row.setLabelJson(change.label()==null?null:encode(change.label()));row.setSort(change.sort());row.setHidden(change.hidden());row.setEnabled(null);row.setParentCode(null);
        save(row);return changed(entry);
    }
    @Transactional public State restoreItem(String key,ConfigurationRevision expected,String code) {
        requireEdit();var entry=entry(key);mode(entry,DictionaryDefinition.Mode.EXTENDABLE);
        locks.lock(CurrentUser.tenantId());long revision=revision();var layer=load(entry);stale(expected,published(entry),revision);
        var row=find(layer,code).filter(value->OVERRIDE.equals(value.getEntryKind())).orElseThrow(()->new BizException(CommonErrorCode.DATA_NOT_FOUND));
        entries.deleteById(row.getId());return changed(entry);
    }
    @Transactional public State savePrivate(String key,ConfigurationRevision expected,PrivateItem item) {
        requireEdit();var entry=entry(key);var mode=entry.dictionary().mode();
        if(mode==DictionaryDefinition.Mode.PLATFORM_LOCKED||item==null)throw new BizException(ConfigErrorCode.SHARED_INVALID);
        try{DictionaryCodeIdentity.validate(item.code());if(item.parentCode()!=null)DictionaryCodeIdentity.validate(item.parentCode());rules.validateLabel(item.label());}
        catch(IllegalArgumentException|NullPointerException invalid){throw new BizException(ConfigErrorCode.SHARED_INVALID);}
        if(item.sort()<0)throw new BizException(ConfigErrorCode.SHARED_INVALID);
        locks.lock(CurrentUser.tenantId());
        // Exclusive head lock before the shared pointer read avoids an S-to-X upgrade deadlock between tenants.
        if(mode==DictionaryDefinition.Mode.EXTENDABLE)lockHead(entry);
        long revision=revision();var layer=load(entry);var publication=published(entry);stale(expected,publication,revision);
        var row=find(layer,item.code()).orElse(null);
        if(row!=null&&!PRIVATE.equals(row.getEntryKind()))throw new BizException(ConfigErrorCode.DICTIONARY_CODE_CONFLICT);
        if(row==null&&mode==DictionaryDefinition.Mode.EXTENDABLE)codes.reservePrivate(entry.key(),item.code());
        long layerId=ensureLayer(entry,layer);
        if(row==null){row=new DictionaryEntryEntity();row.setLayerId(layerId);row.setEntryKind(PRIVATE);row.setCode(item.code());}
        row.setParentCode(item.parentCode());row.setLabelJson(encode(item.label()));row.setSort(item.sort());row.setEnabled(item.enabled());row.setHidden(null);
        save(row);return changed(entry);
    }
    @Transactional public State enabled(String key,ConfigurationRevision expected,boolean enabled) {
        requireEdit();var entry=entry(key);
        if(entry.dictionary().mode()==DictionaryDefinition.Mode.PLATFORM_LOCKED)throw new BizException(ConfigErrorCode.SHARED_INVALID);
        locks.lock(CurrentUser.tenantId());long revision=revision();var layer=load(entry);stale(expected,published(entry),revision);
        long id=ensureLayer(entry,layer);layers.enabled(id,enabled,CurrentUser.userId());return changed(entry);
    }

    /** Host startup only; one tenant per transaction. Existing legacy rows are retained and bound by reference. */
    @Transactional public int migrateCurrentTenant() {
        requireHost();locks.lock(CurrentUser.tenantId());int count=0;
        for(var entry:catalog.entries()) if(entry.kind()==Kind.DICTIONARY) {
            var legacy=legacyTypes.selectOne(new LambdaQueryWrapper<DictionaryTypeEntity>().eq(DictionaryTypeEntity::getTypeKey,entry.key()));
            if(legacy==null)continue;
            var existing=layers.current(entry.key());
            if(existing!=null){check(entry,existing);continue;}
            try{migrate(entry,legacy);}
            catch(RuntimeException incompatible){
                // Never attach parser/driver causes: they can contain customer labels or codes.
                throw new IllegalStateException("Dictionary migration requires review: tenant="+CurrentUser.tenantId()+", key="+entry.key());
            }
            count++;
        }
        if(count>0)advance("*");
        return count;
    }
    private void migrate(Entry entry,DictionaryTypeEntity legacy) {
        var definition=entry.dictionary();
        if(definition.mode()==DictionaryDefinition.Mode.PLATFORM_LOCKED||!Objects.equals(legacy.getTree(),definition.tree()))throw new IllegalArgumentException();
        if(definition.mode()==DictionaryDefinition.Mode.EXTENDABLE)lockHead(entry);
        var publication=published(entry);
        var sharedItems=new LinkedHashMap<String,DictionaryDefinition.Item>();
        publication.ifPresent(value->value.dictionary().items().forEach(item->sharedItems.put(item.code(),item)));
        if(legacyItems.selectCount(new LambdaQueryWrapper<DictionaryItemEntity>().eq(DictionaryItemEntity::getTypeId,legacy.getId()))>SharedConfigurationRules.MAX_ITEMS)throw new IllegalArgumentException();
        var rows=legacyItems.selectList(new LambdaQueryWrapper<DictionaryItemEntity>().eq(DictionaryItemEntity::getTypeId,legacy.getId()).orderByAsc(DictionaryItemEntity::getId));
        var codesById=new HashMap<Long,String>();rows.forEach(row->codesById.put(row.getId(),row.getItemValue()));
        var layer=new DictionaryLayerEntity();layer.setDictionaryKey(entry.key());layer.setDefinitionContract(entry.contractHash());layer.setLegacyTypeId(legacy.getId());
        layer.setEnabled(!Boolean.FALSE.equals(legacy.getEnabled()));
        layer.setLabelJson(encode(new ConfigurationLabel(legacy.getName(),legacy.getNameKey(),names.translations("dictionary-type",legacy.getId()))));layers.insert(layer);
        var present=new HashSet<String>();
        for(var item:rows) {
            String parent=item.getParentId()==null||item.getParentId()==0?null:codesById.get(item.getParentId());
            if(item.getParentId()!=null&&item.getParentId()!=0&&parent==null)throw new IllegalArgumentException();
            var label=new ConfigurationLabel(item.getName(),item.getNameKey(),names.translations("dictionary-item",item.getId()));
            var row=new DictionaryEntryEntity();row.setLayerId(layer.getId());row.setCode(item.getItemValue());row.setLegacyItemId(item.getId());row.setLabelJson(encode(label));
            row.setSort(item.getSort());
            var sharedItem=sharedItems.get(item.getItemValue());
            if(sharedItem!=null) {
                if(!Boolean.TRUE.equals(item.getBuiltin()))throw new IllegalArgumentException();
                if(!Objects.equals(sharedItem.parentCode(),parent))throw new IllegalArgumentException();
                // Existing presentation remains explicit even when it equals the old default.
                row.setEntryKind(OVERRIDE);row.setHidden(!Boolean.TRUE.equals(item.getEnabled()));
            } else {
                if(definition.mode()==DictionaryDefinition.Mode.EXTENDABLE)codes.reservePrivate(entry.key(),item.getItemValue());
                row.setEntryKind(PRIVATE);row.setParentCode(parent);row.setEnabled(Boolean.TRUE.equals(item.getEnabled()));
            }
            present.add(item.getItemValue());entries.insert(row);
        }
        for(var missing:sharedItems.keySet()) if(!present.contains(missing)) {
            // A legacy tenant that never had this option must not suddenly receive it.
            var row=new DictionaryEntryEntity();row.setLayerId(layer.getId());row.setEntryKind(OVERRIDE);row.setCode(missing);row.setHidden(true);entries.insert(row);
        }
        compose(entry,new Layer(layer,entries.current(layer.getId())),publication,0,"zh-CN",true);
    }

    private State changed(Entry entry) {
        long revision=revision();var layer=load(entry);
        compose(entry,layer,published(entry),revision,"zh-CN",true);
        advance(entry.key());
        return compose(entry,load(entry),published(entry),revision(),language(),false);
    }
    private State compose(Entry entry,Layer layer,Optional<SharedConfigurationService.Published> publication,long tenantRevision,String language,boolean writing) {
        var definition=entry.dictionary();var overlays=new ArrayList<ItemOverride>();var privates=new ArrayList<DictionaryDefinition.Item>();
        var overridden=new HashSet<String>();var privateCodes=new HashSet<String>();var raw=new ArrayList<LayerEntry>();
        try {
            if(layer!=null)for(var row:layer.rows()) {
                var label=row.getLabelJson()==null?null:decode(row.getLabelJson());
                raw.add(new LayerEntry(row.getEntryKind(),row.getCode(),row.getParentCode(),label,row.getSort(),row.getHidden(),row.getEnabled()));
                if(OVERRIDE.equals(row.getEntryKind())){overlays.add(new ItemOverride(row.getCode(),label,row.getSort(),row.getHidden()));overridden.add(row.getCode());}
                else if(PRIVATE.equals(row.getEntryKind())){
                    privates.add(new DictionaryDefinition.Item(row.getCode(),row.getParentCode(),Objects.requireNonNull(label),Boolean.TRUE.equals(row.getEnabled()),Objects.requireNonNull(row.getSort())));
                    privateCodes.add(row.getCode());
                } else throw new IllegalArgumentException();
            }
            var snapshot=rules.dictionary(definition,publication.map(value->value.dictionary().items()),overlays,privates,
                    new ConfigurationRevision(publication.map(SharedConfigurationService.Published::revision).orElse(0L),tenantRevision),language,this::message);
            boolean enabled=layer==null||!Boolean.FALSE.equals(layer.row().getEnabled());
            if(!enabled)snapshot=new DictionarySnapshot(snapshot.key(),snapshot.mode(),snapshot.revision(),snapshot.entries().stream()
                    .map(value->new DictionarySnapshot.Entry(value.code(),value.parentCode(),value.label(),value.enabled(),value.hidden(),false,value.sort(),value.origin())).toList(),List.of());
            var label=layer==null||layer.row().getLabelJson()==null?publication.map(value->value.dictionary().label()).orElse(definition.label()):decode(layer.row().getLabelJson());
            return new State(snapshot,definition,enabled,label,layer==null?null:layer.row().getId(),overridden,privateCodes,raw);
        } catch(IllegalArgumentException|NullPointerException invalid) {
            throw new BizException(writing?ConfigErrorCode.SHARED_INVALID:ConfigErrorCode.SHARED_UNAVAILABLE);
        }
    }
    private Layer load(Entry entry) {
        var row=layers.current(entry.key());if(row==null)return null;check(entry,row);
        var values=entries.current(row.getId());
        if(values.size()>2*SharedConfigurationRules.MAX_ITEMS)throw new BizException(ConfigErrorCode.SHARED_UNAVAILABLE);
        return new Layer(row,values);
    }
    private Optional<DictionaryEntryEntity> find(Layer layer,String code){return layer==null?Optional.empty():layer.rows().stream().filter(row->row.getCode().equals(code)).findFirst();}
    private long ensureLayer(Entry entry,Layer layer) {
        if(layer!=null)return layer.row().getId();
        var row=new DictionaryLayerEntity();row.setDictionaryKey(entry.key());row.setDefinitionContract(entry.contractHash());row.setEnabled(true);layers.insert(row);return row.getId();
    }
    private void save(DictionaryEntryEntity row) {
        if(row.getId()==null)entries.insert(row);
        else if(entries.replace(row,CurrentUser.userId())!=1)throw new BizException(ConfigErrorCode.SHARED_STALE);
    }
    private void check(Entry entry,DictionaryLayerEntity row){if(!entry.contractHash().equals(row.getDefinitionContract()))throw new BizException(ConfigErrorCode.SHARED_UNAVAILABLE);}
    private Optional<SharedConfigurationService.Published> published(Entry entry){return shared.published(Kind.DICTIONARY,entry.key());}
    private void lockHead(Entry entry) {
        var head=heads.lockKey(Kind.DICTIONARY.name(),entry.key());
        if(head==null||head.getPublishedRevisionId()==null||!entry.contractHash().equals(head.getContractHash()))throw new BizException(ConfigErrorCode.SHARED_UNAVAILABLE);
    }
    private void stale(ConfigurationRevision expected,Optional<SharedConfigurationService.Published> publication,long revision) {
        var current=new ConfigurationRevision(publication.map(SharedConfigurationService.Published::revision).orElse(0L),revision);
        if(expected==null||!expected.equals(current))throw new BizException(ConfigErrorCode.SHARED_STALE);
    }
    private void mode(Entry entry,DictionaryDefinition.Mode mode){if(entry.dictionary().mode()!=mode)throw new BizException(ConfigErrorCode.SHARED_INVALID);}
    private Entry entry(String key){return catalog.find(Kind.DICTIONARY,key).orElseThrow(()->new BizException(CommonErrorCode.DATA_NOT_FOUND));}
    private long revision(){var value=locks.dictionaryRevision(CurrentUser.tenantId());return value==null?0:value;}
    private void advance(String key) {
        if(locks.advanceDictionaries(CurrentUser.tenantId(),CurrentUser.userId())!=1)throw new BizException(ConfigErrorCode.SHARED_CAPACITY);
        cache.invalidate(ConfigCacheCatalog.DICTIONARIES);events.publishEvent(new ConfigurationChangedEvent(CurrentUser.tenantId(),"dictionary",key));
    }
    private String encode(ConfigurationLabel label){rules.validateLabel(label);return json.writeValueAsString(label);}
    private ConfigurationLabel decode(String value) {
        try{var label=json.readValue(value,ConfigurationLabel.class);rules.validateLabel(label);return label;}
        catch(RuntimeException invalid){throw new IllegalArgumentException("Invalid stored dictionary label");}
    }
    private String message(String key,String language){return messages.getMessage(key,null,null,Locale.forLanguageTag(language));}
    static String language(){return LocaleContextHolder.getLocale().getLanguage().equals("en")?"en-US":"zh-CN";}
    private void requireEdit(){if(!CurrentUser.require().permissions().contains("config:dictionary:update"))throw new AccessDeniedException("Dictionary edit permission required");}
    private void requireHost() {
        if(SecurityContextHolder.getContext().getAuthentication()!=null||RequestContextHolder.getRequestAttributes()!=null
                ||TenantEntryScope.isHttpRequest()||TenantEntryScope.optional().isPresent())throw new AccessDeniedException("Dictionary migration is host startup work");
    }
}
