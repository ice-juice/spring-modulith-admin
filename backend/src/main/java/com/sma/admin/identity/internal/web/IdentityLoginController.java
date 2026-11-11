package com.sma.admin.identity.internal.web;

import java.time.Duration;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import com.sma.admin.common.*;
import com.sma.admin.common.security.*;
import com.sma.admin.identity.internal.ApplicationHttp;
import com.sma.admin.identity.internal.ApplicationSessionCookies;
import com.sma.admin.identity.internal.service.*;

@RestController @RequestMapping("/app/identity")
public class IdentityLoginController {
    public record Start(@NotBlank @Size(max=2048)String callbackUri,@NotNull IdentityLoginStore.Flow flow,@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{43}")String pkceChallenge){}
    public record Complete(@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{43}")String state,@Size(max=43)String proof,
            @NotBlank @Pattern(regexp="[A-Za-z0-9._~-]{43,128}")String verifier,@NotBlank @Size(max=2048)String callbackUri,
            @NotNull IdentityLoginStore.Flow flow,@NotBlank @Size(max=16384)String credential){
        @Override public String toString(){return "LoginCompletion[REDACTED]";}
    }
    public record Refresh(@Size(max=43)String refreshToken){@Override public String toString(){return "SessionRefresh[REDACTED]";}}
    private final GoogleLoginCoordinator login;private final IdentitySessionStore sessions;private final IdentityClock clock;private final ApplicationSessionCookies cookies;
    public IdentityLoginController(GoogleLoginCoordinator login,IdentitySessionStore sessions,IdentityClock clock,ApplicationSessionCookies cookies){this.login=login;this.sessions=sessions;this.clock=clock;this.cookies=cookies;}
    @GetMapping("/bootstrap") @PermitAnonymous
    public ApiResponse<IdentityApplicationViews.Bootstrap> bootstrap(HttpServletRequest request){
        var client=ApplicationHttp.client(request);String csrf=null;
        if(client.web())csrf=((CsrfToken)request.getAttribute(CsrfToken.class.getName())).getToken();
        return ApiResponse.ok(new IdentityApplicationViews.Bootstrap(client.name(),client.authority().application(),client.authority().platform(),
                client.googleEnabled(),client.googleClientId(),client.registrationEnabled(),csrf));
    }
    @PostMapping("/google/transactions") @PermitAnonymous
    public ApiResponse<IdentityApplicationViews.LoginStarted> begin(@Valid @RequestBody Start input,HttpServletRequest request,HttpServletResponse response){
        var client=ApplicationHttp.client(request);String origin=(String)request.getAttribute(ApplicationHttp.ORIGIN);
        var started=ActorContext.withinJob(client.authority().tenantId(),"identity-http-begin",()->login.begin(new IdentityLoginStore.Start(client.handle(),input.callbackUri(),origin,
                input.flow(),input.pkceChallenge(),request.getRemoteAddr())));
        if(client.web())ApplicationHttp.cookie(response,client,"login-proof",started.proof(),true,Duration.between(clock.now(),started.expiresAt()));
        return ApiResponse.ok(new IdentityApplicationViews.LoginStarted(started.state(),client.web()?null:started.proof(),started.nonce(),
                started.authorizationUri()==null?null:started.authorizationUri().toString(),started.googleClientId(),started.platform(),started.expiresAt()));
    }
    @PostMapping("/google/completions") @PermitAnonymous
    public ApiResponse<IdentityApplicationViews.Session> complete(@Valid @RequestBody Complete input,HttpServletRequest request,HttpServletResponse response){
        var client=ApplicationHttp.client(request);if(client.web() && input.proof()!=null)throw new BizException(CommonErrorCode.BAD_REQUEST);
        String proof=client.web()?ApplicationHttp.cookie(request,client,"login-proof"):input.proof();
        var command=new IdentityLoginStore.Completion(input.state(),proof,input.verifier(),client.handle(),input.callbackUri(),(String)request.getAttribute(ApplicationHttp.ORIGIN),input.flow(),input.credential());
        var issued=ActorContext.withinJob(client.authority().tenantId(),"identity-http-complete",()->login.complete(command));
        cookies.write(response,client,issued);if(client.web())ApplicationHttp.clear(response,client,"login-proof");
        return ApiResponse.ok(IdentityApplicationViews.issued(issued));
    }
    @PostMapping("/sessions/refresh") @PermitAnonymous
    public ApiResponse<IdentityApplicationViews.Session> refresh(@Valid @RequestBody Refresh input,HttpServletRequest request,HttpServletResponse response){
        var client=ApplicationHttp.client(request);if(client.web() && input.refreshToken()!=null)throw new BizException(CommonErrorCode.BAD_REQUEST);
        String raw=client.web()?ApplicationHttp.cookie(request,client,"refresh"):input.refreshToken();
        var issued=ActorContext.withinJob(client.authority().tenantId(),"identity-http-refresh",()->sessions.refresh(raw,client.handle(),client.web()?"WEB":"NATIVE",ApplicationHttp.header(request,ApplicationHttp.CSRF_HEADER)));
        cookies.write(response,client,issued);return ApiResponse.ok(IdentityApplicationViews.issued(issued));
    }
}
