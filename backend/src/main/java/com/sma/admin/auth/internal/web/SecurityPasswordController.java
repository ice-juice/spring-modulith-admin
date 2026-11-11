package com.sma.admin.auth.internal.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import com.sma.admin.auth.internal.service.SecurityPasswordService;
import com.sma.admin.common.ApiResponse;
import com.sma.admin.common.security.*;

@RestController @RequestMapping("/api/auth/security-passwords") @PermitLoggedIn
@com.sma.admin.common.request.GovernedApi(group="auth.security-passwords",ownResource=true,mode=com.sma.admin.common.request.GovernedApi.Mode.NATIVE,reason="Existing credential lock order, reauthentication and password policy")
public class SecurityPasswordController implements com.sma.admin.common.request.RequestAuthorizer {
    @Override public void authorizeRequest(String method,java.util.Map<String,String> variables){com.sma.admin.common.security.CurrentUser.require();}
    private final SecurityPasswordService passwords;
    public SecurityPasswordController(SecurityPasswordService passwords){this.passwords=passwords;}
    public record Input(@NotBlank @Size(max=128) String password,@Pattern(regexp="[0-9]{6}") String code,
            @Size(max=8) String oldPassword,@NotBlank @Size(min=6,max=8) String newPassword,@NotBlank @Size(min=6,max=8) String confirmation){}
    @PostMapping @AuditAction("auth:security-password:set")
    public ApiResponse<Void> set(@RequestAttribute("sma.session-id") long sessionId,@Valid @RequestBody Input input,HttpServletRequest request){
        passwords.set(sessionId,input.password(),input.code(),input.newPassword(),input.confirmation(),request.getRemoteAddr());return ApiResponse.ok();
    }
    @PutMapping @AuditAction("auth:security-password:change")
    public ApiResponse<Void> change(@RequestAttribute("sma.session-id") long sessionId,@Valid @RequestBody Input input,HttpServletRequest request){
        passwords.change(sessionId,input.password(),input.code(),input.oldPassword(),input.newPassword(),input.confirmation(),request.getRemoteAddr());return ApiResponse.ok();
    }
    @PostMapping("/reset") @AuditAction("auth:security-password:reset")
    public ApiResponse<Void> reset(@RequestAttribute("sma.session-id") long sessionId,@Valid @RequestBody Input input,HttpServletRequest request){
        passwords.reset(sessionId,input.password(),input.code(),input.newPassword(),input.confirmation(),request.getRemoteAddr());return ApiResponse.ok();
    }
}
