package com.sma.admin.common.internal.cache;

import java.util.HashMap;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.sma.admin.common.cache.*;
import com.sma.admin.common.internal.service.*;

/** Apply on this process outside a DB work transaction, then fence the persisted acknowledgement. */
@Component
public class CacheConfirmationProcessor {
    private final ManagedCache cache;
    private final CacheConfirmationStore store;
    private final CacheNodeIdentity identity;
    public CacheConfirmationProcessor(ManagedCache cache,CacheConfirmationStore store,CacheNodeIdentity identity){this.cache=cache;this.store=store;this.identity=identity;}
    public int processTenant(long epoch){
        if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Confirmation must not hold a work transaction while applying cache state");
        if(!(cache instanceof DurableManagedCache durable))throw new CacheUnavailableException();
        var requests=store.due(identity,epoch,16);var states=new HashMap<String,CacheRevisionStore.State>();int confirmed=0;
        for(var request:requests){
            final CacheRevisionStore.State state;
            try{state=states.computeIfAbsent(request.region(),durable::controlState);}
            catch(RuntimeException unavailable){store.failed(request.id(),identity,epoch);continue;}
            if(store.confirm(request.id(),identity,epoch,state))confirmed++;
            else store.failed(request.id(),identity,epoch);
        }
        return confirmed;
    }
}
