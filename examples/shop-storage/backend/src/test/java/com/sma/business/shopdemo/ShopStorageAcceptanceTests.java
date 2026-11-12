package com.sma.business.shopdemo;

import static org.assertj.core.api.Assertions.*;
import java.net.URI;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import com.sma.admin.support.*;
import com.sma.admin.common.security.*;
import com.sma.admin.common.request.BusinessLedger;
import com.sma.admin.tenant.internal.service.*;
import com.sma.admin.tenant.storage.StorageCredentialResolver;
import com.sma.business.shopdemo.internal.service.*;
import com.sma.business.shopdemo.internal.persistence.ProjectionMapper;

/** Test-only provisioning uses internal setup services; the business production files never depend on them. */
@IntegrationTest @Import(ShopStorageAcceptanceTests.Secrets.class)
@org.springframework.boot.test.context.SpringBootTest(classes=com.sma.admin.SpringbootModulithAdminApplication.class)
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
@EnabledIfEnvironmentVariable(named="SMA_SAMPLE_BUSINESS_DB_URL",matches=".+")
@TestPropertySource(properties={"sma.tenant.platform-admin-user-ids=991","sma.tenant.storage.allowed-hosts=127.0.0.1","sma.tenant.storage.allow-insecure-loopback=true",
        "sma.cache.nodes.enabled=true","sma.cache.nodes.cluster=shop-storage-acceptance",
        "sma.security.requests.fingerprint-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="})
