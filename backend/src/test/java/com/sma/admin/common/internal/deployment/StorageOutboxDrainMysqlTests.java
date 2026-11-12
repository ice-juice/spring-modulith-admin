package com.sma.admin.common.internal.deployment;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.awaitility.Awaitility.await;
import static com.sma.admin.support.DeploymentWorkFixture.host;
import java.net.URI;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.zaxxer.hikari.*;
import com.sma.admin.common.request.*;
import com.sma.admin.common.security.*;
import com.sma.admin.tenant.internal.persistence.StorageResourceEntity;
import com.sma.admin.tenant.internal.service.*;
import com.sma.admin.tenant.storage.StorageCredentialResolver;
import com.sma.admin.support.*;

@IntegrationTest
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="SMA_P08_BUSINESS_DB_URL",matches=".+")
@org.springframework.test.context.TestPropertySource(properties="sma.security.requests.fingerprint-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class StorageOutboxDrainMysqlTests {
    @Autowired BusinessLedgerResourceFactory factory;
    @Autowired DeploymentWorkCoordinator admission;

    @SuppressWarnings("unchecked")
    @Test void actualBusinessOutboxKeepsUnknownAndUnclaimedRowsWhileAdmittedDeliveryCommits()throws Exception{
        String url=System.getenv("SMA_P08_BUSINESS_DB_URL");
        if(url==null||!url.startsWith("jdbc:mysql://")||!URI.create(url.substring(5)).getPath().matches("/sma_test_p08_storage_drain_20261112(?:_[a-z0-9]+)?")
                ||!URI.create(url.substring(5)).getHost().equals("127.0.0.1")||URI.create(url.substring(5)).getPort()!=13306)
            throw new IllegalArgumentException("Requires isolated P08 business schema on port 13306");
        String database=URI.create(url.substring(5)).getPath().substring(1);
        var config=new HikariConfig();config.setJdbcUrl(url);config.setUsername(System.getenv("SMA_TEST_DB_USERNAME"));config.setPassword(System.getenv("SMA_TEST_DB_PASSWORD"));config.setMaximumPoolSize(2);
        try(var observer=new HikariDataSource(config)){
            Flyway.configure().dataSource(observer).locations("classpath:db/business-ledger").table("flyway_schema_history_business_ledger").load().migrate();
            var sql=new JdbcTemplate(observer);
            assertThat(sql.queryForObject("SELECT COUNT(*) FROM common_business_outbox",Long.class)).as("fresh dedicated fixture").isZero();
            var credentials=(ObjectProvider<StorageCredentialResolver>)mock(ObjectProvider.class);
            when(credentials.getIfAvailable()).thenReturn(ref->new StorageCredentialResolver.Credentials(System.getenv("SMA_TEST_DB_USERNAME"),System.getenv("SMA_TEST_DB_PASSWORD")));
            try(var pools=new StoragePoolRegistry(factory,credentials,new StoragePoolProperties(1,2,2,Duration.ofMinutes(5)),admission)){
                var source=new StorageResourceEntity();source.setResourceId("p08-drain");source.setRevision(1L);source.setState("REGISTERED");
                source.setDbHost("127.0.0.1");source.setDbPort(13306);source.setDatabaseName(database);source.setTlsMode("DISABLED");source.setSecretRef("test/p08");source.setPoolMax(2);source.setConnectTimeoutMillis(2000);
                var ids=new ArrayList<Long>();
                String fixtureKey=UUID.randomUUID().toString().replace("-","");
                Users.as(Users.principal(71,97280,DataAccess.none()),()->{
                    try(var session=pools.open(source)){
                        for(int i=0;i<3;i++){
                            final int index=i;
                            var outcome=session.executeLocal(new RequestCommand("shop.outbox","shop.order","order-"+i,fixtureKey+"-"+i,new byte[]{(byte)i},""),new RequestExecution.Work<Long>(){
                                public void authorize(){}
                                public Long execute(){long id=session.enqueue("shop.drain",fixtureKey+"-"+index,new byte[]{(byte)index});ids.add(id);return id;}
                            });
                            assertThat(outcome.receipt().state()).isEqualTo(RequestReceipt.State.SUCCEEDED);
                        }
                    }return null;
                });
                var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var calls=new AtomicInteger();
                BusinessOutboxConsumer consumer=new BusinessOutboxConsumer(){
                    public boolean supports(String type){return type.equals("shop.drain");}
                    public void deliver(Delivery event){
                        assertThat(ActorContext.tenantId()).isEqualTo(97280);assertThat(event.resourceId()).isEqualTo("p08-drain");
                        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();assertThat(admission.snapshot().activeWork()).isOne();
                        if(calls.incrementAndGet()==1)throw new IllegalStateException("result unknown");
                        assertThat(event.id()).isEqualTo(ids.get(1));entered.countDown();
                        try{if(!release.await(10,TimeUnit.SECONDS))throw new AssertionError("Outbox not released");}
                        catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new AssertionError(interrupted);}
                    }
                };
                assertThatThrownBy(()->host(()->pools.deliverOne("p08-drain",1,List.of(consumer)))).isInstanceOf(IllegalStateException.class);
                assertThat(state(sql,ids.getFirst())).isEqualTo("UNKNOWN");assertThat(admission.snapshot().activeWork()).isZero();
                try(var worker=new StorageOutboxWorker(pools,List.of(consumer),admission)){
                    try{
                        host(worker::tick);assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();assertThat(host(admission::beginDrain)).isTrue();
                        assertThat(admission.snapshot().activeWork()).isOne();assertThat(state(sql,ids.get(1))).isEqualTo("UNKNOWN");assertThat(state(sql,ids.get(2))).isEqualTo("PENDING");
                    }finally{release.countDown();}
                    await().atMost(Duration.ofSeconds(5)).until(()->admission.snapshot().activeWork()==0);
                    assertThat(state(sql,ids.get(1))).isEqualTo("DONE");assertThat(state(sql,ids.getFirst())).isEqualTo("UNKNOWN");assertThat(state(sql,ids.get(2))).isEqualTo("PENDING");
                    sql.update("UPDATE common_business_outbox SET lease_until=DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 1 SECOND) WHERE id=?",ids.getFirst());
                    host(worker::tick);assertThat(host(()->pools.deliverOne("p08-drain",1,List.of(consumer)))).isFalse();assertThat(calls).hasValue(2);
                    assertThat(sql.queryForList("SELECT attempts FROM common_business_outbox ORDER BY id",Integer.class)).containsExactly(1,1,0);
                }
            }
        }
    }
    private String state(JdbcTemplate sql,long id){return sql.queryForObject("SELECT state FROM common_business_outbox WHERE id=?",String.class,id);}
}
