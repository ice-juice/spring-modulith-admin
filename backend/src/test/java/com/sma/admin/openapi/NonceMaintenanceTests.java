package com.sma.admin.openapi;
import static org.assertj.core.api.Assertions.*;
import static com.sma.admin.support.DeploymentWorkFixture.host;
import com.sma.admin.common.deployment.DeploymentWork;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.sma.admin.openapi.internal.persistence.*;
import com.sma.admin.openapi.internal.service.NonceMaintenance;
import com.sma.admin.common.security.*;
import com.sma.admin.support.IntegrationTest;

@IntegrationTest
class NonceMaintenanceTests {
    @Autowired DeploymentWork admission;
    @Autowired NonceMapper nonces;@Autowired NonceMaintenance maintenance;@Autowired PlatformTransactionManager manager;
    private <T>T job(long tenant,Supplier<T> work){return ActorContext.withinJob(tenant,"openapi-nonce:test",work);}
    private void seed(long tenant,int expired){job(tenant,()->new TransactionTemplate(manager).execute(status->{
        for(int i=0;i<expired+2;i++){var row=new NonceEntity();row.setApplicationId(101L);row.setNonce(UUID.randomUUID().toString());row.setExpiresAt(Instant.now().plusSeconds(i<expired?-60:3600));nonces.insert(row);}return null;
    }));}
    @Test void cleanupIsBoundedTenantScopedAndNeverRefundsUnexpiredNonces(){
        seed(90211,203);seed(90212,1);
        assertThat(host(()->maintenance.cleanTenant(90211))).isEqualTo(200);assertThat(job(90211,()->nonces.selectCount(null))).isEqualTo(5);
        assertThat(host(()->maintenance.cleanTenant(90211))).isEqualTo(3);assertThat(host(()->maintenance.cleanTenant(90211))).isZero();
        assertThat(job(90211,()->nonces.selectCount(null))).isEqualTo(2);assertThat(job(90212,()->nonces.selectCount(null))).isEqualTo(3);
    }
    @Test void twoIndependentWorkersDeleteExpiredRowsOnlyOnce()throws Exception{
        seed(90213,203);var start=new CountDownLatch(1);
        try(var second=new NonceMaintenance(nonces,(after,limit)->List.of(),new NonceMaintenance.Settings(false,2,1000L),manager,admission);var pool=Executors.newFixedThreadPool(2)){
            var a=pool.submit(()->{start.await();return maintenance.cleanTenant(90213);});var b=pool.submit(()->{start.await();return second.cleanTenant(90213);});start.countDown();
            int first=a.get(10,TimeUnit.SECONDS),other=b.get(10,TimeUnit.SECONDS);assertThat(first).isBetween(0,200);assertThat(other).isBetween(0,200);assertThat(first+other).isEqualTo(203);
            assertThat(job(90213,()->nonces.selectCount(null))).isEqualTo(2);
        }
    }
}
