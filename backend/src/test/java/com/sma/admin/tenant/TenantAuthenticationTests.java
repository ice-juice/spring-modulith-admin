package com.sma.admin.tenant;

import static org.assertj.core.api.Assertions.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import com.sma.admin.auth.internal.persistence.*;
import com.sma.admin.auth.internal.service.*;
import com.sma.admin.common.BizException;
import com.sma.admin.common.security.*;
import com.sma.admin.iam.AccountView;
import com.sma.admin.iam.internal.service.UserService;
import com.sma.admin.message.internal.service.SocketTicketService;
import com.sma.admin.support.*;
import com.sma.admin.tenant.internal.persistence.*;
import com.sma.admin.tenant.internal.service.TenantDirectoryService;

@IntegrationTest @AutoConfigureMockMvc
@TestPropertySource(properties={"sma.tenant.platform-admin-user-ids=991","spring.datasource.hikari.maximum-pool-size=4"})
class TenantAuthenticationTests {
    private static final String PASSWORD="Q7d4e10594";
    private static final SmaPrincipal OPERATOR=new SmaPrincipal(991,"platform",1,"admin",null,false,DataAccess.unrestricted(),Set.of(),"UTC");
    @Autowired TenantDirectoryService tenants;
    @Autowired TenantRecordMapper tenantRows;
    @Autowired DomainMapper domains;
    @Autowired UserService users;
    @Autowired CaptchaMapper captchas;
    @Autowired SessionMapper sessionRows;
    @Autowired SecurityChallenges challenges;
    @Autowired SessionService sessions;
    @Autowired TenantAuthenticationGuard guard;
    @Autowired SocketTicketService sockets;
    @Autowired PlatformTransactionManager transactions;
    @Autowired MockMvcTester mvc;
    @Autowired JsonMapper json;
    @Autowired javax.sql.DataSource dataSource;

    record Fixture(long tenant,String host,AccountView account) {}

    private Fixture fixture(String username) {
        return fixture(username,"admin");
    }
    private Fixture fixture(String username,String portal) {
        var tenant=Users.as(OPERATOR,() -> {
            String code="t"+UUID.randomUUID().toString().replace("-","");
            var created=tenants.create(new TenantDirectoryService.TenantInput(code,code));
            tenants.changeStatus(created.id(),new TenantDirectoryService.StatusInput("ACTIVE",1));
            var domain=tenants.registerDomain(new TenantDirectoryService.DomainInput(created.id(),code+".example.test",portal));
            var row=domains.selectById(domain.id());row.setStatus("ACTIVE");row.setVerificationStatus("VERIFIED");row.setTlsStatus("READY");row.setDnsStatus("READY");row.setVerifiedAt(java.time.Instant.now());row.setTlsValidUntil(java.time.Instant.now().plusSeconds(86400));domains.updateById(row);
            return domain;
        });
        var account=Users.as(new SmaPrincipal(991,"fixture",tenant.tenantId(),portal,null,false,DataAccess.unrestricted(),Set.of(),"UTC"),() -> {
            users.create(new UserService.Create(new UserService.Input(username,username,null,null,null,"UNSPECIFIED",true,Set.of(),Set.of(),"en-US","UTC"),PASSWORD));
            return users.authenticate(username,PASSWORD,portal).orElseThrow();
        });
        return new Fixture(tenant.tenantId(),tenant.hostname(),account);
    }
    private String captcha(Fixture fixture) {
        return TenantScope.within(fixture.tenant(),() -> {
            String id=UUID.randomUUID().toString();var row=new CaptchaEntity();
            row.setChallengeId(id);row.setAnswerHash(Tokens.hash(id+":ABCDE"));row.setExpiresAt(Instant.now().plusSeconds(120));
            row.setPortal(fixture.account().portal());row.setTenantSecurityGeneration(tenants.securityState(fixture.tenant()).orElseThrow().generation());
            captchas.insert(row);return id;
        });
    }
    private String body(Fixture fixture,String captcha) {
        return json.writeValueAsString(Map.of("username",fixture.account().username(),"password",PASSWORD,"portal",fixture.account().portal(),"captchaId",captcha,"captcha","ABCDE","remember",false));
    }
    private String login(Fixture fixture) throws Exception {
        var result=mvc.post().uri("/api/auth/login").header("Host",fixture.host()).contentType(MediaType.APPLICATION_JSON)
                .content(body(fixture,captcha(fixture))).exchange();
        assertThat(result).hasStatusOk().bodyJson().hasPathSatisfying("$.code",v -> v.assertThat().isEqualTo(0));
        return json.readTree(result.getResponse().getContentAsString()).path("data").path("token").asString();
    }

