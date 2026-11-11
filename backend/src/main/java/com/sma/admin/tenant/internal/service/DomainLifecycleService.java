package com.sma.admin.tenant.internal.service;

import java.security.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import tools.jackson.databind.json.JsonMapper;
import com.sma.admin.common.*;
import com.sma.admin.common.security.CurrentUser;
import com.sma.admin.tenant.TenantErrorCode;
import com.sma.admin.tenant.internal.persistence.*;

/** Short database transactions only. The network orchestrator must call begin/finish in separate transactions. */
@Service @Transactional(readOnly=true)
public class DomainLifecycleService {
    private final DomainMapper domains;private final DomainCheckMapper checks;private final TenantRecordMapper tenants;
    private final PlatformOperators operators;private final TenantPlatformProperties platform;private final DomainVerificationProperties settings;private final JsonMapper json;
    private final SecureRandom random=new SecureRandom();
    public DomainLifecycleService(DomainMapper domains,DomainCheckMapper checks,TenantRecordMapper tenants,PlatformOperators operators,
            TenantPlatformProperties platform,DomainVerificationProperties settings,JsonMapper json) {
        this.domains=domains;this.checks=checks;this.tenants=tenants;this.operators=operators;this.platform=platform;this.settings=settings;this.json=json;
    }
    public record Settings(boolean configured,List<String> ingressAddresses) {}
    public record CheckView(Long id,Long domainId,String operationId,String status,String failureCode,boolean ownershipVerified,boolean dnsReady,
            boolean tlsReady,Instant tlsValidUntil,List<String> addresses,List<String> fingerprints,Instant createdAt,Instant leaseUntil,Instant completedAt) {}
    public record Work(Long checkId,Long domainId,long version,String hostname,String challenge) {
        @Override public String toString() { return "DomainCheckWork[checkId="+checkId+", domainId="+domainId+", version="+version+"]"; }
    }
    public record Begin(CheckView result,Work work) {}
    private record Locked(TenantRecordEntity tenant,DomainEntity domain) {}

