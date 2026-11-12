package com.sma.admin.common.internal.security;

import com.sma.admin.common.ErrorCode;
import com.sma.admin.common.security.SecuritySignal.Fact;
import com.sma.admin.common.security.SecuritySignals;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** Audited response keys, not arbitrary status/message/body inference. Captures before authentication unwinds. */
@Component
public class RequestRejectionSignals {
    private final RequestSourceScope scope;
    private final ObjectProvider<SecuritySignals> signals;
    public RequestRejectionSignals(RequestSourceScope scope,ObjectProvider<SecuritySignals> signals){this.scope=scope;this.signals=signals;}
    public void response(ErrorCode code){if(code!=null)response(code,code.status());}
    public void response(ErrorCode code,int status){
        if(code==null)return;
        Fact fact=switch(code.key()){
            case "common.error.unauthorized", "auth.error.invalid-credentials", "auth.error.invalid-captcha", "auth.error.invalid-challenge",
                 "auth.error.invalid-totp", "auth.error.security-password-invalid", "openapi.error.invalid-credentials",
                 "identity.error.invalid-google-proof", "identity.error.invalid-session", "identity.error.invalid-email-challenge",
                 "identity.error.invalid-password-login", "identity.error.invalid-reauthentication" -> Fact.AUTHENTICATION_REJECTED;
            case "common.error.forbidden", "openapi.error.invalid-signature" -> Fact.AUTHORIZATION_REJECTED;
            case "common.error.bad-request", "common.error.validation-failed", "common.error.payload-too-large", "common.error.unsupported-media-type",
                 "common.request.too-large", "common.request.key-required", "file.error.too-large" -> Fact.REQUEST_INPUT_REJECTED;
            case "common.request.rate-limited", "common.request.busy", "auth.error.rate-limited", "openapi.error.rate-limit",
                 "identity.error.login-limit", "identity.error.email-challenge-limit", "identity.error.reauthentication-limit", "file.error.upload-limit" -> Fact.REQUEST_BUDGET_REJECTED;
            case "openapi.error.replay" -> Fact.SIGNATURE_REPLAY_REJECTED;
            default -> null;
        };
        if(fact!=null)observed(fact,status);
    }
    public void observed(Fact fact){observed(fact,null);}
    public void unmapped(boolean noHandler){if(scope.unmapped(noHandler))observed(Fact.UNMAPPED_REQUEST_OBSERVED,404);}
    public void observed(Fact fact,Integer status){
        if(fact==null||!scope.claimRejection())return;
        try{scope.response(status);var target=signals.getIfUnique();if(target!=null)target.observe(fact);}
        catch(RuntimeException unavailable){/* Detection cannot change the original denial or retry a failed observation. */}
    }
}
