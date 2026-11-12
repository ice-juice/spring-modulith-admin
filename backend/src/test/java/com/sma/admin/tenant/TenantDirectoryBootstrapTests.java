package com.sma.admin.tenant;

import static org.assertj.core.api.Assertions.*;
import java.util.Set;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sma.admin.support.IntegrationTest;
import com.sma.admin.tenant.internal.persistence.*;
import com.sma.admin.tenant.internal.service.*;

/** Run as the only integration context in a fresh guarded test database; other selected unit tests may coexist. */
@IntegrationTest
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="SMA_P10_EMPTY_BOOTSTRAP",matches="true")
@TestPropertySource(properties="sma.tenant.bootstrap-hosts=")
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class TenantDirectoryBootstrapTests {
    @Autowired TenantRecordMapper tenants;
    @Autowired DomainMapper domains;
    @Autowired PlatformTransactionManager manager;
    @Autowired JdbcTemplate jdbc;
    private TenantDirectoryBootstrap bootstrap(long id) {
        return new TenantDirectoryBootstrap(new TenantPlatformProperties(id,Set.of(),Set.of("localhost","127.0.0.1","[::1]")),tenants,domains);
    }
    private TransactionTemplate transaction() {
        var tx=new TransactionTemplate(manager);tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);tx.setTimeout(15);return tx;
    }
    private static void signal(CountDownLatch latch) {
        try { if(!latch.await(10,TimeUnit.SECONDS))throw new AssertionError("Bootstrap transaction rendezvous timed out"); }
        catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new AssertionError(interrupted);}
    }

    @Test void concurrentFirstStartSurvivesAnOlderSnapshotAndNeverOverwritesOperatorState() throws Exception {
        assertThat(tenants.selectCount(null)).as("Isolated empty directory prerequisite").isZero();
        assertThat(domains.selectCount(null)).isZero();
        var invalid=new TenantDirectoryBootstrap(new TenantPlatformProperties(1L,Set.of(),Set.of("example.test")),tenants,domains);
        assertThatThrownBy(invalid::initialize).isInstanceOf(IllegalArgumentException.class);
        assertThat(tenants.selectCount(null)).isZero();
        var snapshot=new CountDownLatch(1);var inserted=new CountDownLatch(1);
        var enterSecond=new CountDownLatch(1);var releaseFirst=new CountDownLatch(1);var attempting=new CountDownLatch(1);
        var pool=Executors.newFixedThreadPool(2,Thread.ofPlatform().inheritInheritableThreadLocals(false).factory());
        try {
            var second=pool.submit(()->transaction().executeWithoutResult(status->{
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tenant_tenant",Long.class)).isZero();
                snapshot.countDown();signal(enterSecond);attempting.countDown();bootstrap(1).initialize();
            }));
            signal(snapshot);
            var first=pool.submit(()->transaction().executeWithoutResult(status->{bootstrap(1).initialize();inserted.countDown();signal(releaseFirst);}));
            signal(inserted);enterSecond.countDown();signal(attempting);
            assertThatThrownBy(()->second.get(200,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            releaseFirst.countDown();first.get(10,TimeUnit.SECONDS);second.get(10,TimeUnit.SECONDS);
        } finally {
            enterSecond.countDown();releaseFirst.countDown();pool.shutdownNow();assertThat(pool.awaitTermination(10,TimeUnit.SECONDS)).isTrue();
        }
        assertThat(tenants.selectCount(null)).isOne();
        assertThat(domains.selectList(null)).hasSize(3).allSatisfy(row->{
            assertThat(row.getOwnerTenantId()).isEqualTo(1L);assertThat(row.getStatus()).isEqualTo("ACTIVE");
        });
        var tenant=tenants.selectById(1);var created=tenant.getCreatedAt();
        tenant.setName("Operator renamed");tenant.setStatus("SUSPENDED");tenant.setSecurityGeneration(9L);tenants.updateById(tenant);
        var domain=domains.selectOne(new LambdaQueryWrapper<DomainEntity>().eq(DomainEntity::getHostname,"localhost"));
        domain.setStatus("DISABLED");domains.updateById(domain);
        transaction().executeWithoutResult(status->bootstrap(1).initialize());
        var retained=tenants.selectById(1);
        assertThat(retained.getName()).isEqualTo("Operator renamed");assertThat(retained.getStatus()).isEqualTo("SUSPENDED");
        assertThat(retained.getSecurityGeneration()).isEqualTo(9L);assertThat(retained.getCreatedAt()).isEqualTo(created);
        assertThat(domains.selectById(domain.getId()).getStatus()).isEqualTo("DISABLED");
        // A uniqueness conflict on code cannot silently bless a different configured platform ID.
        assertThatThrownBy(()->transaction().executeWithoutResult(status->bootstrap(2).initialize())).isInstanceOf(IllegalStateException.class);
        assertThat(tenants.selectById(2)).isNull();
        domain.setOwnerTenantId(2L);domains.updateById(domain);
        try { assertThatThrownBy(()->transaction().executeWithoutResult(status->bootstrap(1).initialize())).isInstanceOf(IllegalStateException.class); }
        finally { domain.setOwnerTenantId(1L);domains.updateById(domain); }
        // A retired/soft-deleted row is not resurrected by ON DUPLICATE KEY UPDATE.
        tenants.deleteById(1);
        assertThatThrownBy(()->transaction().executeWithoutResult(status->bootstrap(1).initialize())).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT deleted FROM tenant_tenant WHERE id=1",Integer.class)).isEqualTo(1);
        assertThat(tenants.selectById(1)).isNull();
    }
}
