package com.sma.admin.identity.internal.web;

import java.time.*;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import com.sma.admin.common.*;
import com.sma.admin.common.security.*;
import com.sma.admin.identity.internal.*;
import com.sma.admin.identity.internal.service.*;

/** Anonymous mailbox flows have a closed purpose set. Binding is exclusively an authenticated account operation. */
@RestController @RequestMapping("/app/identity")
public class IdentityEmailLoginController {
    public enum Purpose { REGISTRATION, PASSWORD_RESET }
    public record Challenge(@NotNull Purpose purpose,@NotBlank @Size(max=320)String email,@NotBlank @Pattern(regexp="zh-CN|en-US")String language){
        @Override public String toString(){return "EmailChallenge[REDACTED]";}
    }
    public record Verification(@NotNull Purpose purpose,@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{43}")String handle,
            @Size(max=43)String proof,@NotBlank @Pattern(regexp="[0-9]{6}")String code){
        @Override public String toString(){return "EmailVerification[REDACTED]";}
    }
    public record Credential(@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{43}")String grant,@Size(max=43)String proof,
            @NotBlank @Size(max=72)String password){@Override public String toString(){return "EmailCredential[REDACTED]";}}
    public record Password(@NotBlank @Size(max=320)String email,@NotBlank @Size(max=72)String password){
        @Override public String toString(){return "PasswordLogin[REDACTED]";}
    }
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Started(String handle,String proof,Instant expiresAt){@Override public String toString(){return "EmailStarted[REDACTED]";}}
    public record Verified(String grant,Instant expiresAt){@Override public String toString(){return "EmailVerified[REDACTED]";}}
    private final IdentityEmailAccountService accounts;
    private final IdentityEmailChallengeStore challenges;
    private final IdentityPasswordLoginCoordinator passwords;
    private final IdentityClock clock;
    private final ApplicationSessionCookies cookies;
    public IdentityEmailLoginController(IdentityEmailAccountService accounts,IdentityEmailChallengeStore challenges,IdentityPasswordLoginCoordinator passwords,
            IdentityClock clock,ApplicationSessionCookies cookies){
        this.accounts=accounts;this.challenges=challenges;this.passwords=passwords;this.clock=clock;this.cookies=cookies;
    }
    @PostMapping("/email/challenges") @PermitAnonymous
    public ApiResponse<Started> begin(@Valid @RequestBody Challenge input,HttpServletRequest request,HttpServletResponse response){
        var client=ApplicationHttp.client(request);
        var result=ActorContext.withinJob(client.authority().tenantId(),"identity-http-email-begin",()->accounts.start(new IdentityEmailAccountService.Challenge(
                client.handle(),input.language(),purpose(input.purpose()),input.email(),origin(request),request.getRemoteAddr())));
        if(client.web())ApplicationHttp.cookie(response,client,kind(input.purpose()),result.proof(),true,Duration.between(clock.now(),result.expiresAt()));
        return ApiResponse.ok(new Started(result.handle(),client.web()?null:result.proof(),result.expiresAt()));
    }
    @PostMapping("/email/verifications") @PermitAnonymous
    public ApiResponse<Verified> verify(@Valid @RequestBody Verification input,HttpServletRequest request){
        var client=ApplicationHttp.client(request);String proof=proof(request,input.purpose(),input.proof());
        var result=ActorContext.withinJob(client.authority().tenantId(),"identity-http-email-verify",()->challenges.verify(new IdentityEmailChallengeStore.Verification(
                input.handle(),proof,input.code(),client.handle(),purpose(input.purpose()),origin(request),request.getRemoteAddr())));
        if(result.error()!=null)throw new BizException(result.error());
        return ApiResponse.ok(new Verified(result.grant(),result.expiresAt()));
    }
    @PostMapping("/registrations") @PermitAnonymous
    public ApiResponse<IdentityApplicationViews.Session> register(@Valid @RequestBody Credential input,HttpServletRequest request,HttpServletResponse response){
        var client=ApplicationHttp.client(request);var use=use(request,input,Purpose.REGISTRATION);
        var issued=ActorContext.withinJob(client.authority().tenantId(),"identity-http-register",()->accounts.register(use,input.password()));
        cookies.write(response,client,issued);if(client.web())ApplicationHttp.clear(response,client,kind(Purpose.REGISTRATION));
        return ApiResponse.ok(IdentityApplicationViews.issued(issued));
    }
    @PostMapping("/password/resets") @PermitAnonymous
    public ApiResponse<Void> reset(@Valid @RequestBody Credential input,HttpServletRequest request,HttpServletResponse response){
        var client=ApplicationHttp.client(request);var use=use(request,input,Purpose.PASSWORD_RESET);
        ActorContext.withinJob(client.authority().tenantId(),"identity-http-password-reset",()->{accounts.resetPassword(use,input.password());return null;});
        cookies.clear(response,client);return ApiResponse.ok();
    }
    @PostMapping("/password/sessions") @PermitAnonymous
    public ApiResponse<IdentityApplicationViews.Session> login(@Valid @RequestBody Password input,HttpServletRequest request,HttpServletResponse response){
        var client=ApplicationHttp.client(request);
        var issued=ActorContext.withinJob(client.authority().tenantId(),"identity-http-password-login",()->passwords.login(new IdentityPasswordLoginCoordinator.Login(
                client.handle(),input.email(),input.password(),origin(request),request.getRemoteAddr())));
        cookies.write(response,client,issued);return ApiResponse.ok(IdentityApplicationViews.issued(issued));
    }
    private IdentityEmailChallengeStore.Use use(HttpServletRequest request,Credential input,Purpose purpose){
        return new IdentityEmailChallengeStore.Use(input.grant(),proof(request,purpose,input.proof()),ApplicationHttp.client(request).handle(),purpose(purpose),origin(request));
    }
    private String proof(HttpServletRequest request,Purpose purpose,String supplied){
        var client=ApplicationHttp.client(request);if(client.web() && supplied!=null)throw new BizException(CommonErrorCode.BAD_REQUEST);
        return client.web()?ApplicationHttp.cookie(request,client,kind(purpose)):supplied;
    }
    private String origin(HttpServletRequest request){return (String)request.getAttribute(ApplicationHttp.ORIGIN);}
    private String kind(Purpose purpose){return purpose==Purpose.REGISTRATION?"registration-proof":"password-reset-proof";}
    private IdentityEmailChallengeStore.Purpose purpose(Purpose purpose){return IdentityEmailChallengeStore.Purpose.valueOf(purpose.name());}
}
