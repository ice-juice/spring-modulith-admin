package com.sma.admin.tenant.internal.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.sma.admin.common.request.BusinessLedger;
import com.sma.admin.common.request.BusinessLedgerResourceFactory;
import com.sma.admin.tenant.internal.persistence.StorageResourceEntity;
import com.sma.admin.tenant.storage.StorageCredentialResolver;
import java.time.Clock;
import com.sma.admin.support.DeploymentWorkFixture;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class StoragePoolRegistryTests {
    @SuppressWarnings("unchecked")
    @Test void outboxAdmissionCoversConsumerAndPoolLeaseEvenWhenClosingDuringDrain()throws Exception{
        var admission=new DeploymentWorkFixture();var ledgers=mock(BusinessLedgerResourceFactory.class);
        var resource=mock(BusinessLedgerResourceFactory.Resource.class);
        when(ledgers.preparePhysical(any(),anyLong(),any())).thenReturn(resource);
        when(resource.open(any())).thenAnswer(i->{var session=mock(BusinessLedger.Session.class);Runnable release=i.getArgument(0);doAnswer(c->{release.run();return null;}).when(session).close();return session;});
        var provider=(ObjectProvider<StorageCredentialResolver>)mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(ref->new StorageCredentialResolver.Credentials("fixture","secret"));
        var pools=new StoragePoolRegistry(ledgers,provider,new StoragePoolProperties(1,2,2,Duration.ofMinutes(5)),Clock.systemUTC(),admission);
        try(var session=pools.open(source("first",1,2))){}
        var entered=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);
        when(resource.deliverOne(anyList())).thenAnswer(i->{assertThat(admission.active).hasValue(1);entered.countDown();
            if(!release.await(5,java.util.concurrent.TimeUnit.SECONDS))throw new AssertionError("Delivery not released");return true;});
        try(var runner=java.util.concurrent.Executors.newSingleThreadExecutor()){
            var result=runner.submit(()->pools.deliverOne("first",1,java.util.List.of()));
            try{
                assertThat(entered.await(3,java.util.concurrent.TimeUnit.SECONDS)).isTrue();admission.accepting=false;pools.close();
                assertThat(pools.activePools()).isOne();assertThat(admission.active).hasValue(1);
                assertThat(pools.deliverNext("first",1,java.util.List.of())).isEmpty();
            }finally{release.countDown();}
            assertThat(result.get(3,java.util.concurrent.TimeUnit.SECONDS)).isTrue();assertThat(pools.activePools()).isZero();assertThat(admission.active).hasValue(0);
            verify(resource,times(1)).deliverOne(anyList());
        }finally{release.countDown();pools.close();}
    }

    @SuppressWarnings("unchecked")
    @Test void idleEvictionWaitsForQualificationButReleaseAndCloseDoNotAndFailuresReturnPermits(){
        var admission=new DeploymentWorkFixture();var ledgers=mock(BusinessLedgerResourceFactory.class);var resource=mock(BusinessLedgerResourceFactory.Resource.class);
        when(ledgers.preparePhysical(any(),anyLong(),any())).thenReturn(resource);
        when(resource.open(any())).thenAnswer(i->{var session=mock(BusinessLedger.Session.class);Runnable release=i.getArgument(0);doAnswer(c->{release.run();return null;}).when(session).close();return session;});
        var provider=(ObjectProvider<StorageCredentialResolver>)mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(ref->new StorageCredentialResolver.Credentials("fixture","secret"));
        var now=new java.util.concurrent.atomic.AtomicReference<>(java.time.Instant.parse("2026-11-12T00:00:00Z"));
        var clock=mock(Clock.class);when(clock.instant()).thenAnswer(i->now.get());
        try(var pools=new StoragePoolRegistry(ledgers,provider,new StoragePoolProperties(1,2,2,Duration.ofMinutes(5)),clock,admission)){
            try(var session=pools.open(source("first",1,2))){}
            com.sma.admin.support.MaintenanceCallerAssertions.hostOnly(pools::evictIdle,()->pools.deliverOne("first",1,java.util.List.of()));
            verify(resource,never()).deliverOne(anyList());
            when(resource.deliverOne(anyList())).thenThrow(new IllegalStateException("unknown"));
            assertThatThrownBy(()->pools.deliverOne("first",1,java.util.List.of())).isInstanceOf(IllegalStateException.class);assertThat(admission.active).hasValue(0);
            now.set(now.get().plusSeconds(301));admission.accepting=false;pools.evictIdle();assertThat(pools.activePools()).isOne();
            admission.accepting=true;pools.evictIdle();assertThat(pools.activePools()).isZero();assertThat(admission.active).hasValue(0);
        }
    }
    @SuppressWarnings("unchecked")
    @Test void physicalPoolBudgetsAndPinnedLeasesPreventUnboundedGrowth() {
        var ledgers=mock(BusinessLedgerResourceFactory.class);
        var resource=mock(BusinessLedgerResourceFactory.Resource.class);
        when(ledgers.preparePhysical(any(), anyLong(), any())).thenReturn(resource);
        when(resource.open(any())).thenAnswer(invocation -> {
            var release=(Runnable)invocation.getArgument(0);
            var session=mock(BusinessLedger.Session.class);
            var closed=new AtomicBoolean();
            doAnswer(call -> { if (closed.compareAndSet(false,true)) release.run(); return null; }).when(session).close();
            return session;
        });
        var provider=(ObjectProvider<StorageCredentialResolver>)mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(ref -> new StorageCredentialResolver.Credentials("fixture", "fixture-secret"));
        var budget=new StoragePoolProperties(1,2,2,Duration.ofMinutes(5));
        try (var pools=new StoragePoolRegistry(ledgers,provider,budget,Clock.systemUTC(),new DeploymentWorkFixture())) {
            var first=source("first",1,2);
            var second=source("second",1,2);
            var s1=pools.open(first);
            assertThat(pools.activePools()).isEqualTo(1);
            assertThatThrownBy(() -> pools.open(second)).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("pool budget");
            assertThatThrownBy(() -> pools.open(source("first",1,1))).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("changed in place");
            var s2=pools.open(first); var s3=pools.open(first); var s4=pools.open(first);
            assertThatThrownBy(() -> pools.open(first)).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("lease budget");
            s1.close(); s1.close(); s2.close(); s3.close(); s4.close();
            try (var next=pools.open(second)) { assertThat(pools.activePools()).isEqualTo(1); }
        }
    }

    @SuppressWarnings("unchecked")
    @Test void credentialAndPreparationFailuresDoNotReserveCapacity() {
        var ledgers=mock(BusinessLedgerResourceFactory.class);
        var providers=(ObjectProvider<StorageCredentialResolver>)mock(ObjectProvider.class);
        var budget=new StoragePoolProperties(1,2,2,Duration.ofMinutes(5));
        var first=source("first",1,2);
        try(var pools=new StoragePoolRegistry(ledgers,providers,budget,Clock.systemUTC(),new DeploymentWorkFixture())) {
            assertThatThrownBy(()->pools.open(first)).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("resolver");
            assertThat(pools.activePools()).isZero();
            when(providers.getIfAvailable()).thenReturn(ref->null);
            assertThatThrownBy(()->pools.open(first)).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("unavailable");
            assertThat(pools.activePools()).isZero();
            when(providers.getIfAvailable()).thenReturn(ref->new StorageCredentialResolver.Credentials("fixture","secret"));
            when(ledgers.preparePhysical(any(),anyLong(),any())).thenThrow(new IllegalStateException("fixture preparation"));
            assertThatThrownBy(()->pools.open(first)).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("fixture preparation");
            assertThat(pools.activePools()).isZero();
        }
    }

    @SuppressWarnings("unchecked")
    @Test void shutdownKeepsLeasedPoolUntilTheLastSessionCloses() {
        var ledgers=mock(BusinessLedgerResourceFactory.class);
        var resource=mock(BusinessLedgerResourceFactory.Resource.class);
        when(ledgers.preparePhysical(any(),anyLong(),any())).thenReturn(resource);
        when(resource.open(any())).thenAnswer(invocation->{
            var release=(Runnable)invocation.getArgument(0);
            var session=mock(BusinessLedger.Session.class);
            var once=new AtomicBoolean();
            doAnswer(call->{if(once.compareAndSet(false,true))release.run();return null;}).when(session).close();
            return session;
        });
        var provider=(ObjectProvider<StorageCredentialResolver>)mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(ref->new StorageCredentialResolver.Credentials("fixture","secret"));
        var pools=new StoragePoolRegistry(ledgers,provider,new StoragePoolProperties(1,2,2,Duration.ofMinutes(5)),Clock.systemUTC(),new DeploymentWorkFixture());
        var session=pools.open(source("first",1,2));
        pools.close();
        assertThat(pools.activePools()).isEqualTo(1);
        assertThatThrownBy(()->pools.open(source("first",1,2))).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("closing");
        session.close();
        assertThat(pools.activePools()).isZero();
    }

    private static StorageResourceEntity source(String id,long revision,int max) {
        var row=new StorageResourceEntity();
        row.setResourceId(id); row.setRevision(revision); row.setState("REGISTERED");
        row.setDbHost("127.0.0.1"); row.setDbPort(3306); row.setDatabaseName("business_test");
        row.setTlsMode("DISABLED"); row.setSecretRef("secret/test");
        row.setPoolMax(max); row.setConnectTimeoutMillis(1000);
        return row;
    }
}
