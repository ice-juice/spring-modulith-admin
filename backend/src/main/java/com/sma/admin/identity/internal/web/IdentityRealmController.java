package com.sma.admin.identity.internal.web;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.*;
import com.sma.admin.common.*;
import com.sma.admin.common.security.RequiresPermission;
import com.sma.admin.identity.internal.service.IdentityRealmService;
@RestController @RequestMapping("/api/identity/realms")
@com.sma.admin.common.request.GovernedApi(group="identity.realms")
public class IdentityRealmController implements com.sma.admin.common.request.RequestAuthorizer {
    @Override public void authorizeRequest(String method,java.util.Map<String,String> pathVariables){com.sma.admin.common.security.CurrentUser.require();}
    private final IdentityRealmService service;
    public IdentityRealmController(IdentityRealmService service){this.service=service;}
    public record Update(@Positive long expectedVersion,@NotNull @Valid IdentityRealmService.Input configuration) {}
    public record Version(@Positive long expectedVersion) {}
    @GetMapping @RequiresPermission("identity:realm:view")
    public ApiResponse<PageResult<IdentityRealmService.View>> page(@ModelAttribute PageQuery page){return ApiResponse.ok(service.page(page));}
    @GetMapping("/{id}") @RequiresPermission("identity:realm:view")
    public ApiResponse<IdentityRealmService.View> get(@PathVariable long id){return ApiResponse.ok(service.get(id));}
    @PostMapping @RequiresPermission("identity:realm:manage")
    public ApiResponse<IdentityRealmService.View> create(@Valid @RequestBody IdentityRealmService.Input input){return ApiResponse.ok(service.create(input));}
    @PutMapping("/{id}") @RequiresPermission("identity:realm:manage")
    public ApiResponse<IdentityRealmService.View> update(@PathVariable long id,@Valid @RequestBody Update input){return ApiResponse.ok(service.update(id,input.expectedVersion(),input.configuration()));}
    @PostMapping("/{id}/disable") @RequiresPermission("identity:realm:manage")
    public ApiResponse<IdentityRealmService.View> disable(@PathVariable long id,@Valid @RequestBody Version input){return ApiResponse.ok(service.disable(id,input.expectedVersion()));}
}
