package com.sma.admin.defense.internal.service;

import java.util.List;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.IntSupplier;
import java.util.function.LongToIntFunction;
import com.sma.admin.common.deployment.*;
import com.sma.admin.common.security.TenantWorkQuery;

/** Single host execution thread. No tenant context is inherited; the store owns each tenant transaction. */
final class SecurityRetentionCycle {
    private final DefenseMaintenanceProperties settings;private final DeploymentWork admission;private final TenantWorkQuery directory;
    private final IntSupplier platform;private final LongToIntFunction tenant;
    private long cursor;private volatile boolean stopping;
    final LongAdder platformDeleted=new LongAdder(),tenantDeleted=new LongAdder(),failures=new LongAdder(),denied=new LongAdder(),cycles=new LongAdder();
    SecurityRetentionCycle(DefenseMaintenanceProperties settings,DeploymentWork admission,TenantWorkQuery directory,IntSupplier platform,LongToIntFunction tenant){
        this.settings=settings;this.admission=admission;this.directory=directory;this.platform=platform;this.tenant=tenant;
    }
    void cycle(){
        DeploymentEvidence.requireHost();if(stopping)return;cycles.increment();
        var permit=permit();if(permit==null)return;
        try(var platformWork=permit){platformDeleted.add(platform.getAsInt());}catch(RuntimeException failed){failures.increment();}
        permit=permit();if(permit==null)return;
        List<TenantWorkQuery.Entry> entries;
        try(var discovery=permit){
            entries=List.copyOf(directory.page(cursor,settings.tenantBatch()));
            if(entries.size()>settings.tenantBatch())throw new IllegalStateException("Unbounded maintenance directory");
            long previous=cursor;for(var entry:entries){if(entry.tenantId()<=previous)throw new IllegalStateException("Invalid maintenance directory order");previous=entry.tenantId();}
        }catch(RuntimeException failed){failures.increment();return;}
        if(entries.isEmpty()){cursor=0;return;}
        for(var entry:entries){
            permit=permit();if(permit==null)return;
            // Paused tenants still have retention obligations. A failure stays in durable storage and is retried on the next sweep.
            try(var cleanup=permit){tenantDeleted.add(tenant.applyAsInt(entry.tenantId()));}
            catch(RuntimeException failed){failures.increment();}
            cursor=entry.tenantId();
        }
    }
    private DeploymentWork.Permit permit(){
        if(stopping)return null;
        try{var accepted=admission.tryStart();if(accepted.isEmpty()){denied.increment();return null;}return accepted.orElseThrow();}
        catch(RuntimeException unavailable){failures.increment();return null;}
    }
    void stop(){stopping=true;}
}
