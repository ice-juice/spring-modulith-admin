package com.sma.admin.mail.internal.service;

import java.util.UUID;
import com.sma.admin.common.security.ActorContext;
import com.sma.admin.common.security.TenantEntryQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class MailDispatchCycle {
    private final MailWorkService work;private final SmtpMailSender sender;
    private final TenantEntryQuery tenants;private final MailDispatchProperties settings;
    public MailDispatchCycle(MailWorkService work,SmtpMailSender sender,TenantEntryQuery tenants,MailDispatchProperties settings){
        this.work=work;this.sender=sender;this.tenants=tenants;this.settings=settings;
    }
    /** Called with a tenant discovered by the host directory; never expose this as a client-supplied tenant route. */
    public int runTenant(long tenantId){
        if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Mail dispatch requires no outer transaction");
        return ActorContext.withinJob(tenantId,"mail-dispatch",()->{
            work.recover();int sent=0;String worker=UUID.randomUUID().toString();
            for(int i=0;i<settings.batchPerTenant();i++){
                if(!active(tenantId))break;
                var claim=work.claim(worker);if(claim.isEmpty())break;
                var prepared=work.begin(claim.get());if(prepared.isEmpty())continue;
                if(!work.stillValid(claim.get())){work.finish(claim.get(),MailWorkService.Outcome.CANCELLED);continue;}
                var result=sender.send(prepared.get());
                // A failed commit leaves SENDING. Recovery marks UNKNOWN instead of resending a possible acceptance.
                if(work.finish(claim.get(),result))sent++;
            }
            return sent;
        });
    }
    private boolean active(long tenant){return tenants.securityState(tenant).filter(TenantEntryQuery.SecurityState::acceptsBusiness).isPresent();}
}
