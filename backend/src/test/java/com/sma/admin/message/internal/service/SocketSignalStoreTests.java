package com.sma.admin.message.internal.service;
import static org.assertj.core.api.Assertions.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import java.util.stream.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.access.AccessDeniedException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.sma.admin.common.security.*;
import com.sma.admin.message.internal.persistence.*;
import com.sma.admin.support.*;

@IntegrationTest @TestPropertySource(properties={"sma.message.signals.enabled=false","sma.message.signals.max-entries=8"})
class SocketSignalStoreTests {
    @Autowired SocketSignalStore store;@Autowired SocketSignalMapper signals;@Autowired PlatformTransactionManager transactions;
    private <T>T job(long tenant,Supplier<T> action){return ActorContext.withinJob(tenant,"message-signal:test",action);}
    private <T>T write(long tenant,Supplier<T> action){return job(tenant,()->{var tx=new TransactionTemplate(transactions);tx.setTimeout(10);return tx.execute(status->action.get());});}
    private void publish(long tenant,String portal,Set<Long> users,String type,long id,String reason){write(tenant,()->{store.publish(portal,users,type,id,reason);return null;});}

    @Test void coalescingIsVisibleToIndependentReadersAndTenantPortalUserRemainIsolated(){
        publish(90101,"admin",Set.of(77L),"notice",10,null);publish(90101,"customer",Set.of(77L),"notice",20,null);publish(90102,"admin",Set.of(77L),"notice",30,null);
        var first=job(90101,()->store.read("admin",Set.of(77L)));assertThat(first).hasSize(1);assertThat(first.getFirst().objectId()).isEqualTo(10);
        assertThat(job(90101,()->store.read("admin",Set.of(77L)))).isEqualTo(first);
        assertThat(job(90101,()->store.read("admin",Set.of(78L)))).isEmpty();
        assertThat(job(90102,()->store.read("admin",Set.of(77L))).getFirst().objectId()).isEqualTo(30);
        publish(90101,"admin",Set.of(77L),"notice",11,null);
        var latest=job(90101,()->store.read("admin",Set.of(77L))).getFirst();assertThat(latest.id()).isEqualTo(first.getFirst().id());assertThat(latest.revision()).isEqualTo(2);assertThat(latest.objectId()).isEqualTo(11);
        publish(90101,"*",Set.of(77L),"session-ended",99,"FORCED");
        assertThat(job(90101,()->store.read("customer",Set.of(77L)))).extracting(SocketSignalStore.Signal::objectId).containsExactlyInAnyOrder(20L,99L);
    }
    @Test void rollbackDoesNotPublishAndOrdinaryCallerCannotForgeWorker(){
        assertThatThrownBy(()->write(90103,()->{store.publish("admin",Set.of(77L),"todo",10,null);throw new IllegalStateException("rollback");})).isInstanceOf(IllegalStateException.class);
        assertThat(job(90103,()->store.read("admin",Set.of(77L)))).isEmpty();
        Users.as(Users.principal(77,90103,DataAccess.unrestricted()),()->{assertThatThrownBy(()->store.read("admin",Set.of(77L))).isInstanceOf(AccessDeniedException.class);return null;});
    }
    @Test void capacityIsSharedButExistingSubjectsCanUpdateAndExpiryCreatesANewStamp(){
        var users=LongStream.rangeClosed(1,8).boxed().collect(Collectors.toSet());publish(90104,"admin",users,"notice",10,null);
        var original=job(90104,()->store.read("admin",Set.of(1L))).getFirst();publish(90104,"admin",Set.of(1L),"notice",11,null);
        assertThatThrownBy(()->publish(90104,"admin",Set.of(9L),"notice",12,null)).isInstanceOf(IllegalStateException.class).hasMessageContaining("capacity");
        job(90104,()->{assertThat(signals.selectCount(null)).isEqualTo(8);signals.update(new LambdaUpdateWrapper<SocketSignalEntity>().eq(SocketSignalEntity::getUserId,1L).set(SocketSignalEntity::getExpiresAt,signals.now().minusSeconds(1)));return null;});
        assertThat(job(90104,()->store.read("admin",Set.of(1L)))).isEmpty();assertThat(job(90104,store::purge)).isEqualTo(1);
        publish(90104,"admin",Set.of(1L),"notice",13,null);var recreated=job(90104,()->store.read("admin",Set.of(1L))).getFirst();
        assertThat(recreated.id()).isNotEqualTo(original.id());assertThat(recreated.revision()).isEqualTo(1);assertThat(recreated.objectId()).isEqualTo(13);
    }
    @Test void twoWritersReserveCapacityAtomicallyAcrossIndependentTransactions()throws Exception{
        var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)){
            var a=pool.submit(()->{start.await();publish(90105,"admin",Set.of(1L,2L,3L,4L),"todo",10,null);return true;});
            var b=pool.submit(()->{start.await();publish(90105,"admin",Set.of(5L,6L,7L,8L),"todo",11,null);return true;});
            start.countDown();assertThat(a.get(15,TimeUnit.SECONDS)).isTrue();assertThat(b.get(15,TimeUnit.SECONDS)).isTrue();
        }
        job(90105,()->{assertThat(signals.selectCount(null)).isEqualTo(8);return null;});
        assertThatThrownBy(()->publish(90105,"admin",Set.of(9L),"todo",12,null)).isInstanceOf(IllegalStateException.class);
    }
    @Test void maintenancePurgesAtMostTwoHundredExpiredRowsAndNeverTouchesOtherTenantOrFreshRows(){
        publish(90106,"admin",Set.of(999L),"notice",1,null);publish(90107,"admin",Set.of(999L),"notice",1,null);
        job(90107,()->{signals.update(new LambdaUpdateWrapper<SocketSignalEntity>().eq(SocketSignalEntity::getUserId,999L).set(SocketSignalEntity::getExpiresAt,signals.now().minusSeconds(1)));return null;});
        job(90106,()->{
            // Deliberate over-capacity fixture verifies physical cleanup independently of admission.
            var expired=signals.now().minusSeconds(1);var rows=new ArrayList<SocketSignalEntity>();for(long user=1;user<=201;user++){var row=new SocketSignalEntity();row.setId(IdWorker.getId());row.setPortal("admin");row.setUserId(user);row.setSignalType("notice");row.setObjectId(1L);row.setExpiresAt(expired);rows.add(row);}
            signals.coalesce(90106,rows);return null;
        });
        assertThat(job(90106,store::purge)).isEqualTo(200);
        job(90106,()->{assertThat(signals.selectCount(null)).isEqualTo(2);assertThat(signals.selectCount(new LambdaQueryWrapper<SocketSignalEntity>().eq(SocketSignalEntity::getUserId,999L))).isEqualTo(1);return null;});
        assertThat(job(90106,store::purge)).isEqualTo(1);assertThat(job(90107,()->signals.selectCount(null))).isEqualTo(1);
    }
}
