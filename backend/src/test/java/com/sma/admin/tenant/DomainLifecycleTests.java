package com.sma.admin.tenant;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.transaction.PlatformTransactionManager;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sma.admin.common.*;
import com.sma.admin.common.security.*;
import com.sma.admin.support.*;
import com.sma.admin.tenant.internal.persistence.*;
import com.sma.admin.tenant.internal.service.*;

@IntegrationTest
@TestPropertySource(properties={"sma.tenant.platform-admin-user-ids=991,992","sma.tenant.domain-verification.ingress-addresses=127.0.0.1"})
class DomainLifecycleTests {
    @Autowired TenantDirectoryService directory;
    @Autowired DomainLifecycleService lifecycle;
    @Autowired DomainVerificationService verification;
    @Autowired DomainMapper domains;
    @Autowired DomainCheckMapper checks;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean DomainProbe probe;
    private static final Set<String> PERMISSIONS=Set.of("tenant:tenant:view","tenant:tenant:create","tenant:tenant:update","tenant:domain:view","tenant:domain:create","tenant:domain:verify","tenant:domain:update");
    private static final SmaPrincipal OPERATOR=actor(991,1,PERMISSIONS);
    private static SmaPrincipal actor(long user,long tenant,Set<String> permissions) {return new SmaPrincipal(user,"domain-operator",tenant,"admin",null,false,DataAccess.unrestricted(),permissions,"UTC");}
    private static String operation() {return UUID.randomUUID().toString();}
    private static DomainProbe.Result good() {return new DomainProbe.Result(true,true,true,Instant.now().plusSeconds(86400),List.of("127.0.0.1"),List.of("a".repeat(64)),DomainProbe.Failure.NONE);}
    @BeforeEach void network() {
        when(probe.verify(anyString(),anyString())).thenAnswer(invocation->{assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();return good();});
    }
    private long tenant() {String code="domain-"+operation().substring(0,8);var tenant=directory.create(new TenantDirectoryService.TenantInput(code,code));return directory.changeStatus(tenant.id(),new TenantDirectoryService.StatusInput("ACTIVE",1)).id();}
    private TenantDirectoryService.DomainView domain(long tenant) {return directory.registerDomain(new TenantDirectoryService.DomainInput(tenant,"d-"+operation().substring(0,8)+".example.test","admin"));}
    private TenantDirectoryService.DomainView checked(long tenant) {
        var domain=domain(tenant);var challenged=lifecycle.challenge(domain.id(),domain.version());
        assertThat(verification.verify(domain.id(),challenged.version(),operation()).status()).isEqualTo("SUCCEEDED");return lifecycle.get(domain.id());
    }
    private TenantDirectoryService.DomainView active(long tenant) {var domain=checked(tenant);return lifecycle.enabled(domain.id(),domain.version(),true);}

