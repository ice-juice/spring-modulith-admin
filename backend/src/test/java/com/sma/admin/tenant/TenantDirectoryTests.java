package com.sma.admin.tenant;

import static org.assertj.core.api.Assertions.*;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import com.sma.admin.common.*;
import com.sma.admin.common.security.*;
import com.sma.admin.support.*;
import com.sma.admin.tenant.internal.persistence.*;
import com.sma.admin.tenant.internal.service.*;

@IntegrationTest @AutoConfigureMockMvc
@TestPropertySource(properties={"sma.tenant.platform-admin-user-ids=991", "sma.tenant.bootstrap-hosts=localhost,127.0.0.1,[::1]"})
class TenantDirectoryTests {
    @Autowired TenantDirectoryService service;
    @Autowired TenantDirectoryBootstrap bootstrap;
    @Autowired DomainMapper domains;
    @Autowired MockMvcTester mvc;
    private static final SmaPrincipal OPERATOR=new SmaPrincipal(991,"platform-operator",1,"admin",null,false,DataAccess.unrestricted(),
            Set.of("tenant:tenant:view","tenant:tenant:create","tenant:tenant:update","tenant:domain:view","tenant:domain:create"),"UTC");

    @Test void domainsAreGloballyUniqueAndRegistrationDoesNotActivateAnonymousEntry() {
        Users.as(OPERATOR, () -> {
            var a=service.create(new TenantDirectoryService.TenantInput(code(),"Tenant A"));
            var b=service.create(new TenantDirectoryService.TenantInput(code(),"Tenant B"));
            String host=code()+".example.com";
            var domain=service.registerDomain(new TenantDirectoryService.DomainInput(a.id(),host.toUpperCase()+".","admin"));
            assertThat(domain.hostname()).isEqualTo(host);
            assertThat(domain.status()).isEqualTo("PENDING");
            assertThat(service.resolve(host)).isEmpty();
            assertThatThrownBy(() -> service.registerDomain(new TenantDirectoryService.DomainInput(b.id(),host,"admin")))
                    .isInstanceOf(BizException.class);
            assertThat(service.domains(b.id())).isEmpty();
            assertThat(service.domains(a.id())).singleElement().extracting(TenantDirectoryService.DomainView::tenantId).isEqualTo(a.id());
            assertThatThrownBy(() -> service.registerDomain(new TenantDirectoryService.DomainInput(a.id(),"https://bad.example.com","admin")))
                    .isInstanceOf(BizException.class);
            return null;
        });
    }

    @Test void directoryResolutionChecksVerificationTlsTenantStateAndGeneration() {
        Users.as(OPERATOR, () -> {
            var tenant=service.create(new TenantDirectoryService.TenantInput(code(),"Lifecycle"));
            var active=service.changeStatus(tenant.id(),new TenantDirectoryService.StatusInput("ACTIVE",1));
            assertThat(active.securityGeneration()).isEqualTo(2);
            String host=code()+".example.com";
            var domain=service.registerDomain(new TenantDirectoryService.DomainInput(tenant.id(),host,"admin"));
            var row=domains.selectById(domain.id()); row.setStatus("ACTIVE"); row.setVerificationStatus("VERIFIED"); row.setTlsStatus("LOCAL"); domains.updateById(row);
            assertThat(service.resolve(host)).isEmpty(); // A non-loopback domain cannot claim the local-development exception.
            row.setTlsStatus("READY");row.setDnsStatus("READY");row.setVerifiedAt(java.time.Instant.now());row.setTlsValidUntil(java.time.Instant.now().plusSeconds(86400));domains.updateById(row);
            assertThat(service.resolve(host)).get().satisfies(entry -> {
                assertThat(entry.tenantId()).isEqualTo(tenant.id()); assertThat(entry.securityGeneration()).isEqualTo(2);
            });
            service.changeStatus(tenant.id(),new TenantDirectoryService.StatusInput("SUSPENDED",2));
            assertThat(service.resolve(host)).isEmpty();
            assertThat(service.securityState(tenant.id())).get().extracting(TenantEntryQuery.SecurityState::acceptsBusiness).isEqualTo(false);
            assertThatThrownBy(() -> service.changeStatus(tenant.id(),new TenantDirectoryService.StatusInput("ACTIVE",2))).isInstanceOf(BizException.class);
            service.changeStatus(tenant.id(),new TenantDirectoryService.StatusInput("ACTIVE",3));
            assertThat(service.resolve(host)).get().extracting(TenantEntry::securityGeneration).isEqualTo(4L);
            assertThat(service.resolve("unknown.example.com")).isEmpty();
            assertThat(service.securityState(Long.MAX_VALUE)).isEmpty();
            return null;
        });
    }

