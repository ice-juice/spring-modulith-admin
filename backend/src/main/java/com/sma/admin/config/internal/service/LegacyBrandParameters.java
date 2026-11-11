package com.sma.admin.config.internal.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.security.access.AccessDeniedException;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.sma.admin.common.security.*;
import com.sma.admin.config.LegacyBrandSource;
import com.sma.admin.config.internal.persistence.*;
import tools.jackson.databind.json.JsonMapper;

/** Only the host upgrade job may read/archive these rows, inside its platform transaction. */
@Service @Transactional(propagation=Propagation.MANDATORY)
public class LegacyBrandParameters implements LegacyBrandSource {
    public static final Set<String> KEYS=Arrays.stream(Key.values()).map(Key::parameter).collect(java.util.stream.Collectors.toUnmodifiableSet());
    private final ParameterMapper parameters;
    private final WriteLockMapper locks;
    private final ParameterService service;
    private final JsonMapper json;
    public LegacyBrandParameters(ParameterMapper parameters,WriteLockMapper locks,ParameterService service,JsonMapper json) {
        this.parameters=parameters;this.locks=locks;this.service=service;this.json=json;
    }
    @Override public Snapshot snapshot() {
        requireUpgrade();locks.lock(CurrentUser.tenantId());
        var rows=parameters.legacyBrandRows(KEYS);
        Map<Key,Value> values=new EnumMap<>(Key.class);var fingerprints=new ArrayList<Object>();
        for(var row:rows) {
            Key key=Arrays.stream(Key.values()).filter(k->k.parameter().equals(row.getParamKey())).findFirst().orElseThrow();
            boolean secret=Boolean.TRUE.equals(row.getSecret()), publicly=Boolean.TRUE.equals(row.getPublicReadable());
            values.put(key,new Value(row.getParamValue(),publicly,secret));
            // Never include secret text in the returned fingerprint or in a loggable projection.
            fingerprints.add(Arrays.asList(row.getId(),key.name(),row.getValueType(),publicly,secret,
                    secret?null:row.getParamValue(),row.getUpdatedAt()));
        }
        return new Snapshot(digest(json.writeValueAsString(fingerprints)),values);
    }
    @Override public void archive(String expectedSourceRevision) {
        var current=snapshot();
        if(!Objects.equals(expectedSourceRevision,current.sourceRevision())) throw new IllegalStateException("Legacy brand source changed during migration");
        if(current.values().isEmpty()) return;
        parameters.update(null,new LambdaUpdateWrapper<ParameterEntity>().in(ParameterEntity::getParamKey,KEYS)
                .eq(ParameterEntity::getBrandArchived,false).set(ParameterEntity::getBrandArchived,true));
        service.refresh();
    }
    private void requireUpgrade() {
        if(TenantEntryScope.isHttpRequest() || TenantEntryScope.optional().isPresent()
                || org.springframework.web.context.request.RequestContextHolder.getRequestAttributes()!=null
                || org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication()!=null)
            throw new AccessDeniedException("Legacy brand archival is a host upgrade operation");
    }
    private String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch(java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
