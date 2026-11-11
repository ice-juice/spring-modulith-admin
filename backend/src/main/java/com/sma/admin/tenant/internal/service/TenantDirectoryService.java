package com.sma.admin.tenant.internal.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import jakarta.validation.constraints.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sma.admin.common.*;
import com.sma.admin.common.security.*;
import com.sma.admin.tenant.TenantErrorCode;
import com.sma.admin.tenant.internal.persistence.*;

@Service
@Transactional(readOnly=true)
public class TenantDirectoryService implements TenantEntryQuery {
    private final TenantRecordMapper tenants;
    private final DomainMapper domains;
    private final PlatformOperators operators;
    private final TenantPlatformProperties properties;
    public TenantDirectoryService(TenantRecordMapper tenants, DomainMapper domains, PlatformOperators operators, TenantPlatformProperties properties) {
        this.tenants=tenants; this.domains=domains; this.operators=operators; this.properties=properties;
    }
    public record TenantInput(@NotBlank @Pattern(regexp="[a-z][a-z0-9-]{0,63}") String code, @NotBlank @Size(max=128) String name) {}
    public record TenantView(Long id,String code,String name,String status,long securityGeneration) {}
    public record StatusInput(@NotBlank @Pattern(regexp="ACTIVE|SUSPENDED|MAINTENANCE|RETIRED") String status,@Min(1) long expectedGeneration) {}
    public record DomainInput(@Min(1) long tenantId,@NotBlank @Size(max=253) String hostname,
                              @NotBlank @Pattern(regexp="[a-z][a-z0-9-]{0,31}") String portal) {}
    public record DomainView(Long id,Long tenantId,String hostname,String portal,String status,boolean primaryDomain,
                             String verificationStatus,String tlsStatus,String provenance,long version,String dnsStatus,
                             java.time.Instant verifiedAt,java.time.Instant tlsValidUntil,String challengeRecord,String challengeValue,
                             java.time.Instant challengeExpiresAt,Long lastCheckId,String checkStatus,String checkFailure,
                             java.time.Instant checkLeaseUntil,java.time.Instant checkCompletedAt,boolean activationReady) {}

    public PageResult<TenantView> page(PageQuery page,String keyword) {
        operators.require();
        var query=new LambdaQueryWrapper<TenantRecordEntity>().orderByAsc(TenantRecordEntity::getId);
        if (keyword!=null && !keyword.isBlank()) {
            if (keyword.length()>128) throw new BizException(TenantErrorCode.INVALID);
            query.and(q -> q.like(TenantRecordEntity::getCode,keyword).or().like(TenantRecordEntity::getName,keyword));
        }
        var result=tenants.selectPage(page.<TenantRecordEntity>toPage(Map.of("id","id","code","code","name","name")),query);
        return new PageResult<>(result.getRecords().stream().map(this::view).toList(),result.getTotal(),result.getCurrent(),result.getSize());
    }

    @Transactional public TenantView create(TenantInput input) {
        operators.require();
        if(input.code()==null || !input.code().matches("[a-z][a-z0-9-]{0,63}") || input.name()==null || input.name().isBlank() || input.name().length()>128)
            throw new BizException(TenantErrorCode.INVALID);
        var row=new TenantRecordEntity(); row.setCode(input.code()); row.setName(input.name());
        row.setStatus("PROVISIONING"); row.setSecurityGeneration(1L);
        try { tenants.insert(row); } catch(DuplicateKeyException duplicate) { throw new BizException(TenantErrorCode.DUPLICATE); }
        return view(row);
    }

    @Transactional public TenantView changeStatus(long id,StatusInput input) {
        operators.require();
        if(id==properties.platformTenantId()) throw new BizException(TenantErrorCode.STATE);
        var row=lockedTenant(id);
        if(row.getSecurityGeneration()!=input.expectedGeneration()) throw new BizException(TenantErrorCode.STALE);
        var allowed=switch(row.getStatus()) {
            case "PROVISIONING" -> Set.of("ACTIVE","RETIRED");
            case "ACTIVE" -> Set.of("SUSPENDED","MAINTENANCE");
            case "MAINTENANCE" -> Set.of("ACTIVE","SUSPENDED");
            case "SUSPENDED" -> Set.of("ACTIVE","RETIRED");
            default -> Set.<String>of();
        };
        if(input.status()==null || !allowed.contains(input.status())) throw new BizException(TenantErrorCode.STATE);
        row.setStatus(input.status()); row.setSecurityGeneration(Math.addExact(row.getSecurityGeneration(),1));
        tenants.updateById(row); return view(row);
    }

