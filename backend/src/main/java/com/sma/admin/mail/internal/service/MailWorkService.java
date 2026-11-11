package com.sma.admin.mail.internal.service;

import java.time.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sma.admin.common.security.*;
import com.sma.admin.mail.internal.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Short database transactions; never hold a connection/row lock while contacting SMTP. */
@Service
@Transactional(propagation = Propagation.REQUIRES_NEW)
public class MailWorkService {
    public enum Outcome { ACCEPTED, RETRYABLE, FAILED, UNKNOWN, CANCELLED }
    public record Claim(long tenantId,long deliveryId,long fence,String worker) {}
    public record Prepared(Claim claim,String messageId,MailPayload payload,MailProfileRevisionEntity profile) {
        @Override public String toString() { return "PreparedMail[REDACTED]"; }
    }
    private final MailDeliveryMapper deliveries;
    private final MailAttemptMapper attempts;
    private final MailProfileMapper profiles;
    private final MailProfileRevisionMapper profileVersions;
    private final MailTemplateMapper templates;
    private final MailOutboxService outbox;
    private final MailWorkClock clock;
    private final MailWorkProperties settings;
    private final JsonMapper json;
    private final TenantEntryQuery tenants;
    private final MailSuppressionService suppression;
    private final MailDeliveryGuards guards;
    public MailWorkService(MailDeliveryMapper deliveries,MailAttemptMapper attempts,MailProfileMapper profiles,
            MailProfileRevisionMapper profileVersions,MailTemplateMapper templates,MailOutboxService outbox,
            MailWorkClock clock,MailWorkProperties settings,JsonMapper json,TenantEntryQuery tenants,MailSuppressionService suppression,MailDeliveryGuards guards) {
        this.deliveries=deliveries;this.attempts=attempts;this.profiles=profiles;this.profileVersions=profileVersions;
        this.templates=templates;this.outbox=outbox;this.clock=clock;this.settings=settings;this.json=json;this.tenants=tenants;
        this.suppression=suppression;
        this.guards=guards;
    }
    public Optional<Claim> claim(String worker) {
        requireWorker(); if(worker==null || !worker.matches("[A-Za-z0-9_-]{1,64}")) throw new IllegalArgumentException("Invalid worker ID");
        var rows=deliveries.selectList(new LambdaQueryWrapper<MailDeliveryEntity>().eq(MailDeliveryEntity::getState,"QUEUED")
                .le(MailDeliveryEntity::getNextAttemptAt,clock.now()).orderByAsc(MailDeliveryEntity::getId).last("LIMIT 1 FOR UPDATE SKIP LOCKED"));
        if(rows.isEmpty()) return Optional.empty(); var row=rows.getFirst();
        if(!row.getExpiresAt().isAfter(clock.now())) {terminal(row,"CANCELLED","EXPIRED");return Optional.empty();}
        if(row.getAttempts()>=settings.maximumAttempts()) {terminal(row,"FAILED","ATTEMPTS_EXHAUSTED");return Optional.empty();}
        row.setState("CLAIMED"); row.setFence(row.getFence()+1); row.setLeaseOwner(worker); row.setLeaseUntil(clock.now().plus(settings.leaseDuration()));
        deliveries.updateById(row); return Optional.of(new Claim(row.getTenantId(),row.getId(),row.getFence(),worker));
    }

