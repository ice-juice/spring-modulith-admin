package com.sma.admin.tenant.internal.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Native protocol: durable begin, bounded external read, durable finish. Never holds a database transaction over DNS/TLS. */
@Service
public class DomainVerificationService {
    private final DomainLifecycleService lifecycle;private final DomainProbe probe;
    public DomainVerificationService(DomainLifecycleService lifecycle,DomainProbe probe) {this.lifecycle=lifecycle;this.probe=probe;}
    public DomainLifecycleService.CheckView verify(long id,long version,String operationId) {
        if(TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Domain network verification cannot run inside a database transaction");
        var started=lifecycle.begin(id,version,operationId);if(started.work()==null)return started.result();
        DomainProbe.Result result;
        try {result=probe.verify(started.work().hostname(),started.work().challenge());}
        catch(RuntimeException failure) {try{lifecycle.failed(started.work());}catch(RuntimeException persistence){failure.addSuppressed(persistence);}throw failure;}
        return lifecycle.finish(started.work(),result);
    }
}
