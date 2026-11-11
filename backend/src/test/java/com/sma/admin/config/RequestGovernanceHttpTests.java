package com.sma.admin.config;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;
import com.sma.admin.common.security.*;
import com.sma.admin.config.internal.service.ParameterService;
import com.sma.admin.support.*;

@IntegrationTest @AutoConfigureMockMvc
@TestPropertySource(properties={"sma.security.requests.fingerprint-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "sma.security.requests.minimum-write-interval=30s"})
class RequestGovernanceHttpTests {
    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;
    @Autowired ParameterService parameters;
    private static final Set<String> PERMISSIONS = Set.of("config:parameter:create","config:parameter:view","config:parameter:update","config:parameter:delete");

    @Test void realHttpWriteRequiresAKeyAndReplaysOnlyTheOwnedReceipt() {
        Users.as(actor(79201,79201,PERMISSIONS), () -> {
            String key=UUID.randomUUID().toString(), body="{\"groupKey\":\"governed\",\"name\":\"Group\",\"sort\":0}";
            var missing=send(post("/api/config/parameters/groups").contentType("application/json").content(body));
            assertThat(missing.getResponse().getStatus()).as(body(missing)).isEqualTo(400);
            var first=send(post("/api/config/parameters/groups").header("Idempotency-Key",key).contentType("application/json").content(body));
            assertThat(first.getResponse().getStatus()).as(body(first)).isEqualTo(200);
            String receipt=first.getResponse().getHeader("X-Operation-Id"); assertThat(receipt).isNotBlank();
            var replay=send(post("/api/config/parameters/groups").header("Idempotency-Key",key).contentType("application/json").content(body));
            assertThat(replay.getResponse().getStatus()).as(body(replay)).isEqualTo(409);
            assertThat(json.readTree(body(replay)).at("/error").asText()).isEqualTo("common.request.completed");
            assertThat(json.readTree(body(replay)).at("/data/operationId").asText()).isEqualTo(receipt);
            assertThat(json.readTree(body(replay)).at("/data/name").isMissingNode()).isTrue();
            assertThat(parameters.groups()).filteredOn(group -> !group.builtin()).extracting(ParameterService.GroupView::groupKey).containsExactly("governed");
            var lookup=send(post("/api/common/requests/lookup").contentType("application/json").content("{\"key\":\""+key+"\"}"));
            assertThat(lookup.getResponse().getStatus()).as(body(lookup)).isEqualTo(200);
            assertThat(json.readTree(body(lookup)).at("/data/state").asText()).isEqualTo("SUCCEEDED");
            var conflict=send(post("/api/config/parameters/groups").header("Idempotency-Key",key).contentType("application/json").content(body.replace("Group","Different")));
            assertThat(conflict.getResponse().getStatus()).isEqualTo(409);
            assertThat(json.readTree(body(conflict)).at("/error").asText()).isEqualTo("common.request.conflict");
            Users.as(actor(79202,79201,PERMISSIONS), () -> { assertThat(send(get("/api/common/requests/"+receipt)).getResponse().getStatus()).isEqualTo(404); return null; });
            Users.as(actor(79201,79202,PERMISSIONS), () -> { assertThat(send(get("/api/common/requests/"+receipt)).getResponse().getStatus()).isEqualTo(404); return null; });
            return null;
        });
    }
    @Test void permissionDenialPrecedesExecutionAndRemovedPermissionsAlsoHideReceipts() {
        Users.as(actor(79203,79203,Set.of()), () -> {
            var denied=send(post("/api/config/parameters/groups").header("Idempotency-Key",UUID.randomUUID().toString()).contentType("application/json")
                    .content("{\"groupKey\":\"denied\",\"name\":\"Denied\",\"sort\":0}"));
            assertThat(denied.getResponse().getStatus()).isEqualTo(403); assertThat(parameters.groups()).filteredOn(group -> !group.builtin()).isEmpty(); return null;
        });
        String receipt=Users.as(actor(79204,79204,PERMISSIONS), () -> send(post("/api/config/parameters/groups").header("Idempotency-Key",UUID.randomUUID().toString())
                .contentType("application/json").content("{\"groupKey\":\"revoked\",\"name\":\"Revoked\",\"sort\":0}")).getResponse().getHeader("X-Operation-Id"));
        assertThat(receipt).isNotBlank();
        Users.as(actor(79204,79204,Set.of()), () -> { assertThat(send(get("/api/common/requests/"+receipt)).getResponse().getStatus()).isEqualTo(403); return null; });
    }
    @Test void deletingAnObjectStillAllowsOwnedCompletionReceiptAndCrossTenantIdsNeverMutate() {
        long groupId=Users.as(actor(79205,79205,PERMISSIONS), () -> parameters.saveGroup(null,
                new ParameterService.GroupInput("deleteprobe","Delete",null,0,java.util.Map.of())).id());
        Users.as(actor(79206,79206,PERMISSIONS), () -> {
            var denied=send(delete("/api/config/parameters/groups/"+groupId).header("Idempotency-Key",UUID.randomUUID().toString()));
            assertThat(json.readTree(body(denied)).at("/error").asText()).isEqualTo("common.error.data-not-found"); return null;
        });
        Users.as(actor(79205,79205,PERMISSIONS), () -> {
            assertThat(parameters.groups()).filteredOn(group -> !group.builtin()).extracting(ParameterService.GroupView::groupKey).containsExactly("deleteprobe");
            String key=UUID.randomUUID().toString();
            var first=send(delete("/api/config/parameters/groups/"+groupId).header("Idempotency-Key",key));
            assertThat(first.getResponse().getStatus()).as(body(first)).isEqualTo(200);
            var repeat=send(delete("/api/config/parameters/groups/"+groupId).header("Idempotency-Key",key));
            assertThat(repeat.getResponse().getStatus()).as(body(repeat)).isEqualTo(409);
            assertThat(json.readTree(body(repeat)).at("/data/state").asText()).isEqualTo("SUCCEEDED");
            assertThat(parameters.groups()).filteredOn(group -> !group.builtin()).isEmpty(); return null;
        });
    }
    @Test void rapidNewKeysCannotBypassThePerActorWriteCooldown() {
        Users.as(actor(79207,79207,PERMISSIONS),()->{
            String body="{\"groupKey\":\"rapid\",\"name\":\"Rapid\",\"sort\":0}";
            var first=send(post("/api/config/parameters/groups").header("Idempotency-Key",UUID.randomUUID().toString()).contentType("application/json").content(body));
            assertThat(first.getResponse().getStatus()).as(body(first)).isEqualTo(200);
            String rejectedKey=UUID.randomUUID().toString();
            var repeated=send(post("/api/config/parameters/groups").header("Idempotency-Key",rejectedKey).contentType("application/json").content(body));
            assertThat(repeated.getResponse().getStatus()).as(body(repeated)).isEqualTo(429);
            assertThat(Long.parseLong(repeated.getResponse().getHeader("Retry-After"))).isBetween(1L,30L);
            assertThat(parameters.groups()).hasSize(1);
            var lookup=send(post("/api/common/requests/lookup").contentType("application/json").content("{\"key\":\""+rejectedKey+"\"}"));
            assertThat(json.readTree(body(lookup)).at("/data/state").asText()).isEqualTo("FAILED_FINAL"); return null;
        });
    }
    private static SmaPrincipal actor(long user,long tenant,Set<String> permissions){return new SmaPrincipal(user,"request-http",tenant,"admin",null,false,DataAccess.unrestricted(),permissions,"UTC");}
    private MvcResult send(MockHttpServletRequestBuilder request){
        var user=CurrentUser.require();
        return Users.as(user,()->{try{return mvc.perform(request.with(Users.request(user))).andReturn();}catch(Exception ex){throw new IllegalStateException(ex);}});
    }
    private String body(MvcResult result){try{return result.getResponse().getContentAsString();}catch(Exception ex){throw new IllegalStateException(ex);}}
}
