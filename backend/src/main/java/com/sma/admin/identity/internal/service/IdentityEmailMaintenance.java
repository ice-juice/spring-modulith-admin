package com.sma.admin.identity.internal.service;
import com.sma.admin.common.security.*;
import com.sma.admin.common.deployment.*;
import org.slf4j.*;
import org.springframework.boot.context.properties.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component @EnableConfigurationProperties(IdentityEmailMaintenance.Settings.class)
public class IdentityEmailMaintenance {
    @ConfigurationProperties("sma.identity.email-maintenance")
    public record Settings(Boolean enabled,Integer tenantPageSize,Long pollMillis){
        public Settings{enabled=enabled==null?true:enabled;tenantPageSize=tenantPageSize==null?10:tenantPageSize;pollMillis=pollMillis==null?10000:pollMillis;
            if(tenantPageSize<1 || tenantPageSize>50 || pollMillis<1000 || pollMillis>300000)throw new IllegalArgumentException("Invalid email maintenance bounds");}
    }
    private static final Logger log=LoggerFactory.getLogger(IdentityEmailMaintenance.class);
    private final TenantWorkQuery tenants;private final IdentityEmailChallengeStore store;private final IdentityReauthenticationStore reauthentication;private final Settings settings;private long cursor;
    private final DeploymentWork admission;
    public IdentityEmailMaintenance(TenantWorkQuery tenants,IdentityEmailChallengeStore store,IdentityReauthenticationStore reauthentication,Settings settings,DeploymentWork admission){this.tenants=tenants;this.store=store;this.reauthentication=reauthentication;this.settings=settings;this.admission=admission;}
    @Scheduled(fixedDelayString="${sma.identity.email-maintenance.poll-millis:10000}")
    public synchronized void poll(){
        DeploymentEvidence.requireHost();
        if(!settings.enabled())return;
        try{
            var discovery=admission.tryStart();if(discovery.isEmpty())return;
            java.util.List<TenantWorkQuery.Entry> page;
            try(var permit=discovery.orElseThrow()){page=tenants.page(cursor,settings.tenantPageSize());}
            for(var entry:page){
                var accepted=admission.tryStart();if(accepted.isEmpty())return;
                try(var permit=accepted.orElseThrow()){ActorContext.withinJob(entry.tenantId(),"identity-email-maintenance",store::maintain);}
                catch(RuntimeException unavailable){log.warn("Email challenge maintenance unavailable for tenant {}",entry.tenantId());}
                var reauth=admission.tryStart();if(reauth.isEmpty())return;
                try(var permit=reauth.orElseThrow()){ActorContext.withinJob(entry.tenantId(),"identity-reauthentication-maintenance",reauthentication::maintain);}
                catch(RuntimeException unavailable){log.warn("Reauthentication maintenance unavailable for tenant {}",entry.tenantId());}
                cursor=entry.tenantId();
            }
            if(page.size()<settings.tenantPageSize())cursor=0;
        }catch(RuntimeException unavailable){log.warn("Email challenge tenant discovery unavailable");}
    }
}
