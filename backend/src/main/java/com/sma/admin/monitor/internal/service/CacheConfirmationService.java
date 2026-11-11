package com.sma.admin.monitor.internal.service;
import org.springframework.stereotype.Service;
import com.sma.admin.common.cache.CacheConfirmations;
@Service
public class CacheConfirmationService {
    private final CacheConfirmations confirmations;
    public CacheConfirmationService(CacheConfirmations confirmations){this.confirmations=confirmations;}
    public CacheConfirmations.Report task(String id){return confirmations.task(id);}
    public CacheConfirmations.Runtime nodes(){return confirmations.nodes();}
}
