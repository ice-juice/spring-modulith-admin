package com.sma.admin.mail.internal.service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.sma.admin.common.*;
import com.sma.admin.common.security.*;
import com.sma.admin.mail.*;
import com.sma.admin.mail.internal.persistence.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Service @EnableConfigurationProperties(MailWorkProperties.class)
@Transactional(readOnly = true)
public class MailOutboxService implements MailSendApi, MailStatusQuery {
    private final MailDeliveryMapper deliveries;
    private final MailTemplateMapper templates;
    private final MailTemplateVersionMapper templateVersions;
    private final MailProfileMapper profiles;
    private final MailProfileRevisionMapper profileVersions;
    private final TenantSecretCodec secrets;
    private final MailTemplateRenderer renderer;
    private final List<MailSendPolicy> policies;
    private final JsonMapper json;
    private final MailWorkClock clock;
    private final MailWorkProperties settings;
    private final MailSuppressionService suppression;
    private final MailDeliveryGuards guards;
    public MailOutboxService(MailDeliveryMapper deliveries, MailTemplateMapper templates, MailTemplateVersionMapper templateVersions,
            MailProfileMapper profiles, MailProfileRevisionMapper profileVersions, TenantSecretCodec secrets,
            MailTemplateRenderer renderer, List<MailSendPolicy> policies, JsonMapper json, MailWorkClock clock, MailWorkProperties settings, MailSuppressionService suppression,MailDeliveryGuards guards) {
        this.deliveries=deliveries; this.templates=templates; this.templateVersions=templateVersions;
        this.profiles=profiles; this.profileVersions=profileVersions; this.secrets=secrets; this.renderer=renderer;
        this.policies=List.copyOf(policies); this.json=json; this.clock=clock; this.settings=settings;
        this.suppression=suppression;
        this.guards=guards;
    }