    @Test void explicitDevelopmentBootstrapIsIdempotentAndDoesNotReactivateDisabledDomains() {
        var first=service.resolve("localhost").orElseThrow();
        assertThat(first.tenantId()).isEqualTo(1);
        bootstrap.initialize(); assertThat(service.resolve("localhost")).contains(first);
        var row=domains.selectById(first.domainId()); row.setStatus("DISABLED"); domains.updateById(row);
        try { bootstrap.initialize(); assertThat(service.resolve("localhost")).isEmpty(); }
        finally { row.setStatus("ACTIVE"); domains.updateById(row); }
    }

    @Test void httpAndServiceRequireIndependentPlatformAuthorityAsWellAsOperationPermission() {
        assertThat(mvc.get().uri("/api/tenant/tenants")).hasStatus(401);
        var foreign=new SmaPrincipal(991,"tenant-admin",77,"admin",null,true,DataAccess.unrestricted(),OPERATOR.permissions(),"UTC");
        Users.as(foreign, () -> {
            assertThatThrownBy(() -> service.page(new PageQuery(1,20,null,null),null)).isInstanceOf(AccessDeniedException.class);
            assertThat(mvc.get().uri("/api/tenant/tenants")).hasStatus(403);
            return null;
        });
        Users.as(Users.principal(991,1,DataAccess.unrestricted()), () -> {
            assertThat(mvc.get().uri("/api/tenant/tenants")).hasStatus(403); return null;
        });
        Users.as(OPERATOR, () -> {
            assertThatThrownBy(() -> service.changeStatus(1,new TenantDirectoryService.StatusInput("SUSPENDED",1))).isInstanceOf(BizException.class);
            // MockMvc clears the request security context on completion; direct service assertions precede it.
            assertThat(mvc.get().uri("/api/tenant/tenants")).hasStatusOk().bodyJson().hasPath("$.data.items");
            return null;
        });
    }

    @Test void concurrentStatusChangesUseOneGenerationAndRejectTheStaleWriter() throws Exception {
        long id=Users.as(OPERATOR, () -> {
            var tenant=service.create(new TenantDirectoryService.TenantInput(code(),"Concurrent lifecycle"));
            return service.changeStatus(tenant.id(),new TenantDirectoryService.StatusInput("ACTIVE",1)).id();
        });
        var start=new java.util.concurrent.CountDownLatch(1);
        var successes=new java.util.concurrent.atomic.AtomicInteger();
        var rejected=new java.util.concurrent.atomic.AtomicInteger();
        try(var executor=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var tasks=java.util.stream.Stream.of("SUSPENDED","MAINTENANCE").map(status -> executor.submit(() -> {
                start.await();
                return Users.as(OPERATOR, () -> {
                    try { service.changeStatus(id,new TenantDirectoryService.StatusInput(status,2)); successes.incrementAndGet(); }
                    catch(BizException failure) { assertThat(failure.getErrorCode()).isEqualTo(TenantErrorCode.STALE); rejected.incrementAndGet(); }
                    return null;
                });
            })).toList();
            start.countDown();
            for(var task:tasks) task.get(20,java.util.concurrent.TimeUnit.SECONDS);
        }
        assertThat(successes.get()).isEqualTo(1); assertThat(rejected.get()).isEqualTo(1);
        assertThat(service.securityState(id)).get().extracting(TenantEntryQuery.SecurityState::generation).isEqualTo(3L);
    }
    private static String code() { return "t"+UUID.randomUUID().toString().replace("-",""); }
}
