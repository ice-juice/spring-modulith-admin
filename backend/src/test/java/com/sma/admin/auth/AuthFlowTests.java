package com.sma.admin.auth;

import static org.assertj.core.api.Assertions.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import tools.jackson.databind.json.JsonMapper;
import com.sma.admin.auth.internal.persistence.*;
import com.sma.admin.auth.internal.service.Tokens;
import com.sma.admin.common.security.DataScope;
import com.sma.admin.iam.internal.service.*;
import com.sma.admin.support.IntegrationTest;

@IntegrationTest @AutoConfigureMockMvc
class AuthFlowTests {
    @Autowired MockMvcTester mvc;
    @Autowired JsonMapper json;
    @Autowired CaptchaMapper captchas;
    @Autowired SessionMapper sessions;
    @Autowired UserService users;
    @Autowired RoleService roles;
    @Autowired RolePermissionService grants;
    @Autowired PermissionService permissions;
    @Autowired com.sma.admin.auth.internal.service.SessionIssuer issuer;
    @Autowired com.sma.admin.auth.internal.service.SessionService sessionService;

    @Test
    void bearerLoginReloadsPermissionsThenLogoutInvalidatesToken() throws Exception {
        var role=roles.save(null,new RoleService.Input(0L,"Login role","login-role",0,true,DataScope.SELF,Set.of(),null));
        var input=new UserService.Input("login-tester","Login Tester",null,null,null,"UNSPECIFIED",true,Set.of(role.id()),Set.of(),"en-US","America/New_York");
        users.create(new UserService.Create(input,"Q7d4e10594"));
        String challenge=fixtureChallenge("ABCDE");
        var login=mvc.post().uri("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginBody("login-tester",challenge,"ABCDE")).exchange();
        assertThat(login).hasStatusOk().bodyJson().hasPathSatisfying("$.code",v -> v.assertThat().isEqualTo(0));
        String token=json.readTree(login.getResponse().getContentAsString()).path("data").path("token").asString();
        assertThat(token).hasSize(43);
        assertThat(sessions.selectList(null)).anySatisfy(row -> {
            assertThat(row.getTokenHash()).isEqualTo(Tokens.hash(token)); assertThat(row.getTokenHash()).isNotEqualTo(token);
        });
        assertThat(mvc.get().uri("/api/auth/me").header("Authorization","Bearer "+token)).hasStatusOk().bodyJson()
                .hasPathSatisfying("$.data.username",v -> v.assertThat().isEqualTo("login-tester")).doesNotHavePath("$.data.passwordHash");
        assertThat(mvc.get().uri("/api/iam/departments").header("Authorization","Bearer "+token)).hasStatus(403);
        var permission=permissions.list().stream().filter(row -> row.permissionKey().equals("iam:department:view")).findFirst().orElseThrow();
        grants.replace(role.id(),Set.of(permission.id()));
        assertThat(mvc.get().uri("/api/iam/departments").header("Authorization","Bearer "+token)).hasStatusOk();
        assertThat(mvc.post().uri("/api/auth/logout").header("Authorization","Bearer "+token)).hasStatusOk();
        assertThat(mvc.get().uri("/api/auth/me").header("Authorization","Bearer "+token)).hasStatus(401);
        assertThat(mvc.post().uri("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginBody("login-tester",challenge,"ABCDE")))
                .hasStatusOk().bodyJson().hasPathSatisfying("$.error",v -> v.assertThat().isEqualTo("auth.error.invalid-captcha"));
    }
    @Test
    void concurrentSingleTerminalLoginsLeaveOneSessionAndExpiredSessionsAreDenied() throws Exception {
        var input=new UserService.Input("single-tester","Single Tester",null,null,null,"UNSPECIFIED",true,Set.of(),Set.of(),null,"UTC");
        users.create(new UserService.Create(input,"Q7d4e10594"));
        var account=users.authenticate("single-tester","Q7d4e10594","admin").orElseThrow();
        try(var executor=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var one=executor.submit(() -> issuer.issue(account,true,java.time.Duration.ofHours(8),"127.0.0.1","test-one"));
            var two=executor.submit(() -> issuer.issue(account,true,java.time.Duration.ofHours(8),"127.0.0.1","test-two"));
            var first=one.get(10,java.util.concurrent.TimeUnit.SECONDS);
            var second=two.get(10,java.util.concurrent.TimeUnit.SECONDS);
            long active=java.util.stream.Stream.of(first,second).filter(result -> sessionService.resolve(result.token()).isPresent()).count();
            assertThat(active).isEqualTo(1);
        }
        var live=sessions.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SessionEntity>().eq(SessionEntity::getUserId,account.id()));
        assertThat(live).hasSize(1);
        var expired=issuer.issue(account,true,java.time.Duration.ofSeconds(-1),"127.0.0.1","expiry-fixture");
        assertThat(sessionService.resolve(expired.token())).isEmpty();
    }
    @Test
    void passwordChangesInvalidateEvenSessionsIssuedFromAnEarlierAuthenticationSnapshot() {
        var input=new UserService.Input("version-tester","Version Tester",null,null,null,"UNSPECIFIED",true,Set.of(),Set.of(),null,"UTC");
        var created=users.create(new UserService.Create(input,"Q7d4e10594"));
        var oldSnapshot=users.authenticate("version-tester","Q7d4e10594","admin").orElseThrow();
        var previous=issuer.issue(oldSnapshot,false,java.time.Duration.ofHours(8),"127.0.0.1","existing-session-fixture");
        users.resetPassword(created.id(),"Q7fc1401e1");
        var stale=issuer.issue(oldSnapshot,false,java.time.Duration.ofHours(8),"127.0.0.1","stale-login-fixture");
        assertThat(sessionService.resolve(stale.token())).isEmpty();
        var current=users.authenticate("version-tester","Q7fc1401e1","admin").orElseThrow();
        var good=issuer.issue(current,false,java.time.Duration.ofHours(8),"127.0.0.1","current-fixture");
        assertThat(sessionService.resolve(good.token())).isPresent();
        org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(5)).untilAsserted(() ->
            assertThat(sessions.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SessionEntity>()
                    .eq(SessionEntity::getTokenHash,Tokens.hash(previous.token())))).isEmpty());
        assertThat(sessionService.resolve(good.token())).isPresent();
    }
    @Test
    void expiredAndForgedTokensAreRejectedAndCaptchaErrorsAreLocalized() {
        assertThat(mvc.get().uri("/api/auth/me").header("Authorization","Bearer "+"A".repeat(43))).hasStatus(401);
        assertThat(mvc.get().uri("/api/auth/captcha")).hasStatusOk().bodyJson()
                .hasPathSatisfying("$.data.image",v -> v.assertThat().asString().startsWith("data:image/png;base64,"));
        assertThat(mvc.post().uri("/api/auth/login").header("Accept-Language","en-US").contentType(MediaType.APPLICATION_JSON)
                .content(loginBody("nobody","not-a-challenge","wrong"))).hasStatusOk().bodyJson()
                .hasPathSatisfying("$.message",v -> v.assertThat().isEqualTo("The verification code is incorrect or expired"));
    }
    private String fixtureChallenge(String answer) {
        String id=UUID.randomUUID().toString();
        var row=new CaptchaEntity(); row.setChallengeId(id); row.setAnswerHash(Tokens.hash(id+":"+answer)); row.setExpiresAt(Instant.now().plusSeconds(120));row.setPortal("admin");row.setTenantSecurityGeneration(1L); captchas.insert(row);
        return id;
    }
    private String loginBody(String username,String id,String answer) {
        return "{\"username\":\""+username+"\",\"password\":\"Q7d4e10594\",\"portal\":\"admin\",\"captchaId\":\""+id+"\",\"captcha\":\""+answer+"\",\"remember\":false}";
    }
}