    public Settings settings() { require("tenant:domain:view");return new Settings(!settings.ingressAddresses().isEmpty(),settings.ingressAddresses().stream().sorted().toList()); }
    public TenantDirectoryService.DomainView get(long id) { require("tenant:domain:view");return TenantDirectoryService.domainView(domain(id)); }
    public CheckView check(long id,String operationId) {
        require("tenant:domain:verify");operation(operationId);domain(id);
        var row=checks.selectOne(checkScope(id,operationId));if(row==null) throw new BizException(CommonErrorCode.DATA_NOT_FOUND);
        if(!Objects.equals(row.getCreatedBy(),CurrentUser.userId())) throw new AccessDeniedException("Verification receipt belongs to another operator");
        return view(row);
    }
    @Transactional public TenantDirectoryService.DomainView challenge(long id,long expectedVersion) {
        require("tenant:domain:verify");var locked=locked(id);mutable(locked);var row=locked.domain();version(row,expectedVersion);
        if(DomainEligibility.local(row) || row.getHostname().length()>241 || !row.getHostname().contains(".") || row.getHostname().matches("[0-9.]+")) throw new BizException(TenantErrorCode.INVALID);
        supersede(row);row.setLastCheckId(null);row.setCheckStatus("IDLE");row.setCheckFailure("NONE");row.setCheckLeaseUntil(null);row.setCheckCompletedAt(null);
        byte[] value=new byte[32];random.nextBytes(value);
        row.setChallengeToken("sma-tenant-v1="+Base64.getUrlEncoder().withoutPadding().encodeToString(value));row.setChallengeExpiresAt(now().plusSeconds(86400));
        changed(row);return TenantDirectoryService.domainView(row);
    }
    @Transactional public Begin begin(long id,long expectedVersion,String operationId) {
        require("tenant:domain:verify");operation(operationId);var locked=locked(id);mutable(locked);var row=locked.domain();
        String intent=hash(id+":"+expectedVersion);
        var replay=checks.selectOne(checkScope(id,operationId).last("FOR UPDATE"));
        if(replay!=null) {
            if(!Objects.equals(replay.getCreatedBy(),CurrentUser.userId()) || !intent.equals(replay.getIntentHash())) throw new BizException(TenantErrorCode.STALE);
            return new Begin(view(replay),null);
        }
        version(row,expectedVersion);
        if(settings.ingressAddresses().isEmpty()) throw new BizException(TenantErrorCode.DOMAIN_VERIFICATION_DISABLED);
        var now=now();
        if(DomainEligibility.local(row) || row.getChallengeToken()==null || row.getChallengeExpiresAt()==null || !row.getChallengeExpiresAt().isAfter(now))
            throw new BizException(TenantErrorCode.DOMAIN_CHALLENGE_EXPIRED);
        if(row.getLastCheckId()!=null) {
            var old=lockedCheck(row.getLastCheckId());
            if(old!=null && "RUNNING".equals(old.getStatus())) {
                if(old.getLeaseUntil().isAfter(now)) throw new BizException(TenantErrorCode.DOMAIN_CHECK_RUNNING);
                old.setStatus("FAILED");old.setFailureCode("TIMEOUT");old.setCompletedAt(now);checks.updateById(old);
            }
        }
        if(checks.selectCount(new LambdaQueryWrapper<DomainCheckEntity>().eq(DomainCheckEntity::getDomainId,id))>=1000) throw new BizException(TenantErrorCode.DOMAIN_CHECK_CAPACITY);
        var check=new DomainCheckEntity();check.setOwnerTenantId(row.getOwnerTenantId());check.setDomainId(id);check.setOperationId(operationId);check.setIntentHash(intent);
        check.setDomainVersion(Math.addExact(row.getConfigurationVersion(),1));check.setChallengeHash(hash(row.getChallengeToken()));check.setStatus("RUNNING");check.setLeaseUntil(now.plusSeconds(30));checks.insert(check);
        row.setLastCheckId(check.getId());row.setCheckStatus("RUNNING");row.setCheckFailure("NONE");row.setCheckLeaseUntil(check.getLeaseUntil());row.setCheckCompletedAt(null);
        changed(row);
        return new Begin(view(check),new Work(check.getId(),row.getId(),row.getConfigurationVersion(),row.getHostname(),row.getChallengeToken()));
    }
    @Transactional public CheckView finish(Work work,DomainProbe.Result result) { return finish(work,result,null); }
    @Transactional public CheckView failed(Work work) { return finish(work,null,"INTERNAL_ERROR"); }
    private CheckView finish(Work work,DomainProbe.Result result,String failure) {
        require("tenant:domain:verify");var locked=locked(work.domainId());var row=locked.domain();
        var check=checks.selectOne(new LambdaQueryWrapper<DomainCheckEntity>().eq(DomainCheckEntity::getId,work.checkId()).eq(DomainCheckEntity::getDomainId,work.domainId()).last("FOR UPDATE"));
        if(check==null) throw new BizException(CommonErrorCode.DATA_NOT_FOUND);
        if(!Objects.equals(check.getCreatedBy(),CurrentUser.userId())) throw new AccessDeniedException("Verification receipt belongs to another operator");
        if(!"RUNNING".equals(check.getStatus())) return view(check);
        var now=now();boolean owns=Objects.equals(row.getLastCheckId(),check.getId());
        boolean current=owns && row.getConfigurationVersion()==work.version() && check.getDomainVersion()==work.version()
                && check.getChallengeHash().equals(hash(row.getChallengeToken())) && !"RETIRED".equals(locked.tenant().getStatus());
        if(!current) { check.setStatus("SUPERSEDED");check.setFailureCode("CONFIG_CHANGED"); }
        else if(!check.getLeaseUntil().isAfter(now) || row.getChallengeExpiresAt()==null || !row.getChallengeExpiresAt().isAfter(now)) { check.setStatus("FAILED");check.setFailureCode("TIMEOUT"); }
        else if(failure!=null) { check.setStatus("FAILED");check.setFailureCode(failure); }
        else {
            check.setOwnershipVerified(result.ownershipVerified());check.setDnsReady(result.dnsReady());check.setTlsReady(result.tlsReady());
            check.setTlsValidUntil(millis(result.tlsValidUntil()));check.setAddressesJson(json.writeValueAsString(result.addresses()));check.setFingerprintsJson(json.writeValueAsString(result.fingerprints()));
            boolean success=result.successful() && result.tlsValidUntil().isAfter(now);
            check.setStatus(success?"SUCCEEDED":"FAILED");check.setFailureCode(success?"NONE":result.failure()==DomainProbe.Failure.NONE?"TLS_FAILED":result.failure().name());
            if(success) { row.setVerificationStatus("VERIFIED");row.setDnsStatus("READY");row.setTlsStatus("READY");row.setVerifiedAt(now);row.setVerifiedChallengeHash(check.getChallengeHash());row.setTlsValidUntil(millis(result.tlsValidUntil())); }
        }
        check.setCompletedAt(now);checks.updateById(check);
        if(owns) {
            row.setCheckStatus(check.getStatus());row.setCheckFailure(check.getFailureCode());row.setCheckLeaseUntil(null);row.setCheckCompletedAt(now);changed(row);
        }
        return view(check);
    }
    @Transactional public TenantDirectoryService.DomainView enabled(long id,long expectedVersion,boolean enabled) {
        require("tenant:domain:update");var locked=locked(id);mutable(locked);var row=locked.domain();version(row,expectedVersion);
        if(enabled) {
            if(!"ACTIVE".equals(locked.tenant().getStatus()) || !DomainEligibility.activatable(row,now())) throw new BizException(TenantErrorCode.DOMAIN_VERIFICATION_REQUIRED);
            if("ACTIVE".equals(row.getStatus())) return TenantDirectoryService.domainView(row);
            supersede(row);row.setStatus("ACTIVE");changed(row);
            if(activePrimary(row)==null) choosePrimary(row);
        } else {
            if("DISABLED".equals(row.getStatus())) return TenantDirectoryService.domainView(row);
            if("ACTIVE".equals(row.getStatus()) && row.getOwnerTenantId().equals(platform.platformTenantId()) && row.getPortal().equals("admin")
                    && owned(row.getOwnerTenantId(),"admin").stream().noneMatch(other->!other.getId().equals(id) && "ACTIVE".equals(other.getStatus()) && DomainEligibility.verified(other,now())))
                throw new BizException(TenantErrorCode.DOMAIN_LAST_ENTRY);
            supersede(row);boolean primary=row.getPrimaryDomain();row.setStatus("DISABLED");row.setPrimaryDomain(false);changed(row);
            var tenant=locked.tenant();tenant.setSecurityGeneration(Math.addExact(tenant.getSecurityGeneration(),1));tenants.updateById(tenant);
            if(primary) owned(row.getOwnerTenantId(),row.getPortal()).stream().filter(other->"ACTIVE".equals(other.getStatus()) && DomainEligibility.verified(other,now())).findFirst().ifPresent(this::choosePrimary);
        }
        return TenantDirectoryService.domainView(row);
    }
    @Transactional public TenantDirectoryService.DomainView primary(long id,long expectedVersion) {
        require("tenant:domain:update");var locked=locked(id);mutable(locked);var row=locked.domain();version(row,expectedVersion);
        if(!"ACTIVE".equals(row.getStatus()) || !DomainEligibility.verified(row,now())) throw new BizException(TenantErrorCode.DOMAIN_VERIFICATION_REQUIRED);
        if(!row.getPrimaryDomain()) choosePrimary(row);
        return TenantDirectoryService.domainView(row);
    }
    private void choosePrimary(DomainEntity selected) {
        for(var old:owned(selected.getOwnerTenantId(),selected.getPortal())) if(old.getPrimaryDomain() && !old.getId().equals(selected.getId())) {
            supersede(old);old.setPrimaryDomain(false);changed(old);
        }
        if(!selected.getPrimaryDomain()) {supersede(selected);selected.setPrimaryDomain(true);changed(selected);}
    }
    private DomainEntity activePrimary(DomainEntity row) { return owned(row.getOwnerTenantId(),row.getPortal()).stream().filter(other->other.getPrimaryDomain() && "ACTIVE".equals(other.getStatus()) && DomainEligibility.verified(other,now())).findFirst().orElse(null); }
    private List<DomainEntity> owned(long owner,String portal) { return domains.selectList(new LambdaQueryWrapper<DomainEntity>().eq(DomainEntity::getOwnerTenantId,owner).eq(DomainEntity::getPortal,portal).orderByAsc(DomainEntity::getId).last("FOR UPDATE")); }
    private void supersede(DomainEntity row) {
        if(row.getLastCheckId()==null) return;var check=lockedCheck(row.getLastCheckId());
        if(check!=null && "RUNNING".equals(check.getStatus())) {check.setStatus("SUPERSEDED");check.setFailureCode("CONFIG_CHANGED");check.setCompletedAt(now());checks.updateById(check);
            row.setCheckStatus("SUPERSEDED");row.setCheckFailure("CONFIG_CHANGED");row.setCheckLeaseUntil(null);row.setCheckCompletedAt(check.getCompletedAt());}
    }
    private Locked locked(long id) {
        var initial=domain(id);
        var tenant=tenants.selectOne(new LambdaQueryWrapper<TenantRecordEntity>().eq(TenantRecordEntity::getId,initial.getOwnerTenantId()).last("FOR UPDATE"));
        if(tenant==null) throw new BizException(CommonErrorCode.DATA_NOT_FOUND);
        var row=domains.selectOne(new LambdaQueryWrapper<DomainEntity>().eq(DomainEntity::getId,id).last("FOR UPDATE"));
        if(row==null || !Objects.equals(row.getOwnerTenantId(),tenant.getId())) throw new BizException(TenantErrorCode.STALE);
        return new Locked(tenant,row);
    }
    private DomainEntity domain(long id) {var row=domains.selectById(id);if(row==null)throw new BizException(CommonErrorCode.DATA_NOT_FOUND);return row;}
    private DomainCheckEntity lockedCheck(long id) {return checks.selectOne(new LambdaQueryWrapper<DomainCheckEntity>().eq(DomainCheckEntity::getId,id).last("FOR UPDATE"));}
    private void mutable(Locked value) {if("RETIRED".equals(value.tenant().getStatus()))throw new BizException(TenantErrorCode.STATE);}
    private void version(DomainEntity row,long version) {if(version<1 || row.getConfigurationVersion()!=version)throw new BizException(TenantErrorCode.STALE);}
    private void changed(DomainEntity row) {row.setConfigurationVersion(Math.addExact(row.getConfigurationVersion(),1));domains.updateById(row);}
    private void require(String permission) {operators.require();if(!CurrentUser.require().permissions().contains(permission))throw new AccessDeniedException("Domain operation permission required");}
    private void operation(String value) {if(value==null || !value.matches("[a-zA-Z0-9_-]{16,64}"))throw new BizException(TenantErrorCode.INVALID);}
    private LambdaQueryWrapper<DomainCheckEntity> checkScope(long id,String operation) {return new LambdaQueryWrapper<DomainCheckEntity>().eq(DomainCheckEntity::getDomainId,id).eq(DomainCheckEntity::getOperationId,operation);}
    private static Instant now() {return Instant.now().truncatedTo(ChronoUnit.MILLIS);}
    private static Instant millis(Instant value) {return value==null?null:value.truncatedTo(ChronoUnit.MILLIS);}
    private String hash(String value) {return DomainDigests.hash(value);}
    private CheckView view(DomainCheckEntity row) {
        String status="RUNNING".equals(row.getStatus()) && !row.getLeaseUntil().isAfter(now())?"EXPIRED":row.getStatus();
        return new CheckView(row.getId(),row.getDomainId(),row.getOperationId(),status,row.getFailureCode(),row.getOwnershipVerified(),row.getDnsReady(),row.getTlsReady(),millis(row.getTlsValidUntil()),
                Arrays.asList(json.readValue(row.getAddressesJson(),String[].class)),Arrays.asList(json.readValue(row.getFingerprintsJson(),String[].class)),millis(row.getCreatedAt()),row.getLeaseUntil(),millis(row.getCompletedAt()));
    }
}
