package com.sma.admin.config.internal.service;

import java.util.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import tools.jackson.databind.json.JsonMapper;
import com.sma.admin.common.*;
import com.sma.admin.common.cache.ManagedCache;
import com.sma.admin.common.security.*;
import com.sma.admin.config.*;
import com.sma.admin.config.internal.persistence.*;
import com.sma.admin.config.internal.service.ConfigurationCatalogRegistry.Entry;
import com.sma.admin.config.internal.service.ConfigurationCatalogRegistry.Kind;

/** Config's platform-database parameter layers. Every lock and row keeps the trusted tenant filter. */
@Service @Transactional(readOnly=true)
public class DefinedParameterService {
    private final ConfigurationCatalogRegistry catalog;
    private final SharedConfigurationService shared;
    private final ParameterMapper parameters;
    private final WriteLockMapper locks;
    private final ManagedCache cache;
    private final ApplicationEventPublisher events;
    private final SharedConfigurationRules rules;
    public DefinedParameterService(ConfigurationCatalogRegistry catalog, SharedConfigurationService shared,
            ParameterMapper parameters, WriteLockMapper locks, ManagedCache cache, ApplicationEventPublisher events, JsonMapper json) {
        this.catalog=catalog; this.shared=shared; this.parameters=parameters; this.locks=locks; this.cache=cache; this.events=events;
        this.rules=new SharedConfigurationRules(json);
    }
    /** Presentation metadata and old IDs stay on the original row; no copied shared defaults. */
    public record State(ParameterSnapshot snapshot, Long rowId, boolean overridden, ParameterDefinition definition) {}
    /** Only authoritative metadata, not a tenant value or a mutable entity. */
    public record ReadFrame(long tenantId,Entry definition,SharedConfigurationService.PublishedPointer platform,long tenantRevision) {
        public ConfigurationRevision revision() { return new ConfigurationRevision(platform==null?0:platform.revision(),tenantRevision); }
        public String cacheKey(String locale) {
            return definition.key()+":"+definition.fingerprint()+":"+(platform==null?0:platform.revisionId())+":"+tenantRevision+":"+locale;
        }
    }

    public ReadFrame capture(String key) {
        var entry=entry(key);long tenantRevision=revision();
        return new ReadFrame(CurrentUser.tenantId(),entry,shared.pointer(Kind.PARAMETER,entry.key()).orElse(null),tenantRevision);
    }
    public ParameterSnapshot resolve(ReadFrame frame) {
        if(CurrentUser.tenantId()!=frame.tenantId() || !entry(frame.definition().key()).equals(frame.definition()))
            throw new BizException(ConfigErrorCode.SHARED_UNAVAILABLE);
        long current=revision();
        // An intervening override is returned coherently, and the cache's admission predicate rejects the old address.
        if(current!=frame.tenantRevision()) return get(frame.definition().key()).snapshot();
        var row=parameters.current(frame.definition().key());checkBound(frame.definition(),row);
        var published=frame.platform()==null?Optional.<SharedConfigurationService.Published>empty():Optional.of(shared.publication(frame.platform()));
        return compose(frame.definition(),row,current,published).snapshot();
    }

    public State get(String key) {
        var entry=entry(key);
        long revision=revision();
        var row=parameters.current(entry.key());
        checkBound(entry,row);
        return state(entry,row,revision);
    }

    @Transactional public State override(String key, ConfigurationRevision expected, String value) {
        requireEdit();
        return change(key,expected,true,value);
    }
    @Transactional public State inherit(String key, ConfigurationRevision expected) {
        requireEdit();
        return change(key,expected,false,null);
    }
    private State change(String key, ConfigurationRevision expected, boolean overridden, String value) {
        var entry=entry(key);
        if (entry.parameter().overridePolicy()==ParameterDefinition.OverridePolicy.LOCKED) throw new BizException(ConfigErrorCode.SHARED_INVALID);
        locks.lock(CurrentUser.tenantId());
        long before=revision(); var row=parameters.current(entry.key()); checkBound(entry,row);
        var current=state(entry,row,before);
        if (expected==null || !expected.equals(current.snapshot().revision())) throw new BizException(ConfigErrorCode.SHARED_STALE);
        if (overridden) validate(entry,value);
        if (row==null && overridden) {
            row=new ParameterEntity(); row.setParamKey(entry.key()); row.setGroupKey(entry.parameter().group());
            row.setName(entry.parameter().label().text()); row.setNameKey(entry.parameter().label().messageKey());
            row.setValueType(entry.parameter().valueType().name()); row.setParamValue(value);
            row.setSecret(false); row.setPublicReadable(entry.parameter().publicProjection()==ParameterDefinition.PublicProjection.LOGIN_CAPTCHA);
            row.setBuiltin(true); row.setSort(0); row.setBrandArchived(false);
            row.setDefinitionContract(entry.contractHash()); row.setValueOverridden(true); row.setPresentationOverridden(false); parameters.insert(row);
        } else if (row!=null) {
            // An inherited row preserves its original value and presentation for audit/migration; the flag is authoritative.
            parameters.writeValue(row.getId(),overridden?value:row.getParamValue(),overridden,CurrentUser.userId());
            if (overridden) row.setParamValue(value);
            row.setValueOverridden(overridden);
        }
        advance(entry.key());
        return state(entry,row,revision());
    }

