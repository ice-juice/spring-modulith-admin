package com.sma.admin.common.internal.security;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import com.sma.admin.common.*;
import com.sma.admin.common.security.*;

class RequestRejectionSignalsTests {
    private final RequestSourceScope scope=new RequestSourceScope();
    private final DefaultListableBeanFactory beans=new DefaultListableBeanFactory();
    private final SecuritySignals target=mock(SecuritySignals.class);
    private final RequestRejectionSignals signals=new RequestRejectionSignals(scope,beans.getBeanProvider(SecuritySignals.class));
    private final RequestSource source=new RequestSource("192.0.2.1","192.0.2.1",RequestSource.Basis.DIRECT,0);
    private ErrorCode code(String key){return new ErrorCode(){public String key(){return key;}public int status(){return 400;}};}
    @Test void exactReviewedKeysSelectFactAndNeverInterpretLocalizedMessageOrStatus(){
        beans.registerSingleton("signals",target);
        var examples=Map.of("auth.error.invalid-credentials",SecuritySignal.Fact.AUTHENTICATION_REJECTED,
                "common.error.forbidden",SecuritySignal.Fact.AUTHORIZATION_REJECTED,"common.request.too-large",SecuritySignal.Fact.REQUEST_INPUT_REJECTED,
                "common.request.rate-limited",SecuritySignal.Fact.REQUEST_BUDGET_REJECTED,"openapi.error.replay",SecuritySignal.Fact.SIGNATURE_REPLAY_REJECTED);
        examples.forEach((key,fact)->{try(var bound=scope.open(source)){signals.response(code(key));}verify(target).observe(fact);});
        verifyNoMoreInteractions(target);
    }
    @Test void ordinaryMissingConflictCompletionAndInfrastructureFailureDoNotBecomeSecuritySignals(){
        beans.registerSingleton("signals",target);
        for(String key:List.of("common.error.not-found","common.error.data-not-found","common.error.internal","common.error.unavailable",
                "common.request.completed","common.request.conflict","common.request.processing","common.request.unavailable",
                "auth.error.screen-locked","file.error.storage","file.error.invalid-image","identity.error.version-conflict","auth.error.invalid-credentials-extra"))
            try(var bound=scope.open(source)){signals.response(code(key));}
        verifyNoInteractions(target);
    }
    @Test void explicitObservationWinsAndSameRequestNeverRetriesEvenWhenCollectorRejects(){
        beans.registerSingleton("signals",target);when(target.observe(any())).thenReturn(SecuritySignals.Admission.REJECTED);
        try(var bound=scope.open(source)){
            signals.observed(SecuritySignal.Fact.TENANT_ENTRY_REJECTED);signals.response(CommonErrorCode.NOT_FOUND);signals.response(CommonErrorCode.FORBIDDEN);
            signals.observed(SecuritySignal.Fact.BROWSER_ORIGIN_REJECTED);
        }
        verify(target).observe(SecuritySignal.Fact.TENANT_ENTRY_REJECTED);verifyNoMoreInteractions(target);
        try(var bound=scope.open(source)){signals.response(CommonErrorCode.FORBIDDEN);}verify(target).observe(SecuritySignal.Fact.AUTHORIZATION_REJECTED);
    }
    @Test void unavailableCollectorCannotChangeOriginalControlFlowAndDoesNotRetry(){
        beans.registerSingleton("signals",target);when(target.observe(any())).thenThrow(new IllegalStateException("unavailable"));
        try(var bound=scope.open(source)){signals.response(CommonErrorCode.UNAUTHORIZED);signals.response(CommonErrorCode.UNAUTHORIZED);}
        verify(target,times(1)).observe(SecuritySignal.Fact.AUTHENTICATION_REJECTED);
        signals.response(CommonErrorCode.UNAUTHORIZED);verifyNoMoreInteractions(target);
    }
    @Test void missingOrAmbiguousProviderAndNoRequestScopeDoNotInferIdentity(){
        signals.response(CommonErrorCode.UNAUTHORIZED);
        try(var bound=scope.open(source)){signals.response(CommonErrorCode.UNAUTHORIZED);}
        beans.registerSingleton("one",target);beans.registerSingleton("two",mock(SecuritySignals.class));
        try(var bound=scope.open(source)){signals.response(CommonErrorCode.UNAUTHORIZED);}verifyNoInteractions(target);
    }
}
