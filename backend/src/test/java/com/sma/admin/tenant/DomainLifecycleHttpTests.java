package com.sma.admin.tenant;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;
import com.sma.admin.common.security.*;
import com.sma.admin.support.*;
import com.sma.admin.tenant.internal.service.*;

@IntegrationTest @AutoConfigureMockMvc
@TestPropertySource(properties={"sma.tenant.platform-admin-user-ids=991","sma.tenant.domain-verification.ingress-addresses=127.0.0.1",
        "sma.security.requests.fingerprint-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=","sma.security.requests.minimum-write-interval=100ms"})
class DomainLifecycleHttpTests {
    @Autowired MockMvc mvc;@Autowired TenantDirectoryService directory;@Autowired JsonMapper json;
    @MockitoBean DomainProbe probe;
    private static final Set<String> PERMISSIONS=Set.of("tenant:domain:view","tenant:domain:create","tenant:domain:verify","tenant:domain:update");
    private SmaPrincipal actor(long id,Set<String> permissions){return new SmaPrincipal(id,"operator",1,"admin",null,false,DataAccess.unrestricted(),permissions,"UTC");}
    @BeforeEach void network(){when(probe.verify(anyString(),anyString())).thenAnswer(invocation->{assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();return new DomainProbe.Result(true,true,true,Instant.now().plusSeconds(86400),List.of("127.0.0.1"),List.of("b".repeat(64)),DomainProbe.Failure.NONE);});}
    @Test void platformIdentityAndPermissionsAreRequiredAndNetworkChecksUseNativeReceipts() throws Exception {
        var actor=actor(991,PERMISSIONS);
        var domain=Users.as(actor,()->{String code="http-domain-"+UUID.randomUUID().toString().substring(0,8);var tenant=directory.create(new TenantDirectoryService.TenantInput(code,code));directory.changeStatus(tenant.id(),new TenantDirectoryService.StatusInput("ACTIVE",1));return directory.registerDomain(new TenantDirectoryService.DomainInput(tenant.id(),code+".example.test","admin"));});
        assertThat(send(get("/api/tenant/domains/verification-settings"),actor(992,PERMISSIONS)).getStatus()).isEqualTo(403);
        assertThat(send(post("/api/tenant/domains/"+domain.id()+"/challenges").content("{\"expectedVersion\":1}"),actor(991,Set.of("tenant:domain:view"))).getStatus()).isEqualTo(403);
        var options=send(get("/api/tenant/domains/tenant-options"),actor(991,Set.of("tenant:domain:view")));
        assertThat(options.getStatus()).isEqualTo(200);assertThat(options.getContentAsString()).doesNotContain("securityGeneration");
        var challenge=send(post("/api/tenant/domains/"+domain.id()+"/challenges").header("Idempotency-Key",UUID.randomUUID().toString()).content("{\"expectedVersion\":1}"),actor);
        assertThat(challenge.getStatus()).as(challenge.getContentAsString()).isEqualTo(200);
        var challenged=json.readTree(challenge.getContentAsString()).path("data");assertThat(challenged.path("challengeRecord").asText()).startsWith("_sma-tenant.");
        String operation=UUID.randomUUID().toString();Thread.sleep(120);
        var checked=send(post("/api/tenant/domains/"+domain.id()+"/checks").content(json.writeValueAsString(Map.of("expectedVersion",challenged.path("version").asText(),"operationId",operation))),actor);
        assertThat(checked.getStatus()).as(checked.getContentAsString()).isEqualTo(200);assertThat(checked.getHeader("X-Request-Policy")).isEqualTo("NATIVE");
        assertThat(json.readTree(checked.getContentAsString()).at("/data/status").asText()).isEqualTo("SUCCEEDED");
        var fresh=send(get("/api/tenant/domains/"+domain.id()),actor);var view=json.readTree(fresh.getContentAsString()).path("data");assertThat(view.path("status").asText()).isEqualTo("PENDING");
        Thread.sleep(120);
        var activated=send(put("/api/tenant/domains/"+domain.id()+"/enabled").header("Idempotency-Key",UUID.randomUUID().toString()).content(json.writeValueAsString(Map.of("expectedVersion",view.path("version").asText(),"enabled",true))),actor);
        assertThat(activated.getStatus()).as(activated.getContentAsString()).isEqualTo(200);assertThat(json.readTree(activated.getContentAsString()).at("/data/status").asText()).isEqualTo("ACTIVE");
        assertThat(send(get("/api/tenant/domains/"+domain.id()+"/checks/"+operation),actor).getStatus()).isEqualTo(200);
    }
    private org.springframework.mock.web.MockHttpServletResponse send(MockHttpServletRequestBuilder request,SmaPrincipal actor)throws Exception {return mvc.perform(request.contentType("application/json").with(Users.request(actor))).andReturn().getResponse();}
}
