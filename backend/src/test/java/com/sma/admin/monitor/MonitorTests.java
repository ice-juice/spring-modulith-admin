package com.sma.admin.monitor;
import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import com.sma.admin.common.security.*;
import com.sma.admin.config.internal.service.ParameterService;
import com.sma.admin.monitor.internal.service.*;
import com.sma.admin.support.*;

@IntegrationTest @AutoConfigureMockMvc
class MonitorTests {
 @Autowired ServerService server;@Autowired CacheService caches;@Autowired ParameterService parameters;@Autowired MockMvcTester mvc;
 @Test void snapshotsHaveRuntimeAndPoolDataAndHttpRequiresExplicitPermission(){
  var data=server.get();assertThat(data.processors()).isPositive();assertThat(data.heapUsedBytes()).isPositive();assertThat(data.threads()).isPositive();assertThat(data.pool()).isNotNull();assertThat(data.pool().total()).isPositive();
  Users.as(Users.principal(101,261,DataAccess.unrestricted()),()->{assertThat(mvc.get().uri("/api/monitor/server")).hasStatus(403);return null;});
  var actor=new SmaPrincipal(101,"monitor",261,"admin",null,false,DataAccess.unrestricted(),java.util.Set.of("monitor:server:view"),"UTC");
  Users.as(actor,()->{assertThat(mvc.get().uri("/api/monitor/server")).hasStatusOk().bodyJson().hasPath("$.data.heapUsedBytes").doesNotHavePath("$.data.password");return null;});
 }
 @Test void cacheMetadataAndClearAreTenantScopedAndNeverExposeValues(){
  Users.as(Users.principal(101,262,DataAccess.unrestricted()),()->{parameters.stringValue("tenant-a","private-a");parameters.stringValue("tenant-a","private-a");return null;});
  Users.as(cacheManager(263),()->{
   parameters.stringValue("tenant-b","private-b");var row=parameterCache();assertThat(row.size()).isEqualTo(1);assertThat(row.misses()).isEqualTo(1);
   caches.clear("config.parameters");assertThat(parameterCache().size()).isZero();return null;
  });
  Users.as(cacheManager(262),()->{
   var row=parameterCache();assertThat(row.size()).isEqualTo(1);assertThat(row.hits()).isEqualTo(1);caches.clearAll();assertThat(parameterCache().size()).isZero();return null;
  });
 }
 @Test void governedDirectoryHttpShowsPolicyAndScopeButNotValuesOrConnectionSecrets(){
  var viewer=new SmaPrincipal(101,"cache-viewer",264,"admin",null,false,DataAccess.unrestricted(),java.util.Set.of("monitor:cache:view"),"UTC");
  Users.as(viewer,()->{
   assertThat(mvc.get().uri("/api/monitor/caches/directory").param("owner","config").param("scenario","dictionary-options"))
    .hasStatusOk().bodyJson().hasPathSatisfying("$.data.length()",v->v.assertThat().isEqualTo(1));return null;
  });
  Users.as(viewer,()->{
   assertThat(mvc.get().uri("/api/monitor/caches/directory/config.dictionary-options"))
    .hasStatusOk().bodyJson().hasPath("$.data.observation.policy.effective.ttl")
    .hasPathSatisfying("$.data.scope",v->v.assertThat().isEqualTo("TENANT"))
    .hasPathSatisfying("$.data.allowedOperations.length()",v->v.assertThat().isEqualTo(0))
    .doesNotHavePath("$.data.observation.password").doesNotHavePath("$.data.observation.namespace")
    .doesNotHavePath("$.data.values");return null;
  });
  Users.as(viewer,()->{assertThat(mvc.delete().uri("/api/monitor/caches/config.dictionary-options")).hasStatus(403);return null;});
  Users.as(Users.principal(101,264,DataAccess.none()),()->{assertThat(mvc.get().uri("/api/monitor/caches/directory")).hasStatus(403);return null;});
 }
 private SmaPrincipal cacheManager(long tenant){return new SmaPrincipal(101,"cache-manager",tenant,"admin",null,false,DataAccess.unrestricted(),java.util.Set.of("monitor:cache:clear","monitor:cache:view"),"UTC");}
 private CacheService.View parameterCache(){return caches.list().stream().filter(row->row.id().equals("config.parameters")).findFirst().orElseThrow();}
}