    /** Last reversible gate, committed before any SMTP send is attempted. */
    public Optional<Prepared> begin(Claim claim) {
        requireWorker(); var row=locked(claim);
        if(row==null || !row.getState().equals("CLAIMED") || !row.getLeaseUntil().isAfter(clock.now())) return Optional.empty();
        if(tenants.securityState(row.getTenantId()).filter(TenantEntryQuery.SecurityState::acceptsBusiness).isEmpty()){
            terminal(row,"CANCELLED","TENANT_DISABLED");return Optional.empty();
        }
        if(!row.getExpiresAt().isAfter(clock.now())) {terminal(row,"CANCELLED","EXPIRED");return Optional.empty();}
        if(row.getReceiptState()!=null){terminal(row,"ACCEPTED","RECEIPT_CONFIRMED");return Optional.empty();}
        if(!guards.mayDeliver(row)){terminal(row,"CANCELLED","BUSINESS_INVALIDATED");return Optional.empty();}
        var template=templates.selectById(row.getTemplateId());
        if(template==null || !template.getEnabled()) {terminal(row,"CANCELLED","TEMPLATE_DISABLED");return Optional.empty();}
        var profile=profiles.selectOne(new LambdaQueryWrapper<MailProfileEntity>().eq(MailProfileEntity::getId,row.getProfileId()).last("FOR UPDATE"));
        if(profile==null || !profile.getEnabled() || !profile.getCredentialVersion().equals(row.getCredentialVersion())) {
            terminal(row,"CANCELLED","PROFILE_REVOKED");return Optional.empty();
        }
        var current=revision(profile.getId(),profile.getConfigurationVersion());
        var fixed=revision(row.getProfileId(),row.getProfileVersion());
        if(current==null || fixed==null || !json.readValue(current.getApplicationsJson(),new TypeReference<List<String>>(){}).contains(row.getApplication())) {
            terminal(row,"CANCELLED","APPLICATION_REVOKED");return Optional.empty();
        }
        var today=clock.now().atOffset(ZoneOffset.UTC).toLocalDate();long minute=clock.now().getEpochSecond()/60;
        int dayCount=today.equals(profile.getQuotaDay())?profile.getDayAttempts():0;
        int minuteCount=Objects.equals(profile.getQuotaMinute(),minute)?profile.getMinuteAttempts():0;
        if(dayCount>=current.getDailyQuota() || minuteCount>=current.getPerMinuteLimit()) {
            Instant due=dayCount>=current.getDailyQuota()?today.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC):Instant.ofEpochSecond((minute+1)*60);
            row.setState("QUEUED"); row.setNextAttemptAt(due); row.setLeaseOwner(null);row.setLeaseUntil(null);
            row.setLastResult("RATE_LIMITED");deliveries.updateById(row);return Optional.empty();
        }
        MailPayload payload;
        try { payload=outbox.payload(row); }
        catch(RuntimeException unavailable) {
            // No send happened. Back off safely and eventually expire rather than burn an SMTP attempt.
            row.setState("QUEUED"); row.setNextAttemptAt(clock.now().plusSeconds(60));row.setLeaseOwner(null);row.setLeaseUntil(null);
            row.setLastResult("PAYLOAD_UNAVAILABLE");deliveries.updateById(row);return Optional.empty();
        }
        if(row.getRecipientKey()==null)row.setRecipientKey(suppression.recipientKey(profile,row.getApplication(),payload.recipient()));
        if(suppression.blocked(profile.getId(),row.getApplication(),row.getRecipientKey())){
            terminal(row,"CANCELLED","RECIPIENT_SUPPRESSED");return Optional.empty();
        }
        profile.setQuotaDay(today);profile.setDayAttempts(dayCount+1);profile.setQuotaMinute(minute);profile.setMinuteAttempts(minuteCount+1);
        profiles.updateById(profile);
        row.setAttempts(row.getAttempts()+1); row.setState("SENDING");row.setLeaseUntil(clock.now().plus(settings.leaseDuration()));deliveries.updateById(row);
        var attempt=new MailAttemptEntity();attempt.setDeliveryId(row.getId());attempt.setFence(row.getFence());
        attempt.setState("SENDING");attempt.setStartedAt(clock.now());attempts.insert(attempt);
        return Optional.of(new Prepared(claim,row.getMessageId(),payload,fixed));
    }

    /** Recheck immediately before transport. No transaction or lock is held during SMTP itself. */
    public boolean stillValid(Claim claim){
        requireWorker();var row=locked(claim);
        return row!=null && row.getState().equals("SENDING") && row.getReceiptState()==null && row.getLeaseUntil().isAfter(clock.now())
                && row.getExpiresAt().isAfter(clock.now()) && tenants.securityState(row.getTenantId()).filter(TenantEntryQuery.SecurityState::acceptsBusiness).isPresent()
                && guards.mayDeliver(row);
    }
    public boolean finish(Claim claim,Outcome outcome) {
        requireWorker();var row=locked(claim);
        if(row==null || !row.getState().equals("SENDING")) return false;
        var attempt=attempts.selectOne(new LambdaQueryWrapper<MailAttemptEntity>().eq(MailAttemptEntity::getDeliveryId,row.getId())
                .eq(MailAttemptEntity::getFence,row.getFence()));
        if(attempt==null) throw new IllegalStateException("Missing mail attempt");
        attempt.setState(outcome.name());attempt.setFinishedAt(clock.now());attempt.setResultCode(outcome.name());attempts.updateById(attempt);
        if(row.getReceiptState()!=null)terminal(row,"ACCEPTED","RECEIPT_CONFIRMED");
        else if(outcome==Outcome.RETRYABLE && row.getAttempts()<settings.maximumAttempts() && row.getExpiresAt().isAfter(clock.now())) {
            row.setState("QUEUED");row.setNextAttemptAt(clock.now().plusSeconds(Math.min(900,15L*(1L<<row.getAttempts()))+ThreadLocalRandom.current().nextInt(10)));
            row.setLeaseOwner(null);row.setLeaseUntil(null);row.setLastResult("RETRYABLE");deliveries.updateById(row);
        } else terminal(row,outcome==Outcome.RETRYABLE?"FAILED":outcome.name(),outcome.name());
        return true;
    }

    public int recover() {
        requireWorker(); int changed=0;
        var expired=deliveries.selectList(new LambdaQueryWrapper<MailDeliveryEntity>().in(MailDeliveryEntity::getState,List.of("CLAIMED","SENDING"))
                .le(MailDeliveryEntity::getLeaseUntil,clock.now()).orderByAsc(MailDeliveryEntity::getId).last("LIMIT 100 FOR UPDATE SKIP LOCKED"));
        for(var row:expired) {
            if(row.getState().equals("SENDING")) {
                var attempt=attempts.selectOne(new LambdaQueryWrapper<MailAttemptEntity>().eq(MailAttemptEntity::getDeliveryId,row.getId()).eq(MailAttemptEntity::getFence,row.getFence()));
                if(attempt!=null){attempt.setState("UNKNOWN");attempt.setResultCode("LEASE_EXPIRED");attempt.setFinishedAt(clock.now());attempts.updateById(attempt);}
                terminal(row,"UNKNOWN","LEASE_EXPIRED");
            } else if(!row.getExpiresAt().isAfter(clock.now())) terminal(row,"CANCELLED","EXPIRED");
            else {row.setState("QUEUED");row.setFence(row.getFence()+1);row.setLeaseOwner(null);row.setLeaseUntil(null);row.setNextAttemptAt(clock.now());deliveries.updateById(row);}
            changed++;
        }
        // Erase any remaining queued/legacy payload by expiry; request fingerprints contain no plaintext.
        var purge=deliveries.selectList(new LambdaQueryWrapper<MailDeliveryEntity>().isNotNull(MailDeliveryEntity::getPayloadEnvelope)
                .le(MailDeliveryEntity::getExpiresAt,clock.now()).ne(MailDeliveryEntity::getState,"SENDING")
                .orderByAsc(MailDeliveryEntity::getId).last("LIMIT 100 FOR UPDATE SKIP LOCKED"));
        for(var row:purge){if(row.getState().equals("QUEUED")){row.setState("CANCELLED");outbox.releaseQueue(row);}row.setPayloadEnvelope(null);deliveries.updateById(row);changed++;}
        return changed;
    }
    private MailProfileRevisionEntity revision(long id,long version) {return profileVersions.selectOne(new LambdaQueryWrapper<MailProfileRevisionEntity>()
            .eq(MailProfileRevisionEntity::getProfileId,id).eq(MailProfileRevisionEntity::getConfigurationVersion,version));}
    private MailDeliveryEntity locked(Claim claim) {
        ActorContext.requireTenant(claim.tenantId());
        var row=deliveries.selectOne(new LambdaQueryWrapper<MailDeliveryEntity>().eq(MailDeliveryEntity::getId,claim.deliveryId()).last("FOR UPDATE"));
        return row!=null && row.getFence()==claim.fence() && Objects.equals(row.getLeaseOwner(),claim.worker())?row:null;
    }
    private void terminal(MailDeliveryEntity row,String state,String result) {
        if(Set.of("QUEUED","CLAIMED","SENDING").contains(row.getState()))outbox.releaseQueue(row);
        row.setState(state);row.setLastResult(result);row.setLeaseOwner(null);row.setLeaseUntil(null);
        // Reconciliation uses message/attempt identifiers, not reusable OTP or notification plaintext.
        row.setPayloadEnvelope(null);
        deliveries.updateById(row);
    }
    private void requireWorker(){if(ActorContext.require().kind()!=ActorRef.Kind.SYSTEM_JOB)throw new org.springframework.security.access.AccessDeniedException("Mail worker identity required");}
}
