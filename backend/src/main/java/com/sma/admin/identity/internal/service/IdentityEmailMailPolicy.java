package com.sma.admin.identity.internal.service;

import java.util.*;
import com.sma.admin.common.security.*;
import com.sma.admin.identity.internal.persistence.*;
import com.sma.admin.mail.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.json.JsonMapper;

/** Sender admission and dispatch validity. Does not inject the outbox, avoiding a circular service graph. */
@Service
public class IdentityEmailMailPolicy implements MailSendPolicy,MailDeliveryGate {
    public static final String OWNER="identity.email";
    private final IdentityEmailChallengeMapper challenges;private final IdentityEmailConfigurationMapper configurations;
    private final IdentityRealmMapper realms;private final IdentityClientMapper clients;private final IdentitySubjectMapper subjects;
    private final TenantEntryQuery tenants;private final TenantSecretCodec secrets;private final IdentityClock clock;private final IdentityEmailProperties settings;private final JsonMapper json;
    public IdentityEmailMailPolicy(IdentityEmailChallengeMapper challenges,IdentityEmailConfigurationMapper configurations,IdentityRealmMapper realms,
            IdentityClientMapper clients,IdentitySubjectMapper subjects,TenantEntryQuery tenants,TenantSecretCodec secrets,IdentityClock clock,IdentityEmailProperties settings,JsonMapper json){
        this.challenges=challenges;this.configurations=configurations;this.realms=realms;this.clients=clients;this.subjects=subjects;this.tenants=tenants;
        this.secrets=secrets;this.clock=clock;this.settings=settings;this.json=json;
    }
    @Override public String application(){return "identity";}
    @Override public String owner(){return OWNER;}
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public boolean canSend(ActorRef actor,MailSendApi.Command command){
        ActorContext.requireTenant(actor.tenantId());
        if(actor.kind()!=ActorRef.Kind.SYSTEM_JOB || command.guard()==null || !OWNER.equals(command.guard().owner()))return false;
        Long id=id(command.guard().reference());if(id==null || !actor.subject().equals("identity-email:"+id) || !command.idempotencyKey().equals("identity-email:"+id))return false;
        var row=challenges.selectById(id);
        if(row==null || !row.getStatus().equals("PREPARED") || row.getDeliveryId()!=null || !row.getExpiresAt().isAfter(clock.now())
                || !row.getExpiresAt().equals(command.expiresAt()) || !row.getLanguage().equals(command.language()) || !command.application().equals(application())
                || !IdentityEmailChallengeStore.scene(IdentityEmailChallengeStore.Purpose.valueOf(row.getPurpose())).equals(command.scene())
                || !command.variables().keySet().equals(Set.of("code","minutes")) || !Integer.toString(minutes()).equals(command.variables().get("minutes")))return false;
        var configuration=current(row);if(configuration==null)return false;
        String template=switch(IdentityEmailChallengeStore.Purpose.valueOf(row.getPurpose())){
            case REGISTRATION->configuration.getRegistrationTemplate();case PASSWORD_RESET->configuration.getResetTemplate();case EMAIL_BINDING->configuration.getBindingTemplate();};
        if(!template.equals(command.templateCode()))return false;
        String candidate=command.variables().get("code");if(candidate==null || !candidate.matches("[0-9]{6}"))return false;
        char[] code=candidate.toCharArray();
        try{if(!secrets.matchesFingerprint(IdentityEmailChallengeStore.reference(row),"email-challenge-code",code,row.getCodeFingerprint()))return false;}
        finally{Arrays.fill(code,'\0');}
        try(var target=secrets.decrypt(IdentityEmailChallengeStore.reference(row),"email-challenge-target",row.getTargetEnvelope())){
            char[] chars=target.copy();try{return command.recipient().equals(new String(chars));}finally{Arrays.fill(chars,'\0');}
        }
    }
    /** Join mail's fresh short work transaction; non-locking reads, no reverse locks or second pooled connection. */
    @Override @Transactional(propagation=Propagation.MANDATORY,readOnly=true)
    public boolean mayDeliver(Delivery delivery){
        IdentityAuthority.worker();ActorContext.requireTenant(delivery.tenantId());Long id=id(delivery.reference());if(id==null)return false;
        var row=challenges.selectById(id);
        return row!=null && row.getStatus().equals("PENDING") && row.getDeliveryId()!=null && row.getDeliveryId()==delivery.deliveryId()
                && row.getCodeFingerprint()!=null && row.getTargetEnvelope()!=null && row.getExpiresAt().isAfter(clock.now()) && row.getExpiresAt().equals(delivery.expiresAt())
                && application().equals(delivery.application()) && IdentityEmailChallengeStore.scene(IdentityEmailChallengeStore.Purpose.valueOf(row.getPurpose())).equals(delivery.scene())
                && current(row)!=null;
    }
    private IdentityEmailConfigurationEntity current(IdentityEmailChallengeEntity row){
        var snapshot=json.readValue(row.getSnapshotJson(),IdentityAuthority.Snapshot.class);ActorContext.requireTenant(snapshot.tenantId());
        var tenant=tenants.securityState(snapshot.tenantId()).orElse(null);
        if(tenant==null || !tenant.acceptsBusiness() || !tenant.acceptsLogin() || tenant.generation()!=snapshot.tenantGeneration())return null;
        var realm=realms.selectById(snapshot.realmId());var client=clients.selectById(snapshot.clientId());
        if(realm==null || !realm.getEnabled() || !realm.getPasswordEnabled() || realm.getSecurityVersion()!=snapshot.realmGeneration()
                || row.getPurpose().equals("REGISTRATION") && !realm.getRegistrationEnabled() || client==null || !client.getEnabled()
                || client.getRealmId()!=snapshot.realmId() || client.getSecurityVersion()!=snapshot.clientGeneration() || client.getConfigurationVersion()!=snapshot.configurationVersion()
                || client.getCredentialVersion()!=snapshot.credentialVersion() || !client.getApplication().equals(snapshot.application()) || !client.getPlatform().equals(snapshot.platform()))return null;
        var configuration=configurations.selectById(row.getEmailConfigurationId());
        if(configuration==null || !configuration.getEnabled() || !configuration.getVersion().equals(row.getEmailConfigurationVersion())
                || configuration.getClientId()!=snapshot.clientId() || !configuration.getLanguage().equals(row.getLanguage()))return null;
        if(row.getSubjectId()!=null){var subject=subjects.selectById(row.getSubjectId());
            if(subject==null || !subject.getEnabled() || subject.getRealmId()!=snapshot.realmId() || !subject.getSecurityVersion().equals(row.getSubjectGeneration()))return null;}
        return configuration;
    }
    int minutes(){return Math.toIntExact((settings.lifetime().toMillis()+59999)/60000);}
    private Long id(String value){try{return value!=null && value.matches("[1-9][0-9]{0,18}")?Long.valueOf(value):null;}catch(NumberFormatException invalid){return null;}}
}
