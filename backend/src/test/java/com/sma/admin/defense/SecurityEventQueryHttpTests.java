package com.sma.admin.defense;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import com.sma.admin.common.security.*;
import com.sma.admin.defense.internal.service.SecurityEventStore;
import com.sma.admin.support.*;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest @AutoConfigureMockMvc @SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties={"server.address=127.0.0.1","sma.tenant.platform-admin-user-ids=991"})
class SecurityEventQueryHttpTests {
    @Autowired MockMvc mvc; @Autowired SecurityEventStore store; @Autowired SecuritySignalCapture capture;
    @Autowired JsonMapper json; @Autowired DataSource datasource; @LocalServerPort int port;
    private static SecuritySignal a,second,b,platform;
    @BeforeAll static void resetSeed(){a=second=b=platform=null;}
    private static final Set<String> BOTH=Set.of("defense:event:view","defense:platform:view");
    private SmaPrincipal actor(long tenant,long id,boolean superAdmin,Set<String> permissions){
        return new SmaPrincipal(id,"event-query",tenant,"admin",null,superAdmin,DataAccess.unrestricted(),permissions,"UTC");
    }
    private SmaPrincipal viewer(long tenant){return actor(tenant,991,true,BOTH);}
    private <T>T host(Supplier<T> work)throws Exception{
        try(var pool=Executors.newSingleThreadExecutor(Thread.ofPlatform().inheritInheritableThreadLocals(false).factory())){
            return pool.submit(work::get).get(30,TimeUnit.SECONDS);
        }
    }
    @BeforeEach void seed()throws Exception{
        if(a!=null)return;
        a=Users.as(viewer(9201),()->capture.capture(SecuritySignal.Fact.AUTHORIZATION_REJECTED));
        second=Users.as(viewer(9201),()->capture.capture(SecuritySignal.Fact.REQUEST_BUDGET_REJECTED));
        b=Users.as(viewer(9202),()->capture.capture(SecuritySignal.Fact.AUTHENTICATION_REJECTED));
        platform=host(()->capture.capture(SecuritySignal.Fact.SIGNATURE_REPLAY_REJECTED));
        for(var event:List.of(a,second,b,platform))assertThat(host(()->store.append(event))).isEqualTo(SecurityEventStore.Result.STORED);
    }
    @Test void tenantListAndDetailUseCurrentTenantAndHaveFixedNonCacheableProjection()throws Exception{
        var response=mvc.perform(get("/api/defense/events").param("tenantId","9202").with(Users.request(viewer(9201))))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andReturn().getResponse();
        var rows=json.readTree(response.getContentAsString()).path("data");assertThat(rows.size()).isEqualTo(2);
        assertThat(response.getContentAsString()).contains(a.eventId().toString(),second.eventId().toString()).doesNotContain(b.eventId().toString(),platform.eventId().toString());
        var detail=mvc.perform(get("/api/defense/events/"+a.eventId()).with(Users.request(viewer(9201))))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andReturn().getResponse();
        var value=json.readTree(detail.getContentAsString()).path("data");
        assertThat(value.propertyNames()).containsExactlyInAnyOrder("eventId","correlationId","occurredAt","receivedAt","expiresAt","fact","category","severity","outcome","assessment","ruleVersion","attribution","source","held");
        assertThat(value.path("attribution").path("tenantId").asString()).isEqualTo("9201");
        assertThat(value.path("fact").asString()).isEqualTo("AUTHORIZATION_REJECTED");
        assertThat(value.path("ruleVersion").asInt()).isEqualTo(1);
        mvc.perform(get("/api/defense/events/"+a.eventId()).param("includeMetadata","true").with(Users.request(viewer(9201))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.metadata.observer.processId").value(a.metadata().observer().processId().toString()));
        mvc.perform(get("/api/defense/events").param("includeMetadata","true").with(Users.request(viewer(9201))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].metadata.observer.nodeId").value(a.metadata().observer().nodeId()));
        mvc.perform(get("/api/defense/events").param("limit","1").with(Users.request(viewer(9201))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1));
    }
    @Test void crossTenantPlatformAndMissingIdsAreIndistinguishableAndCannotTraverse()throws Exception{
        for(UUID id:List.of(b.eventId(),platform.eventId(),UUID.randomUUID()))
            mvc.perform(get("/api/defense/events/"+id).with(Users.request(viewer(9201))))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.error").value("common.error.data-not-found"));
        mvc.perform(get("/api/defense/events").with(Users.request(viewer(1))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
        mvc.perform(get("/api/defense/platform-events/"+a.eventId()).with(Users.request(viewer(1))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.error").value("common.error.data-not-found"));
    }
    @Test void platformRequiresDeploymentOperatorAndExplicitPermissionEvenForSuperAdmin()throws Exception{
        mvc.perform(get("/api/defense/platform-events/"+platform.eventId()).with(Users.request(viewer(1))))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.data.attribution.scope").value("PLATFORM"));
        mvc.perform(get("/api/defense/platform-events").with(Users.request(viewer(1))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1));
        for(var denied:List.of(viewer(9201),actor(1,992,true,BOTH),actor(1,991,true,Set.of("defense:event:view"))))
            mvc.perform(get("/api/defense/platform-events").with(Users.request(denied))).andExpect(status().isForbidden());
        mvc.perform(get("/api/defense/events").with(Users.request(actor(9201,991,true,Set.of())))).andExpect(status().isForbidden());
        mvc.perform(get("/api/defense/events").with(Users.request(actor(9201,991,false,BOTH)))).andExpect(status().isOk());
    }
    @Test void malformedBoundsAndIdsAreRejectedWithoutChangingEvidence()throws Exception{
        var jdbc=new JdbcTemplate(datasource);
        var before=jdbc.queryForMap("SELECT payload,payload_hash,held,updated_at FROM defense_tenant_event WHERE event_id=?",a.eventId().toString());
        var tenantCount=jdbc.queryForObject("SELECT COUNT(*) FROM defense_tenant_event WHERE tenant_id IN (9201,9202)",Integer.class);
        var platformCount=jdbc.queryForObject("SELECT COUNT(*) FROM defense_platform_event",Integer.class);
        for(String path:List.of("/api/defense/events","/api/defense/platform-events")){
            var user=viewer(path.endsWith("platform-events")?1:9201);
            for(String limit:List.of("0","101","-1","nonnumeric","2147483648"))
                mvc.perform(get(path).param("limit",limit).with(Users.request(user))).andExpect(status().isBadRequest());
            mvc.perform(get(path+"/invalid-uuid").with(Users.request(user))).andExpect(status().isBadRequest());
            mvc.perform(get(path).param("limit","100").with(Users.request(user))).andExpect(status().isOk());
            mvc.perform(post(path).contentType("application/json").content("{}").with(Users.request(user))).andExpect(status().isMethodNotAllowed());
        }
        assertThat(jdbc.queryForMap("SELECT payload,payload_hash,held,updated_at FROM defense_tenant_event WHERE event_id=?",a.eventId().toString())).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM defense_tenant_event WHERE tenant_id IN (9201,9202)",Integer.class)).isEqualTo(tenantCount);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM defense_platform_event",Integer.class)).isEqualTo(platformCount);
    }
    @Test void anonymousRealHttpAndApplicationIdentitiesCannotUseAdminEvidenceRoutes()throws Exception{
        var client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        for(String path:List.of("/api/defense/events","/api/defense/platform-events")){
            var response=client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(10)).GET().build(),HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(401);assertThat(response.body()).doesNotContain(a.eventId().toString(),platform.eventId().toString());
            mvc.perform(get(path).with(Users.request(SmaPrincipal.forApplication(9201,"admin","query-machine")))).andExpect(status().isForbidden());
            var app=new AppUserPrincipal(920101,9201,1,1,"shop",1);
            var authentication=UsernamePasswordAuthenticationToken.authenticated(app,null,List.of(new SimpleGrantedAuthority("defense:event:view"),new SimpleGrantedAuthority("defense:platform:view")));
            mvc.perform(get(path).header("Host","t9201-admin.test").with(SecurityMockMvcRequestPostProcessors.authentication(authentication))).andExpect(status().isForbidden());
        }
    }
}