    @Override @Transactional public MailDeliveryView enqueue(Command command) {
        validate(command); var actor=ActorContext.require();
        if (policies.stream().filter(p->p.application().equals(command.application())).noneMatch(p->p.canSend(actor,command)))
            throw new AccessDeniedException("No mail scene grant");
        // Lock the stable template to serialize same-key requests, even while its published account changes.
        var template=templates.selectOne(new LambdaQueryWrapper<MailTemplateEntity>().eq(MailTemplateEntity::getCode,command.templateCode())
                .eq(MailTemplateEntity::getLanguage,command.language()).last("FOR UPDATE"));
        if(template==null || !template.getApplication().equals(command.application()) || !template.getScene().equals(command.scene()))
            throw new BizException(MailErrorCode.TEMPLATE_NOT_FOUND);
        String canonical=canonical(command);
        var previous=deliveries.selectOne(new LambdaQueryWrapper<MailDeliveryEntity>().eq(MailDeliveryEntity::getApplication,command.application())
                .eq(MailDeliveryEntity::getScene,command.scene()).eq(MailDeliveryEntity::getIdempotencyKey,command.idempotencyKey()));
        if(previous!=null) {
            owner(previous,false);
            char[] request=canonical.toCharArray();
            try {if(!secrets.matchesFingerprint(reference(previous),"delivery-request",request,previous.getRequestFingerprint()))
                throw new BizException(MailErrorCode.IDEMPOTENCY_CONFLICT);}
            finally {Arrays.fill(request,'\0');}
            return view(previous);
        }
        if(!template.getEnabled() || template.getPublishedVersion()==0) throw new BizException(MailErrorCode.TEMPLATE_NOT_FOUND);
        var version=templateVersions.selectOne(new LambdaQueryWrapper<MailTemplateVersionEntity>().eq(MailTemplateVersionEntity::getTemplateId,template.getId())
                .eq(MailTemplateVersionEntity::getVersion,template.getPublishedVersion()));
        if(version==null) throw new BizException(MailErrorCode.TEMPLATE_NOT_FOUND);
        var profile=profiles.selectOne(new LambdaQueryWrapper<MailProfileEntity>().eq(MailProfileEntity::getId,version.getProfileId()).last("FOR UPDATE"));
        if(profile==null || !profile.getEnabled() || !profile.getCredentialVersion().equals(version.getCredentialVersion()))
            throw new BizException(MailErrorCode.PROFILE_NOT_FOUND);
        var current=profileVersions.selectOne(new LambdaQueryWrapper<MailProfileRevisionEntity>().eq(MailProfileRevisionEntity::getProfileId,profile.getId())
                .eq(MailProfileRevisionEntity::getConfigurationVersion,profile.getConfigurationVersion()));
        if(current==null || !json.readValue(current.getApplicationsJson(),new TypeReference<List<String>>(){}).contains(command.application()))
            throw new AccessDeniedException("Mail application removed");
        String recipientKey=suppression.recipientKey(profile,command.application(),command.recipient());
        if(suppression.blocked(profile.getId(),command.application(),recipientKey))throw new BizException(MailErrorCode.RECIPIENT_SUPPRESSED);
        if(profile.getPendingDeliveries()>=settings.maximumQueuedPerProfile())
            throw new BizException(MailErrorCode.QUOTA_EXCEEDED);
        profile.setPendingDeliveries(Math.addExact(profile.getPendingDeliveries(),1));profiles.updateById(profile);
        var rendered=renderer.render(json.readValue(version.getContentJson(),MailTemplateContent.class),command.variables());
        var row=new MailDeliveryEntity(); row.setId(IdWorker.getId()); row.setTenantId(actor.tenantId());
        if(command.guard()!=null){row.setGuardOwner(command.guard().owner());row.setGuardReference(command.guard().reference());}
        row.setApplication(command.application()); row.setScene(command.scene()); row.setIdempotencyKey(command.idempotencyKey());
        row.setTemplateId(template.getId()); row.setTemplateVersion(version.getVersion()); row.setProfileId(version.getProfileId());
        row.setProfileVersion(version.getProfileVersion()); row.setCredentialVersion(version.getCredentialVersion());
        row.setActorKind(actor.kind().name()); row.setActorSubject(actor.subject()); row.setActorRealm(actor.realmId()); row.setActorClient(actor.clientId());
        row.setRecipientMasked(mask(command.recipient())); row.setState("QUEUED"); row.setAttempts(0); row.setFence(0L);
        row.setRecipientKey(recipientKey);
        row.setNextAttemptAt(clock.now()); row.setExpiresAt(command.expiresAt()); row.setMessageId("sma-"+UUID.randomUUID()+"@mail.invalid");
        char[] request=canonical.toCharArray();
        try {row.setRequestFingerprint(secrets.fingerprint(reference(row),"delivery-request",request));}
        finally {Arrays.fill(request,'\0');}
        String serialized=json.writeValueAsString(new MailPayload(command.recipient(),rendered.subject(),rendered.body(),rendered.html()));
        if(serialized.getBytes(StandardCharsets.UTF_8).length>60000) throw new BizException(MailErrorCode.INVALID_TEMPLATE);
        char[] plaintext=serialized.toCharArray();
        try { row.setPayloadEnvelope(secrets.encrypt(reference(row),"delivery-payload",plaintext)); }
        finally { Arrays.fill(plaintext,'\0'); }
        // Different templates sharing the same scene/key may race: the DB unique key remains the final gate.
        try { deliveries.insert(row); }
        catch(org.springframework.dao.DuplicateKeyException conflict) { throw new BizException(MailErrorCode.IDEMPOTENCY_CONFLICT); }
        return view(row);
    }