    @Test void sameUsernameAuthenticatesOnlyWithinItsHostAndForeignTokenOrCaptchaIsRejected() throws Exception {
        String username="shared"+UUID.randomUUID().toString().substring(0,8);
        var a=fixture(username);var b=fixture(username);
        String challengeA=captcha(a);
        assertThat(mvc.post().uri("/api/auth/login").header("Host",b.host()).contentType(MediaType.APPLICATION_JSON).content(body(b,challengeA)))
                .hasStatusOk().bodyJson().hasPathSatisfying("$.error",v -> v.assertThat().isEqualTo("auth.error.invalid-captcha"));
        String tokenA=login(a), tokenB=login(b);
        assertThat(mvc.get().uri("/api/auth/me").header("Host",a.host()).header("X-Tenant-ID",Long.toString(b.tenant())).header("Authorization","Bearer "+tokenA))
                .hasStatusOk().bodyJson().hasPathSatisfying("$.data.id",v -> v.assertThat().isEqualTo(a.account().id().toString()));
        assertThat(mvc.get().uri("/api/auth/me").header("Host",b.host()).header("Authorization","Bearer "+tokenA)).hasStatus(401);
        assertThat(mvc.get().uri("/api/auth/me").header("Host",b.host()).header("Authorization","Bearer "+tokenB))
                .hasStatusOk().bodyJson().hasPathSatisfying("$.data.id",v -> v.assertThat().isEqualTo(b.account().id().toString()));
        assertThat(TenantEntryScope.optional()).isEmpty();assertThat(TenantEntryScope.isHttpRequest()).isFalse();
    }

    @Test void suspensionAndResumeInvalidateSessionCaptchaMfaChallengeAndSocketTicket() throws Exception {
        var fixture=fixture("pause"+UUID.randomUUID().toString().substring(0,8));String token=login(fixture);
        String oldCaptcha=captcha(fixture);
        var session=TenantScope.within(fixture.tenant(),() -> sessions.resolve(token).orElseThrow());
        String mfa=Users.as(session.principal(),() -> new TransactionTemplate(transactions).execute(status -> {
            var row=new SecurityChallengeEntity();row.setUserId(fixture.account().id());row.setPortal("admin");row.setPurpose("LOGIN_TOTP");
            row.setSecurityVersion(0L);row.setAccountCredentialVersion(fixture.account().credentialVersion());
            row.setExpiresAt(Instant.now().plusSeconds(180));row.setSingleTerminal(false);row.setSessionTtlSeconds(28800L);return challenges.insert(row);
        }));
        var ticket=Users.as(session.principal(),() -> sockets.issue(session.session().getId()));
        Users.as(OPERATOR,() -> { tenants.changeStatus(fixture.tenant(),new TenantDirectoryService.StatusInput("SUSPENDED",2));return null; });
        assertThat(mvc.get().uri("/api/auth/me").header("Host",fixture.host()).header("Authorization","Bearer "+token)).hasStatus(404);
        Users.as(OPERATOR,() -> { tenants.changeStatus(fixture.tenant(),new TenantDirectoryService.StatusInput("ACTIVE",3));return null; });
        assertThat(mvc.get().uri("/api/auth/me").header("Host",fixture.host()).header("Authorization","Bearer "+token)).hasStatus(401);
        assertThat(mvc.post().uri("/api/auth/login").header("Host",fixture.host()).contentType(MediaType.APPLICATION_JSON).content(body(fixture,oldCaptcha)))
                .hasStatusOk().bodyJson().hasPathSatisfying("$.error",v -> v.assertThat().isEqualTo("auth.error.invalid-captcha"));
        TenantScope.within(fixture.tenant(),() -> {
            assertThatThrownBy(() -> challenges.reserve(mfa,"LOGIN_TOTP")).isInstanceOf(BizException.class);
            assertThat(sockets.consume(ticket.ticket())).isEmpty(); return null;
        });
        String fresh=login(fixture);
        assertThat(mvc.get().uri("/api/auth/me").header("Host",fixture.host()).header("Authorization","Bearer "+fresh)).hasStatusOk();
    }

    @Test void directoryGenerationIsReadFreshEvenInsideAnOlderBusinessTransaction() throws Exception {
        var fixture=fixture("snapshot"+UUID.randomUUID().toString().substring(0,8));
        var tx=new TransactionTemplate(transactions);
        TenantScope.within(fixture.tenant(),() -> tx.execute(status -> {
            assertThat(tenantRows.selectById(fixture.tenant()).getSecurityGeneration()).isEqualTo(2);
            try(var executor=java.util.concurrent.Executors.newSingleThreadExecutor()) {
                executor.submit(() -> Users.as(OPERATOR,() -> tenants.changeStatus(fixture.tenant(),new TenantDirectoryService.StatusInput("SUSPENDED",2))))
                        .get(15,java.util.concurrent.TimeUnit.SECONDS);
            } catch(Exception failure) { throw new AssertionError(failure); }
            assertThat(guard.active(fixture.tenant(),"admin",2L)).isFalse();return null;
        }));
    }

