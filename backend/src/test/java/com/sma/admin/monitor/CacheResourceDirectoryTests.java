package com.sma.admin.monitor;

import static org.assertj.core.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import com.sma.admin.common.cache.*;
import com.sma.admin.common.security.*;
import com.sma.admin.monitor.internal.service.CacheDirectoryService;
import com.sma.admin.rule.internal.service.ExpressionEngine;
import com.sma.admin.support.*;

@IntegrationTest @AutoConfigureMockMvc
@TestPropertySource(properties="sma.security.requests.fingerprint-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
class CacheResourceDirectoryTests {
    @Autowired CacheDirectoryService directory;
    @Autowired CacheResources resources;
    @Autowired CacheTasks tasks;
    @Autowired CacheGovernance governance;
    @Autowired ExpressionEngine expressions;
    @Autowired MockMvcTester mvc;
    @Test void allKindsAppearWithoutTreatingProtectedStateOrFilesAsDisposableData(){
        var actor=actor(98111,DataAccess.unrestricted(),"monitor:cache:view","monitor:cache:clear","audit:login:view","file:file:view","rule:maintenance:view","monitor:sql:view");
        Users.as(actor,()->{
            var rows=directory.list(new CacheDirectoryService.Filter(null,null,null,null,null,null,null));
            assertThat(rows).extracting(CacheDirectoryService.View::kind).contains("DATA","RUNTIME","RESOURCE","PROTECTED");
            assertThat(rows).filteredOn(row->row.kind().equals("PROTECTED")).allSatisfy(row->{assertThat(row.allowedOperations()).isEmpty();assertThat(row.declaredOperations()).isEmpty();assertThat(row.resource().entries()).isEqualTo(-1);});
            assertThat(directory.detail("audit.ip-library").resource().entries()).isEqualTo(2);
            assertThat(directory.detail("file.storage").state()).isEqualTo("READ_ONLY");assertThat(directory.detail("file.storage").allowedOperations()).isEmpty();
            var preview=tasks.preview(CacheTasks.Action.CLEAR,new CacheTasks.Selection(Set.of("auth.sessions","file.storage","rule.runtime","common.sql-statistics"),null,null,null),"protected target test");
            assertThat(preview.targets()).isEmpty();assertThat(preview.skipped()).extracting(CacheTasks.Skipped::reason).contains(CacheTasks.SkipReason.PROTECTED,CacheTasks.SkipReason.ADAPTER_REQUIRED);
            assertThatThrownBy(()->governance.state("auth.sessions")).isInstanceOf(AccessDeniedException.class);return null;
        });
    }
    @Test void ruleSamplesRetainBusinessPermissionAllScopeAndTenantIsolation(){
        var owner=actor(98112,DataAccess.unrestricted(),"monitor:cache:view","rule:maintenance:view");
        Users.as(owner,()->{expressions.validate("1 + 2");assertThat(resources.inspect("rule.runtime").entries()).isEqualTo(1);return null;});
        Users.as(actor(98113,DataAccess.unrestricted(),"monitor:cache:view","rule:maintenance:view"),()->{assertThat(resources.inspect("rule.runtime").entries()).isZero();return null;});
        for(var viewer:List.of(actor(98112,DataAccess.own(),"monitor:cache:view","rule:maintenance:view"),actor(98112,DataAccess.unrestricted(),"monitor:cache:view")))Users.as(viewer,()->{
            assertThat(resources.inspect("rule.runtime").state()).isEqualTo("RESTRICTED");assertThat(resources.inspect("rule.runtime").entries()).isEqualTo(-1);return null;
        });
        Users.as(owner,()->{assertThat(resources.inspect("rule.runtime").entries()).isEqualTo(1);return null;}); // Inspection never clears.
    }
    @Test void httpUsesLocalizedLifecycleAndNeverLeaksInternalResources(){
        var actor=actor(98114,DataAccess.unrestricted(),"monitor:cache:view","audit:login:view");
        Users.as(actor,()->{assertThat(mvc.get().uri("/api/monitor/caches/directory/audit.ip-library").with(Users.request(actor)).header("Accept-Language","en-US"))
                .hasStatusOk().bodyJson().hasPathSatisfying("$.data.resource.entries",value->value.assertThat().isEqualTo("2"))
                .hasPathSatisfying("$.data.lifecycleDescription",value->value.assertThat().asString().contains("IPv4/IPv6"))
                .doesNotHavePath("$.data.resource.path").doesNotHavePath("$.data.resource.values");return null;});
        Users.as(actor,()->{assertThat(mvc.get().uri("/api/monitor/caches/directory").with(Users.request(actor)).param("provider","protected"))
                .hasStatusOk().bodyJson().hasPathSatisfying("$.data[0].state",value->value.assertThat().isEqualTo("PROTECTED"));return null;});
    }
    private SmaPrincipal actor(long tenant,DataAccess access,String... grants){return new SmaPrincipal(101,"resource-viewer",tenant,"admin",null,false,access,Set.of(grants),"UTC");}
}
