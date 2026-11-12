package com.sma.admin.common.internal.security;

import static org.assertj.core.api.Assertions.*;
import static com.sma.admin.common.security.SecuritySignal.*;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import com.sma.admin.common.security.*;

class SecuritySignalCaptureTests {
    private final RequestSourceScope sources=new RequestSourceScope();
    private final com.sma.admin.common.internal.cache.CacheNodeIdentity node=new com.sma.admin.common.internal.cache.CacheNodeIdentity(
            new com.sma.admin.common.internal.cache.CacheNodeProperties("test-group","node-a",List.of("node-a"),false));
    private final SecuritySignalCaptureService capture=new SecuritySignalCaptureService(sources,node);
    private SecuritySignal signal(){return capture.capture(Fact.AUTHORIZATION_REJECTED);}
    private void authenticate(Object principal){SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal,"not-retained-password",List.of()));}
    private SmaPrincipal admin(long tenant,String portal){return new SmaPrincipal(77,"private-username",tenant,portal,null,true,DataAccess.unrestricted(),Set.of(),"UTC");}
    @AfterEach void clear(){SecurityContextHolder.clearContext();}
    @Test void anonymousKnownHostAndAmbientTenantCannotBecomeTenantAttribution(){
        assertThat(signal().attribution().scope()).isEqualTo(Scope.PLATFORM);
        TenantScope.within(91,()->{assertThat(signal().attribution().tenantId()).isNull();return null;});
        try(var request=TenantEntryScope.beginRequest();var entry=TenantEntryScope.open(new TenantEntry(91,92,"tenant.test","admin",1))){
            assertThat(signal().attribution()).isEqualTo(new Attribution(Scope.PLATFORM,Identity.ANONYMOUS,null,null,null,null,null,null));
            SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken("key","anonymousUser",List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
            assertThat(signal().attribution().tenantId()).isNull();
        }
    }
    @Test void onlyRecognizedAuthenticatedPrincipalsSupplyTenantAndBoundedSubject(){
        authenticate(admin(91,"admin"));assertThat(signal().attribution()).isEqualTo(new Attribution(Scope.TENANT,Identity.ADMIN_USER,91L,"77","admin",null,null,null));
        assertThat(signal().toString()).doesNotContain("private-username","not-retained-password");
        authenticate(new AppUserPrincipal(12,91,13,14,"shop",15));
        assertThat(signal().attribution()).isEqualTo(new Attribution(Scope.TENANT,Identity.APP_USER,91L,"12",null,"shop",13L,14L));
        authenticate(SmaPrincipal.forApplication(91,"admin","application_123"));
        assertThat(signal().attribution()).isEqualTo(new Attribution(Scope.TENANT,Identity.APPLICATION,91L,"application_123","admin",null,null,null));
        authenticate("arbitrary-principal");assertThat(signal().attribution().identity()).isEqualTo(Identity.UNRECOGNIZED);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.unauthenticated(admin(91,"admin"),"secret"));
        assertThat(signal().attribution().identity()).isEqualTo(Identity.ANONYMOUS);
    }
    @Test void tenantOrPortalMismatchFallsBackToPlatformWithoutLeakingPrincipalOrEntry(){
        try(var request=TenantEntryScope.beginRequest();var entry=TenantEntryScope.open(new TenantEntry(91,92,"tenant.test","admin",1))){
            authenticate(admin(99,"admin"));assertThat(signal().attribution()).isEqualTo(new Attribution(Scope.PLATFORM,Identity.UNRECOGNIZED,null,null,null,null,null,null));
            authenticate(admin(91,"other"));assertThat(signal().attribution().scope()).isEqualTo(Scope.PLATFORM);
        }
    }
    @Test void jobAttributionOmitsUnboundedWorkIdAndCannotBeLaunderedFromHttp(){
        ActorContext.withinJob(91,"private:email@example.test",()->{
            assertThat(signal().attribution()).isEqualTo(new Attribution(Scope.TENANT,Identity.SYSTEM_JOB,91L,null,null,null,null,null));
            assertThat(signal().toString()).doesNotContain("email@example.test");return null;
        });
        try(var binding=sources.open(new RequestSource("127.0.0.1","127.0.0.1",RequestSource.Basis.DIRECT,0))){
            ActorContext.withinJob(91,"test",()->{assertThat(signal().attribution().scope()).isEqualTo(Scope.PLATFORM);return null;});
        }
    }
    @Test void correlationIsServerGeneratedPerRequestDistinctFromEventAndNeverInherited()throws Exception{
        SecuritySignal first;
        try(var binding=sources.open(new RequestSource("192.0.2.1","192.0.2.1",RequestSource.Basis.DIRECT,0))){
            first=signal();var next=signal();assertThat(next.correlationId()).isEqualTo(first.correlationId());assertThat(next.eventId()).isNotEqualTo(first.eventId());
            assertThat(first.source().peerAddress()).isEqualTo("192.0.2.1");
            try(var worker=java.util.concurrent.Executors.newSingleThreadExecutor()){
                var async=worker.submit(this::signal).get();assertThat(async.source()).isNull();assertThat(async.correlationId()).isNotEqualTo(first.correlationId());
            }
        }
        assertThat(signal().source()).isNull();assertThat(signal().correlationId()).isNotEqualTo(first.correlationId());
        try(var binding=sources.open(new RequestSource("192.0.2.2","192.0.2.2",RequestSource.Basis.DIRECT,0))){assertThat(signal().correlationId()).isNotEqualTo(first.correlationId());}
    }
    @Test void factsDoNotClaimExploitSuccessAndEnvelopeRejectsFreeformAttribution(){
        for(var fact:Fact.values()){
            assertThat(fact.ruleVersion()).isEqualTo(1);assertThat(fact.category()).isNotNull();assertThat(fact.severity()).isNotNull();
            assertThat(capture.capture(fact).fact()).isSameAs(fact);
        }
        assertThat(Fact.AUTHENTICATION_REJECTED.assessment()).isEqualTo(Assessment.NO_ATTACK_INFERENCE);
        assertThat(Fact.HONEYPOT_OBSERVED.outcome()).isEqualTo(Outcome.OBSERVED);
        assertThatThrownBy(()->capture.capture(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(()->new Attribution(Scope.PLATFORM,Identity.ADMIN_USER,1L,"2","admin",null,null,null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new Attribution(Scope.TENANT,Identity.ADMIN_USER,1L,"9999999999999999999","admin",null,null,null)).isInstanceOf(IllegalArgumentException.class);
        authenticate(SmaPrincipal.forApplication(91,"admin","x\r\nsecret"));assertThat(signal().attribution().scope()).isEqualTo(Scope.PLATFORM);
    }
    @Test void serializedEnvelopeIsBoundedAndCaptureDoesNotChangeAuthentication(){
        var json=tools.jackson.databind.json.JsonMapper.builder().build();
        authenticate(new AppUserPrincipal(Long.MAX_VALUE,Long.MAX_VALUE,Long.MAX_VALUE,Long.MAX_VALUE,"a".repeat(64),Long.MAX_VALUE));
        var original=SecurityContextHolder.getContext().getAuthentication();
        try(var binding=sources.open(new RequestSource("2001:db8:ffff:ffff:ffff:ffff:ffff:ffff","2001:db9:ffff:ffff:ffff:ffff:ffff:ffff",RequestSource.Basis.TRUSTED_CHAIN,32))){
            for(var fact:Fact.values()){
                var signal=capture.capture(fact);byte[] encoded=json.writeValueAsBytes(signal);
                assertThat(encoded.length).isLessThanOrEqualTo(2048);
                assertThat(new String(encoded,java.nio.charset.StandardCharsets.UTF_8)).doesNotContain("not-retained-password","Authorization","Cookie");
                assertThat(json.readValue(encoded,SecuritySignal.class)).isEqualTo(signal);
            }
        }
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(original);
    }
    @Test void captureBeanUsesTheSameRequestSourceBindingAsTheFilter(){
        new org.springframework.boot.test.context.runner.ApplicationContextRunner()
                .withBean(com.sma.admin.common.internal.cache.CacheNodeIdentity.class,()->node)
                .withBean(RequestSourceScope.class).withBean(SecuritySignalCaptureService.class)
                .run(context->{
                    assertThat(context).hasSingleBean(SecuritySignalCapture.class).hasSingleBean(RequestSourceQuery.class);
                    var bound=context.getBean(RequestSourceScope.class);
                    try(var binding=bound.open(new RequestSource("192.0.2.1","192.0.2.1",RequestSource.Basis.DIRECT,0))){
                        assertThat(context.getBean(SecuritySignalCapture.class).capture(Fact.REQUEST_INPUT_REJECTED).source()).isEqualTo(bound.current().orElseThrow());
                    }
                    assertThat(context.getBean(SecuritySignalCapture.class).capture(Fact.REQUEST_INPUT_REJECTED).source()).isNull();
                });
    }
    @Test void observerIsAProcessSnapshotFromDeploymentIdentityEvenWithoutHttp(){
        var first=signal();var next=signal();
        assertThat(first.metadata().observer()).isEqualTo(new Observer("test-group","node-a",java.util.UUID.fromString(node.processId())));
        assertThat(next.metadata().observer()).isEqualTo(first.metadata().observer());assertThat(first.metadata().http()).isNull();
        var another=new com.sma.admin.common.internal.cache.CacheNodeIdentity(node.properties());
        assertThat(new SecuritySignalCaptureService(sources,another).capture(Fact.AUTHORIZATION_REJECTED).metadata().observer().processId())
                .isNotEqualTo(first.metadata().observer().processId());
    }
}