    @Test void unknownHostForwardedSpoofAndPortalMismatchNeverReachAnotherTenant() throws Exception {
        var fixture=fixture("portal"+UUID.randomUUID().toString().substring(0,8));
        assertThat(mvc.get().uri("/api/auth/captcha").header("Host","unknown.example.test").header("X-Forwarded-Host",fixture.host())).hasStatus(404);
        var request=Map.of("username",fixture.account().username(),"password",PASSWORD,"portal","other","captchaId",captcha(fixture),"captcha","ABCDE","remember",false);
        assertThat(mvc.post().uri("/api/auth/login").header("Host",fixture.host()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request)))
                .hasStatusOk().bodyJson().hasPathSatisfying("$.error",value -> value.assertThat().isEqualTo("auth.error.invalid-credentials")).doesNotHavePath("$.data.token");
        assertThat(mvc.get().uri("/actuator/health").header("Host","unknown.example.test")).hasStatusOk();
    }

    @Test void securityGenerationReadDoesNotBorrowAnotherConnectionWhenAllPoolSlotsAreInTransactions() throws Exception {
        var fixture=fixture("pool"+UUID.randomUUID().toString().substring(0,8));
        int slots=dataSource.unwrap(com.zaxxer.hikari.HikariDataSource.class).getMaximumPoolSize();
        assertThat(slots).isEqualTo(4);
        var ready=new java.util.concurrent.CountDownLatch(slots);
        var start=new java.util.concurrent.CountDownLatch(1);
        try(var executor=java.util.concurrent.Executors.newFixedThreadPool(slots)) {
            var tasks=new java.util.ArrayList<java.util.concurrent.Future<Boolean>>();
            for(int i=0;i<slots;i++) tasks.add(executor.submit(() -> TenantScope.within(fixture.tenant(),() ->
                new TransactionTemplate(transactions).execute(status -> {
                    ready.countDown();
                    try { if(!start.await(15,java.util.concurrent.TimeUnit.SECONDS)) throw new AssertionError("Transaction gate timeout"); }
                    catch(InterruptedException failure) { Thread.currentThread().interrupt(); throw new AssertionError(failure); }
                    return guard.active(fixture.tenant(),"admin",2L);
                }))));
            try {
                assertThat(ready.await(15,java.util.concurrent.TimeUnit.SECONDS)).isTrue();start.countDown();
                for(var task:tasks) assertThat(task.get(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            } finally { start.countDown();for(var task:tasks) task.cancel(true); }
        }
    }

    @Test void legacySessionWithoutTenantGenerationMustAuthenticateAgain() throws Exception {
        var fixture=fixture("legacy"+UUID.randomUUID().toString().substring(0,8));String token=login(fixture);
        TenantScope.within(fixture.tenant(),() -> {
            var session=sessions.resolve(token).orElseThrow().session();
            sessionRows.update(null,new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<SessionEntity>()
                    .eq(SessionEntity::getId,session.getId()).set(SessionEntity::getTenantSecurityGeneration,null));
            return null;
        });
        assertThat(mvc.get().uri("/api/auth/me").header("Host",fixture.host()).header("Authorization","Bearer "+token)).hasStatus(401);
        assertThat(login(fixture)).isNotBlank();
    }

    @Test void nonAdminPortalWorksOnlyOnItsOwnEntryAndRemainsValidForTrustedBackgroundChecks() throws Exception {
        var fixture=fixture("partner"+UUID.randomUUID().toString().substring(0,8),"partner");String token=login(fixture);
        assertThat(TenantScope.within(fixture.tenant(),() -> sessions.resolve(token))).isPresent();
        assertThat(mvc.get().uri("/api/auth/me").header("Host",fixture.host()).header("Authorization","Bearer "+token))
                .hasStatusOk().bodyJson().hasPathSatisfying("$.data.portal",v -> v.assertThat().isEqualTo("partner"));
        String adminHost="admin-"+fixture.host();
        Users.as(OPERATOR,() -> {
            var domain=tenants.registerDomain(new TenantDirectoryService.DomainInput(fixture.tenant(),adminHost,"admin"));
            var row=domains.selectById(domain.id());row.setStatus("ACTIVE");row.setVerificationStatus("VERIFIED");row.setTlsStatus("READY");row.setDnsStatus("READY");row.setVerifiedAt(java.time.Instant.now());row.setTlsValidUntil(java.time.Instant.now().plusSeconds(86400));domains.updateById(row);return null;
        });
        assertThat(mvc.get().uri("/api/auth/me").header("Host",adminHost).header("Authorization","Bearer "+token)).hasStatus(401);
    }
}
