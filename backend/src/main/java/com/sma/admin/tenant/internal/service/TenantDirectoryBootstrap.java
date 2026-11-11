package com.sma.admin.tenant.internal.service;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sma.admin.tenant.internal.persistence.*;

/** Only explicit loopback development hosts can be bootstrapped. Production domains require verification. */
@Component
public class TenantDirectoryBootstrap {
    private final TenantPlatformProperties properties;
    private final TenantRecordMapper tenants;
    private final DomainMapper domains;
    public TenantDirectoryBootstrap(TenantPlatformProperties properties,TenantRecordMapper tenants,DomainMapper domains) {
        this.properties=properties; this.tenants=tenants; this.domains=domains;
    }
    @EventListener(ApplicationReadyEvent.class) @org.springframework.core.annotation.Order(-200) @Transactional
    public void initialize() {
        if(properties.bootstrapHosts().isEmpty()) return;
        if(!java.util.Set.of("localhost","127.0.0.1","[::1]").containsAll(properties.bootstrapHosts()))
            throw new IllegalArgumentException("Only explicit loopback hosts may bypass domain verification");
        var tenant=tenants.selectById(properties.platformTenantId());
        if(tenant==null) {
            tenant=new TenantRecordEntity(); tenant.setId(properties.platformTenantId()); tenant.setCode("platform"); tenant.setName("Platform");
            tenant.setStatus("ACTIVE"); tenant.setSecurityGeneration(1L); tenants.insert(tenant);
        }
        tenants.selectOne(new LambdaQueryWrapper<TenantRecordEntity>().eq(TenantRecordEntity::getId,tenant.getId()).last("FOR UPDATE"));
        for(String host : properties.bootstrapHosts()) {
            var existing=domains.selectOne(new LambdaQueryWrapper<DomainEntity>().eq(DomainEntity::getHostname,host));
            if(existing!=null) {
                if(existing.getOwnerTenantId().longValue()!=tenant.getId()) throw new IllegalStateException("Bootstrap domain belongs to another tenant");
                continue; // Do not reactivate domains disabled by an administrator.
            }
            var domain=new DomainEntity(); domain.setOwnerTenantId(tenant.getId()); domain.setHostname(host); domain.setPortal("admin");
            domain.setStatus("ACTIVE"); domain.setPrimaryDomain(false); domain.setVerificationStatus("VERIFIED"); domain.setTlsStatus("LOCAL"); domain.setProvenance("DEPLOYMENT");domain.setDnsStatus("LOCAL");
            domains.insert(domain);
        }
    }
}
