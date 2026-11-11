package com.sma.admin.tenant.internal.web;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import com.sma.admin.common.*;
import com.sma.admin.common.security.*;
import com.sma.admin.tenant.internal.service.TenantDirectoryService;

@RestController @RequestMapping("/api/tenant/tenants")
@com.sma.admin.common.request.GovernedApi(group="tenant.tenants")
public class TenantController implements com.sma.admin.common.request.RequestAuthorizer {
    private final TenantDirectoryService service;
    private final com.sma.admin.tenant.internal.service.PlatformOperators operators;
    public TenantController(TenantDirectoryService service,com.sma.admin.tenant.internal.service.PlatformOperators operators) { this.service=service;this.operators=operators; }
    @Override public void authorizeRequest(String method,java.util.Map<String,String> pathVariables){operators.require();}
    @GetMapping @RequiresPermission("tenant:tenant:view")
    public ApiResponse<PageResult<TenantDirectoryService.TenantView>> page(@ModelAttribute PageQuery page,@RequestParam(required=false) String keyword) {
        return ApiResponse.ok(service.page(page,keyword));
    }
    @PostMapping @RequiresPermission("tenant:tenant:create") @AuditAction("tenant:tenant:create")
    public ApiResponse<TenantDirectoryService.TenantView> create(@Valid @RequestBody TenantDirectoryService.TenantInput input) {
        return ApiResponse.ok(service.create(input));
    }
    @PutMapping("/{id}/status") @RequiresPermission("tenant:tenant:update") @AuditAction("tenant:tenant:update")
    public ApiResponse<TenantDirectoryService.TenantView> status(@PathVariable long id,@Valid @RequestBody TenantDirectoryService.StatusInput input) {
        return ApiResponse.ok(service.changeStatus(id,input));
    }
}
