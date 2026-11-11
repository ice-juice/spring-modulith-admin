package com.sma.admin.identity.internal.service;

import com.sma.admin.common.security.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Bounded sweep also includes inactive tenants; it never performs OAuth requests or creates unused tenant budgets. */
@Component @EnableConfigurationProperties(IdentityLoginMaintenance.Settings.class)
public class IdentityLoginMaintenance {
    @ConfigurationProperties("sma.identity.login-maintenance")
    public record Settings(Boolean enabled,Integer tenantPageSize,Long pollMillis){
        public Settings{enabled=enabled==null?true:enabled;tenantPageSize=tenantPageSize==null?10:tenantPageSize;pollMillis=pollMillis==null?10000:pollMillis;
            if(tenantPageSize<1 || tenantPageSize>50 || pollMillis<1000 || pollMillis>300000)throw new IllegalArgumentException("Invalid identity maintenance bounds");}
    }
    private static final Logger log=LoggerFactory.getLogger(IdentityLoginMaintenance.class);
    private final TenantWorkQuery tenants;private final IdentityLoginStore store;private final Settings settings;private long cursor;
    public IdentityLoginMaintenance(TenantWorkQuery tenants,IdentityLoginStore store,Settings settings){this.tenants=tenants;this.store=store;this.settings=settings;}
    @Scheduled(fixedDelayString="${sma.identity.login-maintenance.poll-millis:10000}")
    public synchronized void poll(){
        if(!settings.enabled())return;
        try{
            var page=tenants.page(cursor,settings.tenantPageSize());
            for(var entry:page){
                try{ActorContext.withinJob(entry.tenantId(),"identity-login-maintenance",store::maintain);}
                catch(RuntimeException unavailable){log.warn("Identity login maintenance unavailable for tenant {}",entry.tenantId());}
                cursor=entry.tenantId();
            }
            if(page.size()<settings.tenantPageSize())cursor=0;
        }catch(RuntimeException unavailable){log.warn("Identity login tenant discovery unavailable");}
    }
}
