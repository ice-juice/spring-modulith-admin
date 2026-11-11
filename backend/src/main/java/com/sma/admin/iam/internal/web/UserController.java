package com.sma.admin.iam.internal.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;
import com.sma.admin.common.*;
import com.sma.admin.common.security.*;
import com.sma.admin.iam.internal.service.UserService;

@RestController @RequestMapping("/api/iam/users")
@com.sma.admin.common.request.GovernedApi(group="iam.users")
public class UserController implements com.sma.admin.common.request.RequestAuthorizer {
    @Override public void authorizeRequest(String method,java.util.Map<String,String> variables) {
        // The receipt is actor-owned metadata; domain reads and writes retain their
        // current tenant, portal, data-scope and object authorization in the service.
        com.sma.admin.common.security.CurrentUser.require();
    }
    private final UserService service;
    public UserController(UserService service) { this.service=service; }
    @GetMapping @RequiresPermission("iam:user:view")
    public ApiResponse<PageResult<UserService.View>> page(@ModelAttribute PageQuery page,@RequestParam(required=false) String keyword,
            @RequestParam(required=false) Long departmentId,@RequestParam(required=false) Boolean enabled,@RequestParam(required=false)Long roleId,@RequestParam(required=false)Long postId) { return ApiResponse.ok(service.page(page,keyword,departmentId,enabled,roleId,postId)); }
    @GetMapping("/{id}") @RequiresPermission("iam:user:view")
    public ApiResponse<UserService.View> get(@PathVariable long id) { return ApiResponse.ok(service.get(id)); }
    @PostMapping @RequiresPermission("iam:user:create")
    public ApiResponse<UserService.View> create(@Valid @RequestBody UserService.Create input) { return ApiResponse.ok(service.create(input)); }
    @PutMapping("/{id}") @RequiresPermission("iam:user:update")
    public ApiResponse<UserService.View> update(@PathVariable long id,@Valid @RequestBody UserService.Input input) { return ApiResponse.ok(service.update(id,input)); }
    @DeleteMapping("/{id}") @RequiresPermission("iam:user:delete")
    public ApiResponse<Void> delete(@PathVariable long id) { service.delete(id); return ApiResponse.ok(); }
    public record Password(@NotBlank String password) {}
    @PutMapping("/{id}/password") @RequiresPermission("iam:user:reset-password")
    public ApiResponse<Void> resetPassword(@PathVariable long id,@Valid @RequestBody Password input) { service.resetPassword(id,input.password()); return ApiResponse.ok(); }
    public record OwnPassword(@NotBlank String oldPassword,@NotBlank String newPassword) {}
    @PutMapping("/me/password") @PermitLoggedIn
    @AuditAction("iam:user:change-password")
    public ApiResponse<Void> changeOwnPassword(@Valid @RequestBody OwnPassword input) { service.changeOwnPassword(input.oldPassword(),input.newPassword()); return ApiResponse.ok(); }
}
