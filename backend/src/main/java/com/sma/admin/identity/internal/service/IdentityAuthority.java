package com.sma.admin.identity.internal.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sma.admin.common.BizException;
import com.sma.admin.common.security.*;
import com.sma.admin.identity.IdentityErrorCode;
import com.sma.admin.identity.internal.persistence.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

/** Authoritative state, never a cache. Lock order is realm -> client -> subject -> session -> refresh. */
@Service
public class IdentityAuthority {
    public record Snapshot(long tenantId,long realmId,long clientId,String application,String platform,long configurationVersion,long credentialVersion,
                           long tenantGeneration,long realmGeneration,long clientGeneration) {}
    record Loaded(Snapshot snapshot,IdentityRealmEntity realm,IdentityClientEntity client,IdentityClientRevisionEntity configuration) {}
    private final IdentityRealmMapper realms;private final IdentityClientMapper clients;
    private final IdentityClientRevisionMapper revisions;private final TenantEntryQuery tenants;
    public IdentityAuthority(IdentityRealmMapper realms,IdentityClientMapper clients,IdentityClientRevisionMapper revisions,TenantEntryQuery tenants){
        this.realms=realms;this.clients=clients;this.revisions=revisions;this.tenants=tenants;
    }
    @Transactional public Snapshot capture(String loginHandle){
        worker();if(loginHandle==null || !loginHandle.matches("[A-Za-z0-9_-]{43}"))throw invalid();
        var client=clients.selectOne(new LambdaQueryWrapper<IdentityClientEntity>().eq(IdentityClientEntity::getLoginHandle,loginHandle));
        if(client==null)throw invalid();return current(client.getId(),true).snapshot();
    }
    @Transactional
    Loaded entry(String loginHandle){
        worker();if(loginHandle==null || !loginHandle.matches("[A-Za-z0-9_-]{43}"))throw invalid();
        var client=clients.selectOne(new LambdaQueryWrapper<IdentityClientEntity>().eq(IdentityClientEntity::getLoginHandle,loginHandle));
        if(client==null)throw invalid();return current(client.getId(),false);
    }
    // This method only reads/locks authority. Its owner may need to persist a denial/terminal state after a business rejection.
    @Transactional(propagation=Propagation.MANDATORY,noRollbackFor=BizException.class)
    Loaded current(long clientId,boolean exclusive){
        worker();String lock=exclusive?"FOR UPDATE":"FOR SHARE";
        var first=clients.selectById(clientId);if(first==null)throw invalid();
        var realm=realms.selectOne(new LambdaQueryWrapper<IdentityRealmEntity>().eq(IdentityRealmEntity::getId,first.getRealmId()).last(lock));
        var client=clients.selectOne(new LambdaQueryWrapper<IdentityClientEntity>().eq(IdentityClientEntity::getId,clientId).last(lock));
        if(realm==null || client==null || !realm.getEnabled() || !client.getEnabled() || !realm.getId().equals(client.getRealmId()))throw invalid();
        var tenant=tenants.securityState(ActorContext.tenantId()).filter(s->s.acceptsBusiness() && s.acceptsLogin()).orElseThrow(this::invalid);
        var revision=revisions.selectOne(new LambdaQueryWrapper<IdentityClientRevisionEntity>().eq(IdentityClientRevisionEntity::getClientId,clientId)
                .eq(IdentityClientRevisionEntity::getConfigurationVersion,client.getConfigurationVersion()).last("FOR SHARE"));
        if(revision==null || !revision.getCredentialVersion().equals(client.getCredentialVersion()))throw invalid();
        var snapshot=new Snapshot(client.getTenantId(),realm.getId(),clientId,client.getApplication(),client.getPlatform(),client.getConfigurationVersion(),
                client.getCredentialVersion(),tenant.generation(),realm.getSecurityVersion(),client.getSecurityVersion());
        return new Loaded(snapshot,realm,client,revision);
    }
    @Transactional(propagation=Propagation.MANDATORY,noRollbackFor=BizException.class)
    Loaded match(Snapshot expected){
        worker();ActorContext.requireTenant(expected.tenantId());var loaded=current(expected.clientId(),true);
        if(!loaded.snapshot().equals(expected))throw invalid();return loaded;
    }
    static void worker(){if(ActorContext.require().kind()!=ActorRef.Kind.SYSTEM_JOB)throw new AccessDeniedException("Trusted identity coordinator required");}
    private BizException invalid(){return new BizException(IdentityErrorCode.INVALID_SESSION);}
}
