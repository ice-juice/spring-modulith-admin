package com.sma.admin.auth.internal.web;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import com.sma.admin.common.*;
import com.sma.admin.common.security.*;
import com.sma.admin.auth.internal.service.*;

@RestController @RequestMapping("/api/auth")
public class AuthController {
    private final SessionService sessions;
    private final CaptchaService captchas;
    private final LoginThrottle throttle;
    public AuthController(SessionService sessions,CaptchaService captchas,LoginThrottle throttle) { this.sessions=sessions; this.captchas=captchas; this.throttle=throttle; }
    @GetMapping("/captcha") @PermitAnonymous
    public ApiResponse<CaptchaService.Challenge> captcha(HttpServletRequest request) { throttle.claimCaptcha(request.getRemoteAddr()); return ApiResponse.ok(captchas.issue()); }
    public record Login(@NotBlank @Size(max=64) String username,@NotBlank @Size(max=128) String password,
            @NotBlank @Pattern(regexp="[a-z][a-z0-9-]{0,31}") String portal,@NotBlank @Size(max=64) String captchaId,
            @NotBlank @Size(max=8) String captcha,boolean remember) {}
    @PostMapping("/login") @PermitAnonymous
    public ApiResponse<SessionService.LoginResult> login(@Valid @RequestBody Login input,HttpServletRequest request) {
        return ApiResponse.ok(sessions.login(input.username(),input.password(),input.portal(),input.captchaId(),input.captcha(),input.remember(),request.getRemoteAddr(),request.getHeader("User-Agent")));
    }
    @GetMapping("/me") @PermitLoggedIn
    public ApiResponse<SessionService.Me> me(@RequestAttribute("sma.session-id") long sessionId) { return ApiResponse.ok(sessions.me(sessionId)); }
    @PostMapping("/logout") @PermitLoggedIn
    public ApiResponse<Void> logout(@RequestAttribute("sma.session-id") long sessionId) { sessions.logout(sessionId); return ApiResponse.ok(); }
}
