package com.sma.admin.rule.internal.service;

import static org.assertj.core.api.Assertions.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import com.sma.admin.common.cache.CacheDescriptor;
import com.sma.admin.common.security.*;
import com.sma.admin.rule.RuleDefinition.*;
import com.sma.admin.support.Users;

class RuleCacheAdapterTests {
    @Test void owningAdapterPreservesActivePlansAndOtherTenantsAndRequiresAllScope() throws Exception {
        var expressions=new ExpressionEngine();var operations=new RuleOperationsService(null,null,null,null,expressions);var adapter=new RuleCacheAdapter(operations);
        var actor=new SmaPrincipal(101,"maintainer",98250,"admin",null,false,DataAccess.unrestricted(),Set.of("rule:maintenance:view","rule:maintenance:clean"),"UTC");
        Users.as(actor,()->{expressions.validate("1 + 2");return null;});
        TenantScope.within(98251,()->{expressions.validate("3 + 4");return null;});
        var flow=new Flow("start",List.of(new Node("start",NodeKind.START,null,null,"result",null,null,null,null,null,null),new Node("result",NodeKind.RESULT,"1 + 2",null,null,null,null,null,null,null,null)));
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        try(var pool=Executors.newSingleThreadExecutor()){
            var running=pool.submit(()->TenantScope.within(98250,()->{RuleFlowRuntime.execute("native_resource_test",RuleFlowPlan.compile(flow),id->{if(id.equals("result")){entered.countDown();try{if(!release.await(10,TimeUnit.SECONDS))throw new IllegalStateException("test timeout");}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IllegalStateException(interrupted);}}return null;});return null;}));
            try{
                assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();
                Users.as(actor,()->{var outcome=adapter.execute(CacheDescriptor.Operation.CLEAR);assertThat(outcome.affectedEntries()).isEqualTo(1);assertThat(outcome.remainingEntries()).isEqualTo(1);return null;});
                TenantScope.within(98251,()->{assertThat(expressions.tenantCacheSize()).isEqualTo(1);return null;});
                var self=new SmaPrincipal(101,"maintainer",98250,"admin",null,false,DataAccess.own(),actor.permissions(),"UTC");
                Users.as(self,()->{assertThatThrownBy(()->adapter.execute(CacheDescriptor.Operation.CLEAR)).isInstanceOf(AccessDeniedException.class);return null;});
            }finally{release.countDown();}
            running.get(10,TimeUnit.SECONDS);
        }
        Users.as(actor,()->{assertThat(adapter.execute(CacheDescriptor.Operation.CLEAR).affectedEntries()).isEqualTo(1);return null;});
    }
}
