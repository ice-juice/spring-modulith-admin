package com.sma.admin.identity.internal.web;

import java.time.Duration;
import java.util.function.Supplier;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import com.sma.admin.common.*;
import com.sma.admin.common.security.*;
import com.sma.admin.identity.internal.*;
import com.sma.admin.identity.internal.service.*;
import com.sma.admin.identity.internal.service.IdentityReauthenticationStore.Action;

/** All principals, tenant/client selectors and request origins come from the application security chain. */
@RestController @RequestMapping("/app/identity/account")
public class IdentityAccountController {
    public record PasswordProof(@NotNull Action action,@NotBlank @Size(max=320)String target,@NotBlank @Size(max=72)String password){
        @Override public String toString(){return "PasswordReauthentication[REDACTED]";}
    }
    public record GoogleProof(@NotNull Action action,@NotBlank @Size(max=320)String target,@NotNull @Valid IdentityLoginController.Start google){}
    public record GoogleLink(@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{43}")String grant,@NotNull @Valid IdentityLoginController.Start google){
        @Override public String toString(){return "GoogleLink[REDACTED]";}
    }
    public record EmailBinding(@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{43}")String grant,@NotBlank @Size(max=320)String email,
            @NotBlank @Pattern(regexp="zh-CN|en-US")String language){@Override public String toString(){return "EmailBinding[REDACTED]";}}
    public record EmailVerification(@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{43}")String handle,@Size(max=43)String proof,
            @NotBlank @Pattern(regexp="[0-9]{6}")String code){@Override public String toString(){return "BindingVerification[REDACTED]";}}
    public record EmailCompletion(@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{43}")String grant,@Size(max=43)String proof,@Size(max=72)String newPassword){
        @Override public String toString(){return "BindingCompletion[REDACTED]";}
    }
    public record Grant(@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{43}")String grant){@Override public String toString(){return "AccountGrant[REDACTED]";}}
    private final IdentityAccountQuery query;private final IdentityPasswordReauthenticationCoordinator passwords;
    private final IdentityGoogleOperations google;private final IdentityCredentialOperations credentials;private final IdentityEmailChallengeStore challenges;
    private final IdentityClock clock;private final ApplicationSessionCookies cookies;
    public IdentityAccountController(IdentityAccountQuery query,IdentityPasswordReauthenticationCoordinator passwords,IdentityGoogleOperations google,
            IdentityCredentialOperations credentials,IdentityEmailChallengeStore challenges,IdentityClock clock,ApplicationSessionCookies cookies){
        this.query=query;this.passwords=passwords;this.google=google;this.credentials=credentials;this.challenges=challenges;this.clock=clock;this.cookies=cookies;
    }
    @GetMapping("/methods") @PermitApplicationSelf
    public ApiResponse<IdentityAccountQuery.Methods> methods(HttpServletRequest request){var actor=actor(request);return ApiResponse.ok(work(request,()->query.current(actor)));}
    @PostMapping("/reauthentication/password") @PermitApplicationSelf
    public ApiResponse<IdentityReauthenticationStore.Issued> passwordProof(@Valid @RequestBody PasswordProof input,HttpServletRequest request){
        var actor=actor(request);return ApiResponse.ok(work(request,()->passwords.verify(new IdentityPasswordReauthenticationCoordinator.Request(
                actor,input.action(),input.target(),input.password(),origin(request),request.getRemoteAddr()))));
    }
    @PostMapping("/reauthentication/google/transactions") @PermitApplicationSelf
    public ApiResponse<IdentityApplicationViews.LoginStarted> googleProofStart(@Valid @RequestBody GoogleProof input,HttpServletRequest request,HttpServletResponse response){
        var actor=actor(request);var result=work(request,()->google.beginReauthentication(start(input.google(),request),actor,input.action(),input.target()));
        return ApiResponse.ok(started(result,request,response,"reauth-google-proof"));
    }
    @PostMapping("/reauthentication/google/completions") @PermitApplicationSelf
    public ApiResponse<IdentityReauthenticationStore.Issued> googleProofComplete(@Valid @RequestBody IdentityLoginController.Complete input,HttpServletRequest request,HttpServletResponse response){
        var actor=actor(request);var completion=completion(input,request,"reauth-google-proof");
        var result=work(request,()->google.completeReauthentication(completion,actor));clearProof(request,response,"reauth-google-proof");return ApiResponse.ok(result);
    }
    @PostMapping("/google-links/transactions") @PermitApplicationSelf
    public ApiResponse<IdentityApplicationViews.LoginStarted> googleLinkStart(@Valid @RequestBody GoogleLink input,HttpServletRequest request,HttpServletResponse response){
        var actor=actor(request);var result=work(request,()->google.beginLink(start(input.google(),request),actor,new IdentityReauthenticationStore.Use(input.grant(),Action.GOOGLE_BINDING,"google")));
        return ApiResponse.ok(started(result,request,response,"link-google-proof"));
    }
    @PostMapping("/google-links/completions") @PermitApplicationSelf
    public ApiResponse<IdentityExternalLinks.Linked> googleLinkComplete(@Valid @RequestBody IdentityLoginController.Complete input,HttpServletRequest request,HttpServletResponse response){
        var actor=actor(request);var completion=completion(input,request,"link-google-proof");var result=work(request,()->google.completeLink(completion,actor));
        clearProof(request,response,"link-google-proof");if(result.signInRequired())cookies.clear(response,ApplicationHttp.client(request));return ApiResponse.ok(result);
    }
    @PostMapping("/email-bindings/challenges") @PermitApplicationSelf
    public ApiResponse<IdentityEmailLoginController.Started> emailStart(@Valid @RequestBody EmailBinding input,HttpServletRequest request,HttpServletResponse response){
        var actor=actor(request);var result=work(request,()->credentials.beginBinding(actor,new IdentityCredentialOperations.Binding(
                new IdentityReauthenticationStore.Use(input.grant(),Action.EMAIL_BINDING,input.email()),input.language(),input.email(),origin(request),request.getRemoteAddr())));
        var client=ApplicationHttp.client(request);
        if(client.web())ApplicationHttp.cookie(response,client,"binding-proof",result.proof(),true,Duration.between(clock.now(),result.expiresAt()));
        return ApiResponse.ok(new IdentityEmailLoginController.Started(result.handle(),client.web()?null:result.proof(),result.expiresAt()));
    }
    @PostMapping("/email-bindings/verifications") @PermitApplicationSelf
    public ApiResponse<IdentityEmailLoginController.Verified> emailVerify(@Valid @RequestBody EmailVerification input,HttpServletRequest request){
        var actor=actor(request);var client=ApplicationHttp.client(request);String proof=proof(request,"binding-proof",input.proof());
        var result=work(request,()->challenges.verifyBinding(new IdentityEmailChallengeStore.Verification(input.handle(),proof,input.code(),client.handle(),
                IdentityEmailChallengeStore.Purpose.EMAIL_BINDING,origin(request),request.getRemoteAddr()),actor));
        if(result.error()!=null)throw new BizException(result.error());return ApiResponse.ok(new IdentityEmailLoginController.Verified(result.grant(),result.expiresAt()));
    }
    @PostMapping("/email-bindings/completions") @PermitApplicationSelf
    public ApiResponse<IdentityCredentialOperations.Changed> emailComplete(@Valid @RequestBody EmailCompletion input,HttpServletRequest request,HttpServletResponse response){
        var actor=actor(request);var client=ApplicationHttp.client(request);String proof=proof(request,"binding-proof",input.proof());
        var result=work(request,()->credentials.completeBinding(actor,new IdentityEmailChallengeStore.Use(input.grant(),proof,client.handle(),IdentityEmailChallengeStore.Purpose.EMAIL_BINDING,origin(request)),input.newPassword()));
        clearProof(request,response,"binding-proof");if(result.signInRequired())cookies.clear(response,client);return ApiResponse.ok(result);
    }
    @DeleteMapping("/password") @PermitApplicationSelf
    public ApiResponse<IdentityCredentialOperations.Changed> removePassword(@Valid @RequestBody Grant input,HttpServletRequest request,HttpServletResponse response){
        var actor=actor(request);var result=work(request,()->credentials.removePassword(actor,new IdentityReauthenticationStore.Use(input.grant(),Action.PASSWORD_REMOVAL,"password")));
        if(result.signInRequired())cookies.clear(response,ApplicationHttp.client(request));return ApiResponse.ok(result);
    }
    @DeleteMapping("/google-links/{id}") @PermitApplicationSelf
    public ApiResponse<IdentityCredentialOperations.Changed> removeGoogle(@PathVariable long id,@Valid @RequestBody Grant input,HttpServletRequest request,HttpServletResponse response){
        var actor=actor(request);var result=work(request,()->credentials.removeGoogle(actor,new IdentityReauthenticationStore.Use(input.grant(),Action.GOOGLE_REMOVAL,Long.toString(id))));
        if(result.signInRequired())cookies.clear(response,ApplicationHttp.client(request));return ApiResponse.ok(result);
    }
    private AppUserPrincipal actor(HttpServletRequest request){
        if(!(request.getAttribute(ApplicationHttp.ACCESS) instanceof IdentitySessionStore.Access access))throw new BizException(CommonErrorCode.UNAUTHORIZED);return access.principal();
    }
    private <T>T work(HttpServletRequest request,Supplier<T> work){return ActorContext.withinJob(ApplicationHttp.client(request).authority().tenantId(),"identity-http-account",work);}
    private String origin(HttpServletRequest request){return (String)request.getAttribute(ApplicationHttp.ORIGIN);}
    private String proof(HttpServletRequest request,String kind,String supplied){
        var client=ApplicationHttp.client(request);if(client.web() && supplied!=null)throw new BizException(CommonErrorCode.BAD_REQUEST);return client.web()?ApplicationHttp.cookie(request,client,kind):supplied;
    }
    private void clearProof(HttpServletRequest request,HttpServletResponse response,String kind){var client=ApplicationHttp.client(request);if(client.web())ApplicationHttp.clear(response,client,kind);}
    private IdentityLoginStore.Start start(IdentityLoginController.Start input,HttpServletRequest request){return new IdentityLoginStore.Start(ApplicationHttp.client(request).handle(),input.callbackUri(),origin(request),input.flow(),input.pkceChallenge(),request.getRemoteAddr());}
    private IdentityLoginStore.Completion completion(IdentityLoginController.Complete input,HttpServletRequest request,String kind){return new IdentityLoginStore.Completion(
            input.state(),proof(request,kind,input.proof()),input.verifier(),ApplicationHttp.client(request).handle(),input.callbackUri(),origin(request),input.flow(),input.credential());}
    private IdentityApplicationViews.LoginStarted started(IdentityLoginStore.Started result,HttpServletRequest request,HttpServletResponse response,String kind){
        var client=ApplicationHttp.client(request);if(client.web())ApplicationHttp.cookie(response,client,kind,result.proof(),true,Duration.between(clock.now(),result.expiresAt()));
        return new IdentityApplicationViews.LoginStarted(result.state(),client.web()?null:result.proof(),result.nonce(),result.authorizationUri()==null?null:result.authorizationUri().toString(),result.googleClientId(),result.platform(),result.expiresAt());
    }
}
