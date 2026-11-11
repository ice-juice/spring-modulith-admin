package com.sma.admin.auth.internal.web;

import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import com.sma.admin.auth.internal.service.*;
import com.sma.admin.common.ApiResponse;
import com.sma.admin.common.security.*;

@RestController @RequestMapping("/api/auth") @PermitLoggedIn
@com.sma.admin.common.request.GovernedApi(group="auth.account-security",ownResource=true,mode=com.sma.admin.common.request.GovernedApi.Mode.NATIVE,reason="Bound session challenges, OTP budgets and atomic credential changes")
public class AccountSecurityController implements com.sma.admin.common.request.RequestAuthorizer {
    @Override public void authorizeRequest(String method,java.util.Map<String,String> variables){com.sma.admin.common.security.CurrentUser.require();}
    private final SecuritySettingsService settings;private final TotpEnrollmentService enrollment;private final TotpEnrollmentConfirmation confirmation;
    public AccountSecurityController(SecuritySettingsService settings,TotpEnrollmentService enrollment,TotpEnrollmentConfirmation confirmation){this.settings=settings;this.enrollment=enrollment;this.confirmation=confirmation;}
    public record Reauthentication(@NotBlank @Size(max=128) String password,@Pattern(regexp="[0-9]{6}") String code){}
    public record Confirmation(@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{43}") String challengeToken,@NotBlank @Pattern(regexp="[0-9]{6}") String code){}
    public record Cancellation(@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{43}") String challengeToken){}
    @GetMapping("/security-settings")
    public ApiResponse<SecuritySettingsService.Settings> get(@RequestAttribute("sma.session-id") long id,HttpServletResponse response){response.setHeader("Cache-Control","no-store");return ApiResponse.ok(settings.get(id));}
    @PostMapping("/totp-enrollments") @AuditAction("auth:totp:enroll")
    public ApiResponse<TotpEnrollmentService.Enrollment> enroll(@RequestAttribute("sma.session-id") long id,@Valid @RequestBody Reauthentication input,HttpServletRequest request,HttpServletResponse response){
        response.setHeader("Cache-Control","no-store");return ApiResponse.ok(enrollment.begin(id,input.password(),input.code(),request.getRemoteAddr()));
    }
    @PostMapping("/totp-enrollments/confirm") @AuditAction("auth:totp:confirm")
    public ApiResponse<Void> confirm(@RequestAttribute("sma.session-id") long id,@Valid @RequestBody Confirmation input,HttpServletRequest request){confirmation.confirm(id,input.challengeToken(),input.code(),request.getRemoteAddr());return ApiResponse.ok();}
    @PostMapping("/totp-enrollments/cancel") @AuditAction("auth:totp:cancel")
    public ApiResponse<Void> cancel(@RequestAttribute("sma.session-id") long id,@Valid @RequestBody Cancellation input){enrollment.cancel(id,input.challengeToken());return ApiResponse.ok();}
    @PostMapping("/totp-credentials/disable") @AuditAction("auth:totp:disable")
    public ApiResponse<Void> disable(@RequestAttribute("sma.session-id") long id,@Valid @RequestBody Reauthentication input,HttpServletRequest request){enrollment.disable(id,input.password(),input.code(),request.getRemoteAddr());return ApiResponse.ok();}
}