class ShopStorageAcceptanceTests {
    @TestConfiguration static class Secrets {
        @Bean StorageCredentialResolver sampleCredentials(){return ref->{
            if(!"sample/fixture".equals(ref))throw new IllegalArgumentException("Unknown sample secret");
            return new StorageCredentialResolver.Credentials(TestDatabase.USERNAME,TestDatabase.PASSWORD);
        };}
    }
    @Autowired TenantDirectoryService tenants;
    @Autowired StorageDirectory directory;
    @Autowired StorageProvisionService provision;
    @Autowired StoragePoolRegistry pools;
    @Autowired StorageOutboxWorker worker;
    @Autowired BusinessLedger ledger;
    @Autowired OrderService orders;
    @Autowired OrderCreatedConsumer consumer;
    @Autowired ProjectionMapper projections;
    @Autowired com.sma.business.shopdemo.internal.persistence.OrderMapper orderRows;
    @Autowired JdbcTemplate platform;
    @Autowired com.sma.admin.common.internal.cache.CacheNodeRuntime node;
    @Test void actualHostDiscoversBusinessMigratesOnlyBusinessSchemaAndPreservesReplayAndTenantIsolation(){
        var url=System.getenv("SMA_SAMPLE_BUSINESS_DB_URL");
        if(!url.startsWith("jdbc:mysql://")||!URI.create(url.substring(5)).getPath().equals("/sma_test_p10_sample_business_20261112"))
            throw new IllegalArgumentException("Explicit sample business schema required");
        var uri=URI.create(url.substring(5));
        worker.close();
        // Exact dedicated schema checked above. Reset both sides so repeated runs cannot reuse prior business rows.
        org.flywaydb.core.Flyway.configure().dataSource(url,TestDatabase.USERNAME,TestDatabase.PASSWORD)
                .cleanDisabled(false).load().clean();
        node.heartbeatTick();
        org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(15)).until(()->node.currentLease()!=null);
        var suffix=UUID.randomUUID().toString().substring(0,8);
        var resourceId="sample-business-"+suffix;
        var operator=new SmaPrincipal(991,"sample-platform",1,"admin",null,false,DataAccess.unrestricted(),Set.of(),"UTC");
        var ids=Users.as(operator,()->{
            var first=tenants.create(new TenantDirectoryService.TenantInput("sample-a-"+suffix,"Sample A"));
            var second=tenants.create(new TenantDirectoryService.TenantInput("sample-b-"+suffix,"Sample B"));
            tenants.changeStatus(first.id(),new TenantDirectoryService.StatusInput("ACTIVE",1));
            tenants.changeStatus(second.id(),new TenantDirectoryService.StatusInput("ACTIVE",1));
            directory.register(new StorageDirectory.ResourceInput(resourceId,1,"Sample business","127.0.0.1",uri.getPort(),uri.getPath().substring(1),"DISABLED","sample/fixture",4,1000));
            for(long id:List.of(first.id(),second.id())){
                var binding=directory.bindInitial(new StorageDirectory.BindingInput(id,"shopdemo",resourceId,1,"v1"));
                assertThat(provision.provision(binding.getId(),1,"sample-provision-"+id)).isEqualTo("SUCCEEDED");
            }
            return new long[]{first.id(),second.id()};
        });
        var permissions=Set.of("shopdemo:order:view","shopdemo:order:create");
        var first=new SmaPrincipal(81,"sample-first",ids[0],"admin",null,false,DataAccess.unrestricted(),permissions,"UTC");
        var second=new SmaPrincipal(82,"sample-second",ids[1],"admin",null,false,DataAccess.unrestricted(),permissions,"UTC");
        Users.as(first,()->{
            assertThat(orders.count()).isZero();
            try(var session=ledger.open("shopdemo")){
                var before=session.authoritativeRevision("shopdemo.orders");
                assertThatThrownBy(()->session.executeLocal(new com.sma.admin.common.request.RequestCommand(
                        "shopdemo.order.rollback","shopdemo.order","rollback","sample-rollback-key-01",new byte[]{1},"shopdemo:order:create"),
                        new com.sma.admin.common.request.RequestExecution.Work<Void>(){
                            @Override public void authorize(){ActorContext.requireTenant(ids[0]);}
                            @Override public Void execute(){
                                var order=new com.sma.business.shopdemo.internal.persistence.OrderEntity();order.setCode("rolled-back");orderRows.insert(order);
                                session.invalidate("shopdemo.orders");session.enqueue("shopdemo.order-created","sample-rollback-event",new byte[]{1});
                                throw new IllegalStateException("sample rollback");
                            }
                        })).isInstanceOf(IllegalStateException.class).hasMessage("sample rollback");
                assertThat(session.authoritativeRevision("shopdemo.orders")).isEqualTo(before);
            }
            assertThat(orders.count()).isZero();
            var result=orders.create("order-one","sample-order-key-0001");assertThat(result.replayed()).isFalse();
            assertThat(orders.count()).isEqualTo(1);
            var replay=orders.create("order-one","sample-order-key-0001");assertThat(replay.replayed()).isTrue();
            assertThat(replay.receipt().operationId()).isEqualTo(result.receipt().operationId());
            assertThat(orders.receipt("sample-order-key-0001").operationId()).isEqualTo(result.receipt().operationId());return null;
        });
        assertThat(Users.as(second,orders::count)).isZero();
        assertThat(DeploymentWorkFixture.host(()->pools.deliverOne(resourceId,1,List.of(consumer)))).isTrue();
        assertThat(DeploymentWorkFixture.host(()->pools.deliverOne(resourceId,1,List.of(consumer)))).isFalse();
        Users.as(first,()->{try(var session=ledger.open("shopdemo")){assertThat(session.query(()->projections.selectCount(null))).isEqualTo(1);}return null;});
        assertThat(platform.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('shopdemo_order','shopdemo_projection')",Long.class)).isZero();
        var denied=new SmaPrincipal(83,"sample-denied",ids[0],"admin",null,true,DataAccess.unrestricted(),Set.of(),"UTC");
        Users.as(denied,()->{assertThatThrownBy(orders::count).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);return null;});
        assertThat(platform.queryForObject("SELECT COUNT(*) FROM tenant_storage_session_lease WHERE owner_tenant_id IN (?,?)",Long.class,ids[0],ids[1])).isZero();
    }
}
