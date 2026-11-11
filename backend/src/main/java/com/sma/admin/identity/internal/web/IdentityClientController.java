package com.sma.admin.identity.internal.web;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.*;
import com.sma.admin.common.*;
import com.sma.admin.common.security.RequiresPermission;
import com.sma.admin.identity.internal.service.IdentityClientService;
@RestController @RequestMapping("/api/identity/clients")
@com.sma.admin.common.request.GovernedApi(group="identity.clients")
public class IdentityClientController implements com.sma.admin.common.request.RequestAuthorizer {
    @Override public void authorizeRequest(String method,java.util.Map<String,String> pathVariables){com.sma.admin.common.security.CurrentUser.require();}
    private final IdentityClientService service;
    public IdentityClientController(IdentityClientService service){this.service=service;}
    public record Update(@Positive long expectedVersion,@NotNull @Valid IdentityClientService.Input configuration) {}
    public record Version(@Positive long expectedVersion) {}
    @GetMapping @RequiresPermission("identity:client:view")
    public ApiResponse<PageResult<IdentityClientService.View>> page(@ModelAttribute PageQuery page,@RequestParam(required=false)Long realmId){return ApiResponse.ok(service.page(page,realmId));}
    @GetMapping("/{id}") @RequiresPermission("identity:client:view")
    public ApiResponse<IdentityClientService.View> get(@PathVariable long id){return ApiResponse.ok(service.get(id));}
    @PostMapping @RequiresPermission("identity:client:manage")
    public ApiResponse<IdentityClientService.View> create(@Valid @RequestBody IdentityClientService.Input input){return ApiResponse.ok(service.create(input));}
    @PutMapping("/{id}") @RequiresPermission("identity:client:manage")
    public ApiResponse<IdentityClientService.View> update(@PathVariable long id,@Valid @RequestBody Update input){return ApiResponse.ok(service.update(id,input.expectedVersion(),input.configuration()));}
    @PostMapping("/{id}/disable") @RequiresPermission("identity:client:manage")
    public ApiResponse<IdentityClientService.View> disable(@PathVariable long id,@Valid @RequestBody Version input){return ApiResponse.ok(service.disable(id,input.expectedVersion()));}
}
