package com.sma.admin.auth.internal.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import com.sma.admin.auth.internal.service.*;
import com.sma.admin.common.ApiResponse;
import com.sma.admin.common.security.PermitAnonymous;

@RestController @RequestMapping("/api/auth/login-challenges")
@com.sma.admin.common.request.GovernedApi(group="auth.login-challenges",ownResource=true,mode=com.sma.admin.common.request.GovernedApi.Mode.NATIVE,reason="Anonymous one-use MFA challenge and independently committed guessing budget")
public class LoginChallengeController implements com.sma.admin.common.request.RequestAuthorizer {
    @Override public void authorizeRequest(String method,java.util.Map<String,String> variables){com.sma.admin.common.security.CurrentUser.require();}
    private final MfaLoginService login;
    public LoginChallengeController(MfaLoginService login){this.login=login;}
    public record Verification(@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{43}") String challengeToken,@NotBlank @Pattern(regexp="[0-9]{6}") String code) {}
    @PostMapping("/verify") @PermitAnonymous
    public ApiResponse<SessionService.LoginResult> verify(@Valid @RequestBody Verification input,HttpServletRequest request,HttpServletResponse response){
        response.setHeader("Cache-Control","no-store");
        return ApiResponse.ok(login.verify(input.challengeToken(),input.code(),request.getRemoteAddr(),request.getHeader("User-Agent")));
    }
}