    public List<DomainView> domains(long tenantId) {
        operators.require(); requireTenant(tenantId);
        return domains.selectList(new LambdaQueryWrapper<DomainEntity>().eq(DomainEntity::getOwnerTenantId,tenantId)
                .orderByAsc(DomainEntity::getHostname)).stream().map(TenantDirectoryService::domainView).toList();
    }

    /** Registration reserves a globally unique host; it never activates DNS/TLS or an anonymous entry. */
    @Transactional public DomainView registerDomain(DomainInput input) {
        operators.require(); var tenant=lockedTenant(input.tenantId());
        if("RETIRED".equals(tenant.getStatus())) throw new BizException(TenantErrorCode.STATE);
        String host;
        try { host=TenantHost.domain(input.hostname()); } catch(IllegalArgumentException invalid) { throw new BizException(TenantErrorCode.INVALID); }
        if(input.portal()==null || !input.portal().matches("[a-z][a-z0-9-]{0,31}")) throw new BizException(TenantErrorCode.INVALID);
        if(domains.selectCount(new LambdaQueryWrapper<DomainEntity>().eq(DomainEntity::getOwnerTenantId,input.tenantId()))>=20)
            throw new BizException(TenantErrorCode.INVALID);
        var row=new DomainEntity(); row.setOwnerTenantId(input.tenantId()); row.setHostname(host); row.setPortal(input.portal());
        row.setStatus("PENDING"); row.setPrimaryDomain(false); row.setVerificationStatus("PENDING"); row.setTlsStatus("PENDING"); row.setProvenance("REQUESTED");
        try { domains.insert(row); } catch(DuplicateKeyException duplicate) { throw new BizException(TenantErrorCode.DUPLICATE); }
        return domainView(row);
    }

    @Override public Optional<TenantEntry> resolve(String host) {
        if(host==null || !TenantHost.authority(host).equals(host)) return Optional.empty();
        var domain=domains.selectOne(new LambdaQueryWrapper<DomainEntity>().eq(DomainEntity::getHostname,host).eq(DomainEntity::getStatus,"ACTIVE"));
        if(domain==null || !DomainEligibility.verified(domain,java.time.Instant.now())) return Optional.empty();
        var tenant=tenants.selectById(domain.getOwnerTenantId());
        if(tenant==null || !tenant.getStatus().equals("ACTIVE")) return Optional.empty();
        return Optional.of(new TenantEntry(tenant.getId(),domain.getId(),domain.getHostname(),domain.getPortal(),tenant.getSecurityGeneration()));
    }

    @Override
    public Optional<SecurityState> securityState(long id) {
        if(id<=0) return Optional.empty();
        var tenant=tenants.currentSecurityState(id);
        return tenant==null?Optional.empty():Optional.of(new SecurityState(id,tenant.getSecurityGeneration(),
                tenant.getStatus().equals("ACTIVE"),tenant.getStatus().equals("ACTIVE")));
    }

    private TenantRecordEntity requireTenant(long id) {
        var row=tenants.selectById(id); if(row==null) throw new BizException(CommonErrorCode.DATA_NOT_FOUND); return row;
    }
    private TenantRecordEntity lockedTenant(long id) {
        var row=tenants.selectOne(new LambdaQueryWrapper<TenantRecordEntity>().eq(TenantRecordEntity::getId,id).last("FOR UPDATE"));
        if(row==null) throw new BizException(CommonErrorCode.DATA_NOT_FOUND); return row;
    }
    private TenantView view(TenantRecordEntity row) { return new TenantView(row.getId(),row.getCode(),row.getName(),row.getStatus(),row.getSecurityGeneration()); }
    static DomainView domainView(DomainEntity row) {
        var now=java.time.Instant.now();
        String tls="READY".equals(row.getTlsStatus()) && row.getTlsValidUntil()!=null && !row.getTlsValidUntil().isAfter(now)?"EXPIRED":row.getTlsStatus();
        String check="RUNNING".equals(row.getCheckStatus()) && row.getCheckLeaseUntil()!=null && !row.getCheckLeaseUntil().isAfter(now)?"EXPIRED":row.getCheckStatus();
        return new DomainView(row.getId(),row.getOwnerTenantId(),row.getHostname(),row.getPortal(),row.getStatus(),row.getPrimaryDomain(),
                row.getVerificationStatus(),tls,row.getProvenance(),row.getConfigurationVersion(),row.getDnsStatus(),row.getVerifiedAt(),row.getTlsValidUntil(),
                row.getChallengeToken()==null?null:"_sma-tenant."+row.getHostname(),row.getChallengeToken(),row.getChallengeExpiresAt(),row.getLastCheckId(),check,row.getCheckFailure(),row.getCheckLeaseUntil(),row.getCheckCompletedAt(),DomainEligibility.activatable(row,now));
    }
}