    /** Host startup only. Runs once per tenant transaction, and is safe to repeat after partial rollout. */
    @Transactional public int migrateCurrentTenant() {
        requireHost(); locks.lock(CurrentUser.tenantId()); int count=0;
        for (var entry:catalog.entries()) if (entry.kind()==Kind.PARAMETER) {
            var row=parameters.current(entry.key()); if (row==null) continue;
            if (row.getDefinitionContract()!=null) { checkBound(entry,row); continue; }
            try {
                if (Boolean.TRUE.equals(row.getSecret()) || !entry.parameter().valueType().name().equals(row.getValueType())
                        || entry.parameter().overridePolicy()==ParameterDefinition.OverridePolicy.LOCKED) throw new IllegalArgumentException();
                validate(entry,row.getParamValue());
            } catch (RuntimeException incompatible) {
                // Do not attach the cause: parser and driver messages may contain old customer values.
                throw new IllegalStateException("Parameter migration requires review: tenant="+CurrentUser.tenantId()+", key="+entry.key());
            }
            row.setDefinitionContract(entry.contractHash()); row.setValueOverridden(true); parameters.updateById(row); count++;
        }
        if (count>0) advance("*");
        return count;
    }
    private State state(Entry entry, ParameterEntity row, long tenantRevision) {
        var publication=shared.published(Kind.PARAMETER,entry.key());
        return compose(entry,row,tenantRevision,publication);
    }
    private State compose(Entry entry,ParameterEntity row,long tenantRevision,Optional<SharedConfigurationService.Published> publication) {
        Optional<SharedConfigurationRules.Value> platform=publication.map(value->new SharedConfigurationRules.Value(value.parameter().value()));
        boolean overridden=row!=null && Boolean.TRUE.equals(row.getValueOverridden());
        var tenant=overridden?Optional.of(new SharedConfigurationRules.Value(row.getParamValue())):Optional.<SharedConfigurationRules.Value>empty();
        try {
            var snapshot=rules.parameter(entry.parameter(),platform,tenant,new ConfigurationRevision(publication.map(SharedConfigurationService.Published::revision).orElse(0L),tenantRevision));
            return new State(snapshot,row==null?null:row.getId(),overridden,entry.parameter());
        } catch (IllegalArgumentException | NullPointerException invalid) { throw new BizException(ConfigErrorCode.SHARED_UNAVAILABLE); }
    }
    private void validate(Entry entry,String value) {
        try { rules.parameter(entry.parameter(),Optional.empty(),Optional.of(new SharedConfigurationRules.Value(value)),new ConfigurationRevision(0,0)); }
        catch (IllegalArgumentException | NullPointerException invalid) { throw new BizException(ConfigErrorCode.SHARED_INVALID); }
    }
    private void checkBound(Entry entry,ParameterEntity row) {
        if (row!=null && (!entry.contractHash().equals(row.getDefinitionContract()) || Boolean.TRUE.equals(row.getSecret())
                || !entry.parameter().valueType().name().equals(row.getValueType()) || row.getValueOverridden()==null))
            throw new BizException(ConfigErrorCode.SHARED_UNAVAILABLE);
    }
    private Entry entry(String key) { return catalog.find(Kind.PARAMETER,key).orElseThrow(()->new BizException(CommonErrorCode.DATA_NOT_FOUND)); }
    private long revision() { var value=locks.parameterRevision(CurrentUser.tenantId()); return value==null?0:value; }
    private void advance(String key) {
        if (locks.advanceParameters(CurrentUser.tenantId(),CurrentUser.userId())!=1) throw new BizException(ConfigErrorCode.SHARED_CAPACITY);
        cache.invalidate(ConfigCacheCatalog.PARAMETERS);
        events.publishEvent(new ConfigurationChangedEvent(CurrentUser.tenantId(),"parameter",key));
    }
    private void requireEdit() {
        var actor=CurrentUser.require();
        if (!actor.permissions().contains("config:parameter:update")) throw new AccessDeniedException("Parameter edit permission required");
    }
    private void requireHost() {
        if (SecurityContextHolder.getContext().getAuthentication()!=null || RequestContextHolder.getRequestAttributes()!=null
                || TenantEntryScope.isHttpRequest() || TenantEntryScope.optional().isPresent())
            throw new AccessDeniedException("Parameter migration is host startup work");
    }
}
