package com.sma.admin.monitor;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;
import com.sma.admin.common.cache.CacheNodeSelection;
import com.sma.admin.common.internal.cache.CacheNodeIdentity;
import com.sma.admin.common.internal.persistence.*;
import com.sma.admin.common.security.*;
import com.sma.admin.support.*;

@IntegrationTest @AutoConfigureMockMvc
@TestPropertySource(properties={"sma.cache.nodes.id=node-a","sma.cache.nodes.expected=node-a,node-b",
    "sma.security.requests.fingerprint-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="})
class CacheNodeRoutingHttpTests {
    @Autowired MockMvc mvc;@Autowired CacheNodeIdentity identity;@Autowired JsonMapper json;
    @Autowired CacheTaskMapper tasks;@Autowired RequestBudgetMapper budgets;
    private static final String NODE=CacheNodeSelection.NODE_HEADER,PROCESS=CacheNodeSelection.PROCESS_HEADER,ROOT="/api/monitor/caches";
    private SmaPrincipal actor(long tenant,String... permissions){return new SmaPrincipal(101L,"route-viewer",tenant,"admin",null,false,DataAccess.unrestricted(),Set.of(permissions),"UTC");}

    @Test void selectedAndLegacyReadsReportOnlyTheActualReceivingProcess()throws Exception{
        var actor=actor(90301,"monitor:cache:view");
        var legacy=mvc.perform(get(ROOT+"/directory").with(Users.request(actor))).andReturn().getResponse();
        assertThat(legacy.getStatus()).isEqualTo(200);assertThat(legacy.getHeader(NODE)).isEqualTo("node-a");assertThat(legacy.getHeader(PROCESS)).isEqualTo(identity.processId());
        var selected=mvc.perform(get(ROOT+"/directory").with(Users.request(actor)).header(NODE,"node-a").header(PROCESS,identity.processId())).andReturn().getResponse();
        assertThat(selected.getStatus()).isEqualTo(200);assertThat(selected.getHeader("Cache-Control")).contains("no-store");
        var rows=json.readTree(selected.getContentAsString()).path("data");assertThat(rows.size()).isPositive();
        for(var row:rows)if(!row.path("observation").isNull())assertThat(row.at("/observation/processId").asText()).isEqualTo(identity.processId());
    }
    @Test void wrongNodeOrOldProcessIsRejectedBeforePreviewAndRequestBudgetWrites()throws Exception{
        var actor=actor(90302,"monitor:cache:view","monitor:cache:clear");
        String body="{\"action\":\"CLEAR\",\"regions\":[\"config.preferences\"],\"reason\":\"route verification\"}";
        var wrong=mvc.perform(post(ROOT+"/tasks/preview").with(Users.request(actor)).contentType("application/json").content(body)
            .header(NODE,"node-b").header(PROCESS,UUID.randomUUID().toString())).andReturn().getResponse();
        assertThat(wrong.getStatus()).isEqualTo(503);assertThat(json.readTree(wrong.getContentAsString()).path("error").asText()).isEqualTo("common.cache.selection-wrong-node");
        assertThat(wrong.getHeader(PROCESS)).isNull();
        var replaced=mvc.perform(post(ROOT+"/tasks/preview").with(Users.request(actor)).contentType("application/json").content(body)
            .header(NODE,"node-a").header(PROCESS,UUID.randomUUID().toString())).andReturn().getResponse();
        assertThat(replaced.getStatus()).isEqualTo(409);assertThat(json.readTree(replaced.getContentAsString()).path("error").asText()).isEqualTo("common.cache.selection-replaced-process");
        Users.as(actor,()->{assertThat(tasks.selectCount(null)).isZero();assertThat(budgets.selectCount(null)).isZero();return null;});
    }
    @Test void malformedAndDuplicateHeadersAreRejectedButOtherRoutesAreNotReinterpreted()throws Exception{
        var actor=actor(90303,"monitor:cache:view");
        for(var request:List.of(get(ROOT+"/directory").header(NODE,"node-a"),get(ROOT+"/directory").header(PROCESS,identity.processId()),
            get(ROOT+"/directory").header(NODE,"node-a","node-b").header(PROCESS,identity.processId()),
            get(ROOT+"/directory").header(NODE,"http://127.0.0.1:8080").header(PROCESS,identity.processId()))){
            var response=mvc.perform(request.with(Users.request(actor))).andReturn().getResponse();assertThat(response.getStatus()).isEqualTo(400);
        }
        var response=mvc.perform(get("/api/config/preferences").with(Users.request(actor)).header(NODE,"arbitrary")).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(200);assertThat(response.getHeader(NODE)).isNull();
    }
    @Test void anonymousAndNoViewUsersCannotObtainIdentityOrUseSelectionAsAnActionGrant()throws Exception{
        var anonymous=mvc.perform(get(ROOT+"/directory").header(NODE,"node-a").header(PROCESS,identity.processId())).andReturn().getResponse();
        assertThat(anonymous.getStatus()).isEqualTo(401);assertThat(anonymous.getHeader(PROCESS)).isNull();
        var denied=mvc.perform(get(ROOT+"/directory").with(Users.request(actor(90304,"monitor:cache:clear"))).header(NODE,"node-a").header(PROCESS,identity.processId())).andReturn().getResponse();
        assertThat(denied.getStatus()).isEqualTo(403);assertThat(denied.getHeader(PROCESS)).isNull();
        var noAction=mvc.perform(delete(ROOT+"/config.preferences").with(Users.request(actor(90305,"monitor:cache:view")))
            .header(NODE,"node-a").header(PROCESS,identity.processId())).andReturn().getResponse();assertThat(noAction.getStatus()).isEqualTo(403);
    }
}
