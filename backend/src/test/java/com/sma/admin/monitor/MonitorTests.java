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
  Users.as(Users.principal(101,263,DataAccess.unrestricted()),()->{
   parameters.stringValue("tenant-b","private-b");var row=caches.list().getFirst();assertThat(row.size()).isEqualTo(1);assertThat(row.misses()).isEqualTo(1);
   caches.clear("config.parameters");assertThat(caches.list().getFirst().size()).isZero();return null;
  });
  Users.as(Users.principal(101,262,DataAccess.unrestricted()),()->{
   var row=caches.list().getFirst();assertThat(row.size()).isEqualTo(1);assertThat(row.hits()).isEqualTo(1);caches.clearAll();assertThat(caches.list().getFirst().size()).isZero();return null;
  });
 }
}