    @Override public MailDeliveryView get(long id) { var row=owned(id,false); owner(row,false); return view(row); }
    @Override @Transactional public MailDeliveryView cancel(long id) {
        var row=owned(id,true); owner(row,true);
        if(row.getState().equals("CANCELLED")) return view(row);
        if(!Set.of("QUEUED","CLAIMED").contains(row.getState())) throw new BizException(MailErrorCode.INVALID_STATE);
        row.setState("CANCELLED"); row.setFence(row.getFence()+1); row.setLeaseOwner(null); row.setLeaseUntil(null);
        row.setPayloadEnvelope(null); row.setLastResult("USER_CANCELLED"); releaseQueue(row); deliveries.updateById(row); return view(row);
    }
    public MailPayload payload(MailDeliveryEntity row) {
        try(var secret=secrets.decrypt(reference(row),"delivery-payload",row.getPayloadEnvelope())) {
            char[] chars=secret.copy(); try { return json.readValue(new String(chars),MailPayload.class); } finally { Arrays.fill(chars,'\0'); }
        }
    }
    static TenantConfigurationRef reference(MailDeliveryEntity row) { return new TenantConfigurationRef(row.getTenantId(),"mail",row.getId(),1,1); }
    void releaseQueue(MailDeliveryEntity row) {
        var profile=profiles.selectOne(new LambdaQueryWrapper<MailProfileEntity>().eq(MailProfileEntity::getId,row.getProfileId()).last("FOR UPDATE"));
        if(profile==null || profile.getPendingDeliveries()<1)throw new IllegalStateException("Missing mail queue reservation");
        profile.setPendingDeliveries(profile.getPendingDeliveries()-1);profiles.updateById(profile);
    }
    private MailDeliveryEntity owned(long id,boolean lock) {
        ActorContext.tenantId(); var query=new LambdaQueryWrapper<MailDeliveryEntity>().eq(MailDeliveryEntity::getId,id);
        if(lock) query.last("FOR UPDATE"); var row=deliveries.selectOne(query);
        if(row==null) throw new BizException(MailErrorCode.DELIVERY_NOT_FOUND); return row;
    }
    private void owner(MailDeliveryEntity row,boolean changing) {
        var actor=ActorContext.require();
        if(actor.kind()==ActorRef.Kind.ADMIN_USER && CurrentUser.require().permissions().contains(changing?"mail:delivery:manage":"mail:delivery:view")) return;
        if(!actor.kind().name().equals(row.getActorKind()) || !actor.subject().equals(row.getActorSubject())
                || !Objects.equals(actor.realmId(),row.getActorRealm()) || !Objects.equals(actor.clientId(),row.getActorClient()))
            throw new AccessDeniedException("Mail delivery not owned");
    }
    static MailDeliveryView view(MailDeliveryEntity row) {
        return new MailDeliveryView(row.getId(),row.getApplication(),row.getScene(),row.getRecipientMasked(),row.getState(),row.getAttempts(),row.getExpiresAt(),row.getLastResult(),row.getReceiptState());
    }
    private String canonical(Command c) {
        var fields=new ArrayList<Object>(List.of(c.application(),c.scene(),c.templateCode(),c.language(),c.recipient(),new TreeMap<>(c.variables()),c.expiresAt().toString()));
        // Preserve the original fingerprint for existing callers and persisted unguarded tasks.
        if(c.guard()!=null)fields.add(List.of(c.guard().owner(),c.guard().reference()));return json.writeValueAsString(fields);
    }
    private String mask(String recipient) { int at=recipient.indexOf('@'); return recipient.substring(0,1)+"***"+recipient.substring(at); }
    private void validate(Command c) {
        if(c==null || !code(c.application()) || !code(c.scene()) || !code(c.templateCode()) || c.language()==null
                || !Set.of("zh-CN","en-US").contains(c.language()) || c.recipient()==null || c.recipient().length()>254
                || !c.recipient().matches("[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])?\\.[A-Za-z]{2,63}")
                || c.expiresAt()==null || !c.expiresAt().isAfter(clock.now()) || c.expiresAt().isAfter(clock.now().plus(Duration.ofDays(7)))
                || c.idempotencyKey()==null || !c.idempotencyKey().matches("[A-Za-z0-9:._-]{1,128}") || c.variables().size()>32 || !guards.valid(c.guard())
                || c.variables().values().stream().mapToInt(String::length).sum()>8000) throw new BizException(MailErrorCode.INVALID_CONFIGURATION);
    }
    private boolean code(String value) { return value!=null && value.matches("[a-z][a-z0-9.-]{0,63}"); }
}
