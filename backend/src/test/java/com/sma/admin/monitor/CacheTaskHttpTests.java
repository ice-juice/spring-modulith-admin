package com.sma.admin.monitor;

import static org.assertj.core.api.Assertions.*;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import com.sma.admin.common.cache.*;
import com.sma.admin.common.security.*;
import com.sma.admin.support.*;

@IntegrationTest @AutoConfigureMockMvc
@TestPropertySource(properties={"sma.security.requests.fingerprint-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "sma.security.requests.minimum-write-interval=100ms","sma.security.requests.max-concurrent=1"})
class CacheTaskHttpTests {
    private static final String BASE="/api/monitor/caches/tasks", REGION="config.dictionary-options";
    @Autowired MockMvcTester mvc;
    @Autowired CacheTasks tasks;
    @Autowired CacheGovernance controls;

    @Test void previewSubmitReplayQueryAndCancellationUseTheNativeTaskProtocol() throws InterruptedException {
        var actor=manager(97301);
        Users.as(actor,()->{
            assertThat(mvc.post().uri(BASE+"/preview").with(Users.request(actor)).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"action\":\"CLEAR\",\"regions\":[\"config.dictionary-options\",\"missing.region\"],\"reason\":\"operator request\"}"))
                    .hasStatusOk().bodyJson().hasPathSatisfying("$.data.state",v->v.assertThat().isEqualTo("PREVIEW"))
                    .hasPathSatisfying("$.data.targets[0].region",v->v.assertThat().isEqualTo(REGION))
                    .hasPathSatisfying("$.data.skipped[0].reason",v->v.assertThat().isEqualTo("UNKNOWN_REGION"))
                    .doesNotHavePath("$.data.namespace").doesNotHavePath("$.data.values");return null;
        });
        String id=Users.as(actor,()->tasks.recent(1).getFirst().id());
        for(int i=0;i<2;i++){
            if(i>0)Thread.sleep(150); // Native replay retains its protocol after the configured HTTP cooldown.
            Users.as(actor,()->{
            assertThat(mvc.post().uri(BASE+"/"+id+"/submit").with(Users.request(actor)).header("Idempotency-Key","http-cache-task-original-01"))
                    .hasStatusOk().bodyJson().hasPathSatisfying("$.data.id",v->v.assertThat().isEqualTo(id))
                    .hasPathSatisfying("$.data.state",v->v.assertThat().isEqualTo("QUEUED")).doesNotHavePath("$.data.items[1]");return null;
            });
        }
        Users.as(actor,()->{assertThat(mvc.get().uri(BASE+"/"+id).with(Users.request(actor))).hasStatusOk().bodyJson()
                .hasPathSatisfying("$.data.items[0].state",v->v.assertThat().isEqualTo("PENDING"));return null;});
        Users.as(actor,()->{assertThat(mvc.post().uri(BASE+"/"+id+"/cancel").with(Users.request(actor))).hasStatusOk().bodyJson()
                .hasPathSatisfying("$.data.state",v->v.assertThat().isEqualTo("CANCELLED"));return null;});
        Users.as(actor,()->{assertThat(mvc.get().uri(BASE).with(Users.request(actor))).hasStatusOk().bodyJson()
                .hasPathSatisfying("$.data[0].id",v->v.assertThat().isEqualTo(id)).doesNotHavePath("$.data[1]");return null;});
    }
    @Test void operationPermissionIsRequiredInAdditionToTheViewHandlerPermission(){
        var viewer=actor(97302,101,Set.of("monitor:cache:view"));
        Users.as(viewer,()->{assertThat(mvc.post().uri(BASE+"/preview").with(Users.request(viewer)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"CLEAR\",\"regions\":[\"config.dictionary-options\"],\"reason\":\"not authorized\"}")).hasStatus(403);return null;});
        var noView=actor(97302,102,Set.of("monitor:cache:clear"));
        Users.as(noView,()->{assertThat(mvc.get().uri(BASE).with(Users.request(noView))).hasStatus(403);return null;});
    }
    @Test void missingKeyAndInvalidInputAreRejectedWithoutQueueing(){
        var actor=manager(97303);var id=Users.as(actor,()->tasks.preview(CacheTasks.Action.CLEAR,new CacheTasks.Selection(Set.of(REGION),null,null,null),"key required").id());
        Users.as(actor,()->{assertThat(mvc.post().uri(BASE+"/"+id+"/submit").with(Users.request(actor))).hasStatus(400).bodyJson()
                .hasPathSatisfying("$.error",v->v.assertThat().isEqualTo("common.request.key-required"));return null;});
        Users.as(actor,()->{assertThat(tasks.get(id).state()).isEqualTo(CacheTasks.State.PREVIEW);return null;});
        for(var body:Set.of("{\"action\":\"CLEAR\",\"reason\":\"\"}","{\"reason\":\"missing action\"}","{\"action\":\"FLUSHALL\",\"reason\":\"unsupported\"}"))Users.as(actor,()->{
            assertThat(mvc.post().uri(BASE+"/preview").with(Users.request(actor)).contentType(MediaType.APPLICATION_JSON).content(body)).hasStatus(400);return null;
        });
    }
    @Test void otherTenantsAndActorsCannotInspectOrCancelAnOwnedTask(){
        var owner=manager(97304);var id=Users.as(owner,()->tasks.preview(CacheTasks.Action.CLEAR,new CacheTasks.Selection(Set.of(REGION),null,null,null),"private task metadata").id());
        for(var intruder:Set.of(manager(97305),actor(97304,102,owner.permissions())))Users.as(intruder,()->{
            assertThat(mvc.get().uri(BASE+"/"+id).with(Users.request(intruder))).hasStatusOk().bodyJson()
                    .hasPathSatisfying("$.error",v->v.assertThat().isEqualTo("common.error.data-not-found")).doesNotHavePath("$.data.reason");return null;
        });
        var intruder=manager(97305);Users.as(intruder,()->{assertThat(mvc.post().uri(BASE+"/"+id+"/cancel").with(Users.request(intruder))).hasStatusOk().bodyJson()
                .hasPathSatisfying("$.error",v->v.assertThat().isEqualTo("common.error.data-not-found"));return null;});
    }
    @Test void changedPreviewReturnsLocalized409AndLeavesNoExecutableItems(){
        var actor=manager(97306);var id=Users.as(actor,()->{
            var preview=tasks.preview(CacheTasks.Action.CLEAR,new CacheTasks.Selection(Set.of(REGION),null,null,null),"stale preview");
            controls.change(REGION,new CacheGovernance.Command(CacheGovernance.Action.PAUSE,0,null,"concurrent operator"));return preview.id();
        });
        Users.as(actor,()->{assertThat(mvc.post().uri(BASE+"/"+id+"/submit").with(Users.request(actor)).header("Idempotency-Key","http-cache-task-changed-06").header("Accept-Language","en-US"))
                .hasStatus(409).bodyJson().hasPathSatisfying("$.error",v->v.assertThat().isEqualTo("common.cache-task.changed"))
                .hasPathSatisfying("$.message",v->v.assertThat().isEqualTo("Cache targets or governance state have changed; create a new preview"));return null;});
        Users.as(actor,()->{assertThat(tasks.get(id).items()).isEmpty();return null;});
    }
    private SmaPrincipal manager(long tenant){return actor(tenant,101,Set.of("monitor:cache:view","monitor:cache:clear","monitor:cache:pause"));}
    private SmaPrincipal actor(long tenant,long id,Set<String> permissions){return new SmaPrincipal(id,"manager",tenant,"admin",null,false,DataAccess.none(),permissions,"UTC");}
}
