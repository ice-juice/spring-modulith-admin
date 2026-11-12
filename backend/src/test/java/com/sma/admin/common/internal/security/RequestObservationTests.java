package com.sma.admin.common.internal.security;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.mock.web.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.resource.ResourceHttpRequestHandler;
import com.sma.admin.common.*;
import com.sma.admin.common.security.*;
import com.sma.admin.common.internal.cache.*;

class RequestObservationTests {
    private final RequestSourceScope scope=new RequestSourceScope();
    private final RequestSource source=new RequestSource("192.0.2.1","192.0.2.1",RequestSource.Basis.DIRECT,0);
    private final RequestObservationInterceptor interceptor=new RequestObservationInterceptor(scope);
    private final SecuritySignalCaptureService capture=new SecuritySignalCaptureService(scope,new CacheNodeIdentity(new CacheNodeProperties("test","node",List.of("node"),false)));
    @RequestMapping("/api/fixture") static class Fixture {
        @GetMapping({"/items/{id}","/lookups/{id}"}) public void lookup(){}
    }
    private HandlerMethod handler()throws Exception{return new HandlerMethod(new Fixture(),Fixture.class.getMethod("lookup"));}
    private MockHttpServletRequest request(String template){
        var request=new MockHttpServletRequest("GET","/api/fixture/items/secret-value");request.setQueryString("token=secret-query");
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE,template);return request;
    }
    @Test void selectedTemplateMustMatchDeclaredHandlerAndNeverCopiesPathVariablesOrQuery()throws Exception{
        for(String template:List.of("/api/fixture/items/{id}","/api/fixture/lookups/{id}"))
            try(var bound=scope.open(source,"GET")){
                assertThat(interceptor.preHandle(request(template),new MockHttpServletResponse(),handler())).isTrue();
                scope.response(403);var observed=capture.capture(SecuritySignal.Fact.AUTHORIZATION_REJECTED);
                assertThat(observed.metadata().http()).isEqualTo(new SecuritySignal.Http(SecuritySignal.Method.GET,SecuritySignal.Route.MATCHED,template,403));
                assertThat(observed.toString()).doesNotContain("secret-value","secret-query");
            }
    }
    @Test void unverifiedOrOversizedPatternAndUnknownMethodRemainBounded()throws Exception{
        for(String pattern:List.of("/api/fixture/items/secret-value","/api/other/{id}","/"+"a".repeat(300)))
            try(var bound=scope.open(source,"private-method-value")){
                interceptor.preHandle(request(pattern),new MockHttpServletResponse(),handler());
                assertThat(scope.http()).isEqualTo(new SecuritySignal.Http(SecuritySignal.Method.OTHER,SecuritySignal.Route.UNRESOLVED,null,null));
                assertThat(capture.capture(SecuritySignal.Fact.AUTHORIZATION_REJECTED).toString()).doesNotContain("private-method-value","secret-value");
            }
    }
    @Test void onlyFrameworkResourceOrAbsentHandlerCanEmitUnmappedObservation()throws Exception{
        var events=new ArrayList<SecuritySignal>();var sink=mock(SecuritySignals.class);var beans=new DefaultListableBeanFactory();beans.registerSingleton("signals",sink);
        doAnswer(call->{events.add(capture.capture(call.getArgument(0)));return SecuritySignals.Admission.QUEUED_VOLATILE;}).when(sink).observe(any());
        var signals=new RequestRejectionSignals(scope,beans.getBeanProvider(SecuritySignals.class));
        try(var bound=scope.open(source,"GET")){
            interceptor.preHandle(request("/api/fixture/items/{id}"),new MockHttpServletResponse(),handler());
            signals.unmapped(false);signals.unmapped(true);signals.response(CommonErrorCode.NOT_FOUND);assertThat(events).isEmpty();
        }
        try(var bound=scope.open(source,"GET")){
            interceptor.preHandle(request("/**"),new MockHttpServletResponse(),new ResourceHttpRequestHandler());
            signals.unmapped(false);signals.response(CommonErrorCode.NOT_FOUND);signals.response(CommonErrorCode.FORBIDDEN);
            assertThat(events).hasSize(1);assertThat(events.getFirst().metadata().http()).isEqualTo(new SecuritySignal.Http(SecuritySignal.Method.GET,SecuritySignal.Route.UNMAPPED,null,404));
            assertThat(events.getFirst().fact()).isEqualTo(SecuritySignal.Fact.UNMAPPED_REQUEST_OBSERVED);
            assertThat(events.getFirst().fact().assessment()).isEqualTo(SecuritySignal.Assessment.NO_ATTACK_INFERENCE);
            assertThat(events.getFirst().fact().severity()).isEqualTo(SecuritySignal.Severity.LOW);
        }
        try(var bound=scope.open(source,"GET")){signals.unmapped(true);}assertThat(events).hasSize(2);
    }
    @Test void responseDecisionIsCapturedOnceAndDoesNotLeakToNextRequestOrWorker()throws Exception{
        var sink=mock(SecuritySignals.class);var beans=new DefaultListableBeanFactory();beans.registerSingleton("signals",sink);
        var events=new ArrayList<SecuritySignal>();doAnswer(call->{events.add(capture.capture(call.getArgument(0)));return SecuritySignals.Admission.REJECTED;}).when(sink).observe(any());
        var signals=new RequestRejectionSignals(scope,beans.getBeanProvider(SecuritySignals.class));
        try(var bound=scope.open(source,"POST")){
            signals.response(CommonErrorCode.UNAUTHORIZED,200);signals.response(CommonErrorCode.UNAUTHORIZED,401);
            assertThat(events).hasSize(1);assertThat(events.getFirst().metadata().http().status()).isEqualTo(200);
            try(var worker=java.util.concurrent.Executors.newSingleThreadExecutor()){
                assertThat(worker.submit(()->capture.capture(SecuritySignal.Fact.AUTHORIZATION_REJECTED)).get().metadata().http()).isNull();
            }
        }
        assertThat(capture.capture(SecuritySignal.Fact.AUTHORIZATION_REJECTED).metadata().http()).isNull();
        try(var bound=scope.open(source,"GET")){assertThat(scope.http().status()).isNull();assertThat(scope.http().route()).isEqualTo(SecuritySignal.Route.UNRESOLVED);}
    }
}
