package com.sma.admin.common.internal.cache;

import static org.assertj.core.api.Assertions.*;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.sma.admin.common.cache.CachePolicy;
import com.sma.admin.common.internal.service.CacheRevisionStore;
import com.sma.admin.common.internal.service.CacheRevisionStore.Change;
import com.sma.admin.common.security.TenantScope;
import com.sma.admin.support.IntegrationTest;

@IntegrationTest
class CacheControlPersistenceTests {
    private static final String REGION="config.parameters";
    @Autowired CacheRevisionStore store;
    @Autowired PlatformTransactionManager transactions;
    @Autowired DataSource database;
    private final CachePolicy policy=new CachePolicy(Duration.ofSeconds(20),Duration.ofSeconds(2),20,4096,1024);

    @Test void firstStatisticsResetDoesNotInvalidateDataAndPolicyPauseResumeUseSeparateVersions() {
        TenantScope.within(9401,()->{
            assertThat(store.state(REGION)).isEqualTo(CacheRevisionStore.State.initial());
            var reset=store.change(REGION,0,Change.RESET_STATS,null);
            assertThat(reset).isEqualTo(new CacheRevisionStore.State(0,1,1,false,null));
            var configured=store.change(REGION,1,Change.POLICY,policy);
            assertThat(configured).isEqualTo(new CacheRevisionStore.State(1,2,1,false,policy));
            assertThat(store.state(REGION)).isEqualTo(configured);
            var paused=store.change(REGION,2,Change.PAUSE,null);
            assertThat(paused).isEqualTo(new CacheRevisionStore.State(2,3,1,true,policy));
            assertThat(store.advance(REGION)).isEqualTo(3); // Business invalidation retains the control state.
            assertThat(store.state(REGION)).isEqualTo(new CacheRevisionStore.State(3,3,1,true,policy));
            assertThat(store.change(REGION,3,Change.RESUME,null)).isEqualTo(new CacheRevisionStore.State(4,4,1,false,policy));
            assertThat(store.change(REGION,4,Change.POLICY,null)).isEqualTo(new CacheRevisionStore.State(5,5,1,false,null));
            assertThat(store.state(REGION).override()).isNull(); // Explicit NULL fields must really be persisted.
            return null;
        });
    }

    @Test void rollbackAndWrongTenantCannotPublishControlOrResetAnotherTenantsStatistics() {
        TenantScope.within(9402,()->{
            var before=store.change(REGION,0,Change.POLICY,policy);
            new TransactionTemplate(transactions).execute(status->{
                store.change(REGION,1,Change.PAUSE,null);
                store.change(REGION,2,Change.RESET_STATS,null);
                assertThat(store.state(REGION)).isEqualTo(before); // Independent committed authority cannot see this transaction.
                status.setRollbackOnly();return null;
            });
            assertThat(store.state(REGION)).isEqualTo(before);
            TenantScope.within(9403,()->{
                assertThat(store.state(REGION)).isEqualTo(CacheRevisionStore.State.initial());
                assertThatThrownBy(()->store.change(REGION,1,Change.RESUME,null)).isInstanceOf(OptimisticLockingFailureException.class);
                assertThat(store.state(REGION)).isEqualTo(CacheRevisionStore.State.initial());return null;
            });
            assertThat(store.state(REGION)).isEqualTo(before);return null;
        });
    }

    @Test void concurrentEditorsCannotOverwriteTheWinningVersionAndBusinessWritesRemainMonotonic() throws Exception {
        var start=new CountDownLatch(1);
        try(var executor=Executors.newVirtualThreadPerTaskExecutor()) {
            var results=new ArrayList<Future<Boolean>>();
            for(var change:List.of(Change.PAUSE,Change.RESET_STATS))results.add(executor.submit(()->{
                start.await();return TenantScope.within(9404,()->{
                    try{store.change(REGION,0,change,null);return true;}
                    catch(OptimisticLockingFailureException expected){return false;}
                });
            }));
            start.countDown();int successes=0;for(var result:results)if(result.get(15,TimeUnit.SECONDS))successes++;
            assertThat(successes).isEqualTo(1);
        }
        TenantScope.within(9404,()->{
            var state=store.state(REGION);assertThat(state.controlVersion()).isEqualTo(1);
            long next=store.advance(REGION);assertThat(next).isEqualTo(state.revision()+1);
            assertThat(store.state(REGION).controlVersion()).isEqualTo(1);return null;
        });
    }

    @Test void committedPolicyAndRevisionComeFromOneFreshSnapshotEvenInsideAnOlderTransaction() throws Exception {
        TenantScope.within(9405,()->{store.change(REGION,0,Change.POLICY,policy);return null;});
        try(var executor=Executors.newVirtualThreadPerTaskExecutor()) {
            TenantScope.within(9405,()->new TransactionTemplate(transactions).execute(status->{
                assertThat(store.state(REGION).controlVersion()).isEqualTo(1);
                try{executor.submit(()->TenantScope.within(9405,()->store.change(REGION,1,Change.PAUSE,null))).get(15,TimeUnit.SECONDS);}
                catch(Exception failure){throw new AssertionError(failure);}
                assertThat(store.state(REGION)).isEqualTo(new CacheRevisionStore.State(2,2,0,true,policy));
                status.setRollbackOnly();return null;
            }));
        }
    }

    @Test void migrationRetainsExistingV1RowsAndAddsZeroRevisionControlWithoutChangingOldAdvanceSemantics() throws Exception {
        // A task-owned table in the guarded integration test schema reproduces the old V1 shape.
        String table="common_p08_control_upgrade_probe";
        try(var connection=database.getConnection();var sql=connection.createStatement()) {
            try {
                sql.execute(migration("V1__cache_revisions.sql",table));
                sql.executeUpdate("INSERT INTO "+table+" (id,tenant_id,region,revision,created_at,created_by,updated_at,updated_by,deleted) VALUES (1,9406,'config.parameters',17,UTC_TIMESTAMP(3),0,UTC_TIMESTAMP(3),0,0)");
                sql.execute(migration("V5__cache_control_state.sql",table));
                try(var rows=sql.executeQuery("SELECT revision,control_version,stats_epoch,paused,policy_ttl_millis FROM "+table+" WHERE id=1")){
                    assertThat(rows.next()).isTrue();assertThat(rows.getLong(1)).isEqualTo(17);
                    assertThat(rows.getLong(2)).isZero();assertThat(rows.getLong(3)).isZero();assertThat(rows.getBoolean(4)).isFalse();assertThat(rows.getObject(5)).isNull();
                }
                sql.executeUpdate("INSERT INTO "+table+" (id,tenant_id,region,revision,created_at,created_by,updated_at,updated_by,deleted) VALUES (2,9407,'config.parameters',0,UTC_TIMESTAMP(3),0,UTC_TIMESTAMP(3),0,0)");
                assertThatThrownBy(()->sql.executeUpdate("UPDATE "+table+" SET policy_ttl_millis=10 WHERE id=2")).isInstanceOf(java.sql.SQLException.class);
                sql.executeUpdate("UPDATE "+table+" SET revision=revision+1 WHERE id=2");
            } finally { sql.execute("DROP TABLE IF EXISTS "+table); }
        }
    }
    private String migration(String name,String table) throws Exception {
        return new ClassPathResource("db/migration/common/"+name).getContentAsString(StandardCharsets.UTF_8)
                .replace("common_cache_revision",table).replace("ck_common_cache_","ck_p08_probe_");
    }
}
