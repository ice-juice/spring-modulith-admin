package com.sma.admin.common.internal;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.context.request.*;
import tools.jackson.databind.json.JsonMapper;
import com.sma.admin.common.request.*;
import com.sma.admin.common.security.*;
import com.sma.admin.support.Users;

class RequestGovernanceAdvisorTests {
    @Test void queryAndNativePoliciesUseAdmissionWithoutClaimingALocalReceipt() {
        try(var context=new AnnotationConfigApplicationContext(Configuration.class)) {
            when(context.getBean(RequestAdmission.class).execute(any())).thenAnswer(call->{
                RequestExecution.Work<?> work=call.getArgument(0);work.authorize();return work.execute();
            });
            var controller=context.getBean(ProtocolProbe.class);
            try {
                Users.as(Users.principal(7,7,DataAccess.unrestricted()),()->{
                    for(boolean nativeCall:new boolean[]{true,false}) {
                        var request=new MockHttpServletRequest("POST","/api/common/protocol");
                        request.setAttribute(org.springframework.web.servlet.HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE,"/api/common/protocol");
                        var response=new MockHttpServletResponse();
                        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request,response));
                        assertThat(nativeCall?controller.nativeCall():controller.query()).isEqualTo(nativeCall?"native":"query");
                        assertThat(response.getHeader("X-Request-Policy")).isEqualTo(nativeCall?"NATIVE":"QUERY");
                        assertThat(response.getHeader("X-Operation-Id")).isNull();
                    }
                    return null;
                });
                var execution=context.getBean(RequestExecution.class);
                verify(execution,never()).executeLocal(any(),any());
                var writeBudget=org.mockito.ArgumentCaptor.forClass(String.class);
                var readBudget=org.mockito.ArgumentCaptor.forClass(String.class);
                verify(execution).claimBudget(writeBudget.capture(),eq(60),eq(java.time.Duration.ofMinutes(1)));
                verify(execution).claimBudget(readBudget.capture(),eq(240),eq(java.time.Duration.ofMinutes(1)));
                assertThat(writeBudget.getValue()).isNotEqualTo(readBudget.getValue());
                verify(execution).claimBudget(anyString(),eq(1),eq(java.time.Duration.ofMillis(500)));
            } finally {RequestContextHolder.resetRequestAttributes();}
        }
    }
    @Test void personalObjectLocksSeparateOwnersAndObjectsWhileSharedEditsStillConflict() {
        try(var context=new AnnotationConfigApplicationContext(Configuration.class)) {
            var commands=new java.util.ArrayList<RequestCommand>();
            when(context.getBean(RequestExecution.class).executeLocal(any(),any())).thenAnswer(call->{
                commands.add(call.getArgument(0));
                var now=java.time.Instant.now();
                return new RequestExecution.Outcome<>(new RequestReceipt(java.util.UUID.randomUUID().toString(),"common.scope",RequestReceipt.State.SUCCEEDED,now,now),false,"ok");
            });
            var controller=context.getBean(ScopeProbe.class);
            try {
                invokeScope(controller,7,"1",1,true);
                invokeScope(controller,8,"1",1,true);
                invokeScope(controller,7,"2",2,true);
                invokeScope(controller,7,"0001",1,true);
                invokeScope(controller,7,"1",1,false);
                invokeScope(controller,8,"1",1,false);
                assertThat(commands.get(0).resource()).isNotEqualTo(commands.get(1).resource()).isNotEqualTo(commands.get(2).resource());
                assertThat(commands.get(0).resource()).isEqualTo(commands.get(3).resource());
                assertThat(commands.get(4).resource()).isEqualTo(commands.get(5).resource()).isNotEqualTo(commands.get(0).resource());
                assertThat(commands).extracting(RequestCommand::conflictGroup).containsOnly("common.scope");
            } finally {RequestContextHolder.resetRequestAttributes();}
        }
    }
    private void invokeScope(ScopeProbe controller,long actor,String rawId,long id,boolean personal) {
        var request=new MockHttpServletRequest("PUT","/api/common/scope/"+rawId);
        request.addHeader("Idempotency-Key",java.util.UUID.randomUUID().toString());
        request.setAttribute(org.springframework.web.servlet.HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE,"/api/common/scope/{id}");
        request.setAttribute(org.springframework.web.servlet.HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE,Map.of("id",rawId));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request,new MockHttpServletResponse()));
        Users.as(Users.principal(actor,7,DataAccess.unrestricted()),()->personal?controller.owned(id):controller.shared(id));
    }
    @Test void infrastructureAutoProxyCreatorActuallyAppliesTheGuardAfterMethodSecurity() {
        try(var context=new AnnotationConfigApplicationContext(Configuration.class)) {
            var controller=context.getBean(Probe.class);
            assertThat(org.springframework.aop.support.AopUtils.isAopProxy(controller)).isTrue();
            var request=new MockHttpServletRequest("POST","/api/common/probe");
            request.setAttribute(org.springframework.web.servlet.HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE,"/api/common/probe");
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request,new MockHttpServletResponse()));
            try {
                Users.as(Users.principal(7,7,DataAccess.unrestricted()),()->{
                    assertThatThrownBy(controller::write).isInstanceOfSatisfying(RequestRejected.class,
                            failure->assertThat(failure.reason()).isEqualTo(RequestRejected.Reason.KEY_REQUIRED));
                    return null;
                });
                assertThat(controller.calls()).isZero();
                verify(context.getBean(RequestExecution.class)).claimBudget(anyString(),eq(60),any());
            } finally { RequestContextHolder.resetRequestAttributes(); }
        }
    }
    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods=false)
    @EnableMethodSecurity(proxyTargetClass=true)
    @Import({RequestGovernanceConfiguration.class,com.sma.admin.common.internal.security.RequestInputFingerprint.class})
    static class Configuration {
        @Bean RequestExecution execution(){return mock(RequestExecution.class);}
        @Bean JsonMapper json(){return JsonMapper.builder().build();}
        @Bean Probe probe(){return new Probe();}
        @Bean ScopeProbe scopeProbe(){return new ScopeProbe();}
        @Bean ProtocolProbe protocolProbe(){return new ProtocolProbe();}
        @Bean RequestAdmission admission(){return mock(RequestAdmission.class);}
        @Bean com.sma.admin.common.internal.security.RequestGovernanceProperties limits(){return new com.sma.admin.common.internal.security.RequestGovernanceProperties("","v1","",100000L,8);}
    }
    @GovernedApi(group="common.protocol")
    public static class ProtocolProbe implements RequestAuthorizer {
        @PostMapping @PermitLoggedIn @GovernedApi(group="common.protocol",mode=GovernedApi.Mode.NATIVE,reason="Test native protocol")
        public String nativeCall(){return "native";}
        @PostMapping @PermitLoggedIn @GovernedApi(group="common.protocol",mode=GovernedApi.Mode.QUERY,reason="Test body-based query")
        public String query(){return "query";}
        @Override public void authorizeRequest(String method,Map<String,String> variables){CurrentUser.require();}
    }
    @GovernedApi(group="common.scope")
    public static class ScopeProbe implements RequestAuthorizer {
        @org.springframework.web.bind.annotation.PutMapping @PermitLoggedIn
        @GovernedApi(group="common.scope",ownResource=true)
        public String owned(@org.springframework.web.bind.annotation.PathVariable long id){return "owned";}
        @org.springframework.web.bind.annotation.PutMapping @PermitLoggedIn
        public String shared(@org.springframework.web.bind.annotation.PathVariable long id){return "shared";}
        @Override public void authorizeRequest(String method,Map<String,String> variables){CurrentUser.require();}
    }
    @GovernedApi(group="common.probe")
    public static class Probe implements RequestAuthorizer {
        int calls;
        public int calls(){return calls;}
        @PostMapping @PermitLoggedIn public String write(){calls++;return "must not run";}
        @Override public void authorizeRequest(String method,Map<String,String> variables){CurrentUser.require();}
    }
}
