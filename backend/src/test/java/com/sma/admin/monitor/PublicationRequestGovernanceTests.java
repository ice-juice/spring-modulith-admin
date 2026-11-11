package com.sma.admin.monitor;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.*;
import org.springframework.modulith.events.*;
import org.springframework.modulith.events.core.*;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.sma.admin.common.security.*;
import com.sma.admin.support.*;

@IntegrationTest @AutoConfigureMockMvc @Import(PublicationRequestGovernanceTests.Fixture.class)
@TestPropertySource(properties="sma.security.requests.minimum-write-interval=60s")
public class PublicationRequestGovernanceTests {
    public record ProbeEvent(long tenantId,UUID id) {}
    public static class ProbeListener {
        private final AtomicBoolean fail=new AtomicBoolean(true);
        private final Map<UUID,AtomicInteger> calls=new ConcurrentHashMap<>();
        public void allowSuccess(){fail.set(false);}
        public int attempts(UUID id){var value=calls.get(id);return value==null?0:value.get();}
        @ApplicationModuleListener public void handle(ProbeEvent event) {
            calls.computeIfAbsent(event.id(),id->new AtomicInteger()).incrementAndGet();
            if(fail.get())throw new IllegalStateException("Intentional retry fixture");
        }
    }
    @TestConfiguration(proxyBeanMethods=false)
    static class Fixture {@Bean ProbeListener publicationProbe(){return new ProbeListener();}}
    @Autowired ProbeListener listener;
    @Autowired ApplicationEventPublisher events;
    @Autowired PlatformTransactionManager transactions;
    @Autowired EventPublicationRepository publications;
    @Autowired MockMvc mvc;

    @Test void retryUsesFrameworkStateRejectsForeignEventsAndCoolsUuidAliases() {
        var own=new ProbeEvent(79520,UUID.randomUUID());var foreign=new ProbeEvent(79521,UUID.randomUUID());
        var actor=actor(79520);
        for(var event:List.of(own,foreign))Users.as(actor(event.tenantId()),()->{
            new TransactionTemplate(transactions).executeWithoutResult(status->events.publishEvent(event));return null;
        });
        org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(20)).untilAsserted(()->{
            assertThat(find(own.id(),EventPublication.Status.FAILED)).isPresent();
            assertThat(find(foreign.id(),EventPublication.Status.FAILED)).isPresent();
        });
        UUID ownPublication=find(own.id(),EventPublication.Status.FAILED).orElseThrow().getIdentifier();
        UUID foreignPublication=find(foreign.id(),EventPublication.Status.FAILED).orElseThrow().getIdentifier();
        var denied=send(actor,foreignPublication.toString());
        assertThat(body(denied)).contains("common.error.data-not-found");
        listener.allowSuccess();
        var accepted=send(actor,ownPublication.toString());
        assertThat(accepted.getResponse().getStatus()).as(body(accepted)).isEqualTo(200);
        assertThat(accepted.getResponse().getHeader("X-Request-Policy")).isEqualTo("NATIVE");
        assertThat(accepted.getResponse().getHeader("X-Operation-Id")).isNull();
        org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(20)).untilAsserted(()->{
            assertThat(find(own.id(),EventPublication.Status.COMPLETED)).isPresent();
            assertThat(listener.attempts(own.id())).isEqualTo(2);
        });
        var rapid=send(actor,ownPublication.toString().toUpperCase(Locale.ROOT));
        assertThat(rapid.getResponse().getStatus()).as(body(rapid)).isEqualTo(429);
        assertThat(rapid.getResponse().getHeader("Retry-After")).isNotBlank();
        assertThat(listener.attempts(own.id())).isEqualTo(2);
        assertThat(find(foreign.id(),EventPublication.Status.FAILED)).isPresent();
        assertThat(listener.attempts(foreign.id())).isEqualTo(1);
    }
    private Optional<TargetEventPublication> find(UUID id,EventPublication.Status status) {
        return publications.findByStatus(status).stream().filter(row->row.getEvent() instanceof ProbeEvent event&&event.id().equals(id)).findFirst();
    }
    private SmaPrincipal actor(long tenant){return new SmaPrincipal(tenant,"event-operator",tenant,"admin",null,false,DataAccess.unrestricted(),Set.of("monitor:event:retry","monitor:event:view"),"UTC");}
    private MvcResult send(SmaPrincipal actor,String id){return Users.as(actor,()->{
        try{return mvc.perform(post("/api/monitor/publications/"+id+"/retry").with(Users.request(actor))).andReturn();}
        catch(Exception ex){throw new IllegalStateException(ex);}
    });}
    private String body(MvcResult result){try{return result.getResponse().getContentAsString();}catch(Exception ex){throw new IllegalStateException(ex);}}
}
