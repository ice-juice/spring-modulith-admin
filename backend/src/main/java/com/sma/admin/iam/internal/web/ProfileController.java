package com.sma.admin.iam.internal.web;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import com.sma.admin.common.*;
import com.sma.admin.common.security.*;
import com.sma.admin.iam.internal.service.ProfileService;

@RestController @RequestMapping("/api/iam/profile") @PermitLoggedIn
@com.sma.admin.common.request.GovernedApi(group="iam.profile",ownResource=true)
public class ProfileController implements com.sma.admin.common.request.RequestAuthorizer {
    @Override public void authorizeRequest(String method,java.util.Map<String,String> variables) {
        // The receipt is actor-owned metadata; domain reads and writes retain their
        // current tenant, portal, data-scope and object authorization in the service.
        com.sma.admin.common.security.CurrentUser.require();
    }
 private final ProfileService service;
 public ProfileController(ProfileService service){this.service=service;}
 @GetMapping public ApiResponse<ProfileService.View> get(){return ApiResponse.ok(service.get());}
 @PutMapping @AuditAction("iam:profile:update")
 public ApiResponse<ProfileService.View> update(@Valid @RequestBody ProfileService.Input input){return ApiResponse.ok(service.update(input));}
}
