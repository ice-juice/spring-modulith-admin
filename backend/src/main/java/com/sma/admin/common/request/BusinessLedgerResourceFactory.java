package com.sma.admin.common.request;

import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.transaction.PlatformTransactionManager;

/** Host-only assembly of a business database's framework ledger. */
public interface BusinessLedgerResourceFactory {
    /** Host-created concrete pool; Common attaches its physical identity and guarded mapper factory. */
    Resource preparePhysical(String resourceId, long revision, DataSource concretePool);

    Resource prepare(String resourceId, long revision, DataSource source,
                     SqlSessionFactory mappers, PlatformTransactionManager transactions);

    interface Resource {
        String resourceId();
        long revision();
        /** Caller holds a resource lease until the returned session is closed. */
        BusinessLedger.Session open(Runnable release);
        /** Claim one physical resource event and deliver outside its transaction. */
        boolean deliverOne(java.util.List<BusinessOutboxConsumer> consumers);
        /** Committed revision from the same physical resource, scoped to one trusted tenant. */
        long authoritativeRevision(long tenantId, String region);
    }
}