    @Test void verificationDoesNotActivateAndReceiptsAreOwnedVersionBoundAndNotReexecuted() {
        Users.as(OPERATOR,()->{
            long tenant=tenant();var domain=domain(tenant);
            assertThatThrownBy(()->lifecycle.enabled(domain.id(),domain.version(),true)).isInstanceOf(BizException.class);
            var challenged=lifecycle.challenge(domain.id(),domain.version());String operation=operation();
            var result=verification.verify(domain.id(),challenged.version(),operation);
            assertThat(result.status()).isEqualTo("SUCCEEDED");assertThat(directory.resolve(domain.hostname())).isEmpty();
            assertThat(lifecycle.get(domain.id()).status()).isEqualTo("PENDING");
            assertThat(verification.verify(domain.id(),challenged.version(),operation)).isEqualTo(result);
            verify(probe,times(1)).verify(anyString(),anyString());
            assertThatThrownBy(()->verification.verify(domain.id(),challenged.version()+1,operation)).isInstanceOf(BizException.class);
            Users.as(actor(992,1,PERMISSIONS),()->{assertThatThrownBy(()->lifecycle.check(domain.id(),operation)).isInstanceOf(AccessDeniedException.class);return null;});
            Users.as(actor(991,tenant,PERMISSIONS),()->{assertThatThrownBy(()->lifecycle.get(domain.id())).isInstanceOf(AccessDeniedException.class);return null;});
            Users.as(actor(991,1,Set.of("tenant:domain:view")),()->{assertThatThrownBy(()->lifecycle.challenge(domain.id(),challenged.version())).isInstanceOf(AccessDeniedException.class);return null;});
            var current=lifecycle.get(domain.id());var activated=lifecycle.enabled(domain.id(),current.version(),true);
            assertThat(activated.primaryDomain()).isTrue();assertThat(directory.resolve(domain.hostname())).isPresent();
            return null;
        });
    }
    @Test void disablingSupersedesInFlightWorkAndRotatingAChallengeCannotReuseAnOldActivationProof() {
        Users.as(OPERATOR,()->{
            long tenant=tenant();var active=active(tenant);long generation=directory.resolve(active.hostname()).orElseThrow().securityGeneration();
            var started=lifecycle.begin(active.id(),active.version(),operation());
            var disabled=lifecycle.enabled(active.id(),lifecycle.get(active.id()).version(),false);
            assertThat(lifecycle.finish(started.work(),good()).status()).isEqualTo("SUPERSEDED");
            assertThat(directory.resolve(active.hostname())).isEmpty();assertThat(directory.securityState(tenant).orElseThrow().generation()).isEqualTo(generation+1);
            var rotated=lifecycle.challenge(active.id(),disabled.version());
            assertThat(rotated.activationReady()).isFalse();assertThat(rotated.checkStatus()).isEqualTo("IDLE");assertThat(rotated.lastCheckId()).isNull();
            assertThatThrownBy(()->lifecycle.enabled(active.id(),rotated.version(),true)).isInstanceOf(BizException.class);
            assertThat(verification.verify(active.id(),rotated.version(),operation()).status()).isEqualTo("SUCCEEDED");
            var restored=lifecycle.enabled(active.id(),lifecycle.get(active.id()).version(),true);
            assertThat(restored.status()).isEqualTo("ACTIVE");assertThat(directory.resolve(active.hostname()).orElseThrow().securityGeneration()).isEqualTo(generation+1);
            return null;
        });
    }
    @Test void expiredChallengesLeasesAndCertificatesNeverEnableAnEntry() {
        Users.as(OPERATOR,()->{
            long tenant=tenant();var domain=domain(tenant);var challenged=lifecycle.challenge(domain.id(),domain.version());
            var row=domains.selectById(domain.id());row.setChallengeExpiresAt(Instant.now().minusSeconds(1));domains.updateById(row);
            assertThatThrownBy(()->verification.verify(domain.id(),challenged.version(),operation())).isInstanceOf(BizException.class);
            var renewed=lifecycle.challenge(domain.id(),challenged.version());
            var started=lifecycle.begin(domain.id(),renewed.version(),operation());
            var receipt=checks.selectById(started.work().checkId());receipt.setLeaseUntil(Instant.now().minusSeconds(1));checks.updateById(receipt);
            assertThat(lifecycle.finish(started.work(),good()).status()).isEqualTo("FAILED");assertThat(directory.resolve(domain.hostname())).isEmpty();
            var checked=checked(tenant);var active=lifecycle.enabled(checked.id(),checked.version(),true);
            var expired=domains.selectById(active.id());expired.setTlsValidUntil(Instant.now().minusSeconds(1));domains.updateById(expired);
            assertThat(lifecycle.get(active.id()).tlsStatus()).isEqualTo("EXPIRED");assertThat(directory.resolve(active.hostname())).isEmpty();
            assertThatThrownBy(()->lifecycle.enabled(active.id(),active.version(),true)).isInstanceOf(BizException.class);return null;
        });
    }
    @Test void parallelCheckCallsHaveOneNetworkWorkAndNoDatabaseTransactionDuringTheProbe() throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        when(probe.verify(anyString(),anyString())).thenAnswer(invocation->{assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();entered.countDown();if(!release.await(20,TimeUnit.SECONDS))throw new IllegalStateException("Fixture timeout");return good();});
        var domain=Users.as(OPERATOR,()->{var created=domain(tenant());return lifecycle.challenge(created.id(),created.version());});String operation=operation();
        try(var executor=Executors.newSingleThreadExecutor()) {
            try {
                var first=executor.submit(()->Users.as(OPERATOR,()->verification.verify(domain.id(),domain.version(),operation)));
                assertThat(entered.await(10,TimeUnit.SECONDS)).isTrue();
                Users.as(OPERATOR,()->{
                    assertThat(lifecycle.check(domain.id(),operation).status()).isEqualTo("RUNNING");
                    assertThat(verification.verify(domain.id(),domain.version(),operation).status()).isEqualTo("RUNNING");
                    assertThatThrownBy(()->verification.verify(domain.id(),lifecycle.get(domain.id()).version(),operation())).isInstanceOf(BizException.class);
                    return null;
                });
                release.countDown();assertThat(first.get(30,TimeUnit.SECONDS).status()).isEqualTo("SUCCEEDED");
                verify(probe,times(1)).verify(anyString(),anyString());
                Users.as(OPERATOR,()->{assertThat(checks.selectCount(new LambdaQueryWrapper<DomainCheckEntity>().eq(DomainCheckEntity::getDomainId,domain.id()))).isEqualTo(1);return null;});
            } finally {release.countDown();}
        }
    }
    @Test void anOlderTransactionCannotRestoreAnOldPrimaryOrOverwriteACompletedReceipt() throws Exception {
        Users.as(OPERATOR,()->{
            long tenant=tenant();var a=active(tenant);var b=active(tenant);var c=active(tenant);
            var started=lifecycle.begin(c.id(),lifecycle.get(c.id()).version(),operation());
            new TransactionTemplate(transactions).execute(status->{
                directory.domains(tenant); // Establish a REPEATABLE_READ snapshot before the other operator commits.
                try(var executor=Executors.newSingleThreadExecutor()) {
                    executor.submit(()->Users.as(actor(991,1,PERMISSIONS),()->{lifecycle.finish(started.work(),good());var fresh=lifecycle.get(c.id());lifecycle.primary(c.id(),fresh.version());return null;})).get(30,TimeUnit.SECONDS);
                } catch(Exception failure) {throw new IllegalStateException(failure);}
                lifecycle.primary(b.id(),b.version());return null;
            });
            assertThat(directory.domains(tenant).stream().filter(TenantDirectoryService.DomainView::primaryDomain).map(TenantDirectoryService.DomainView::id)).containsExactly(b.id());
            assertThat(lifecycle.check(c.id(),started.result().operationId()).status()).isEqualTo("SUCCEEDED");
            assertThat(lifecycle.get(a.id()).primaryDomain()).isFalse();return null;
        });
    }
    @Test @Transactional void theLastUsablePlatformAdminEntryIsProtected() {
        Users.as(OPERATOR,()->{
            var available=directory.domains(1).stream().filter(row->row.portal().equals("admin") && row.status().equals("ACTIVE")).toList();
            assertThat(available).isNotEmpty();
            for(int i=0;i<available.size()-1;i++){var row=lifecycle.get(available.get(i).id());lifecycle.enabled(row.id(),row.version(),false);}
            var last=lifecycle.get(available.getLast().id());
            assertThatThrownBy(()->lifecycle.enabled(last.id(),last.version(),false)).isInstanceOfSatisfying(BizException.class,failure->assertThat(failure.getErrorCode()).isEqualTo(TenantErrorCode.DOMAIN_LAST_ENTRY));
            assertThat(directory.resolve(last.hostname())).isPresent();return null;
        });
    }
}
