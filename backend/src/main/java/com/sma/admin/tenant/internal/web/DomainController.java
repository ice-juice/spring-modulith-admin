package com.sma.admin.tenant.internal.web;

import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import com.sma.admin.common.*;
import com.sma.admin.common.security.*;
import com.sma.admin.tenant.internal.service.TenantDirectoryService;
import com.sma.admin.tenant.internal.service.DomainLifecycleService;
import com.sma.admin.tenant.internal.service.DomainVerificationService;

@RestController @RequestMapping("/api/tenant/domains")
@com.sma.admin.common.request.GovernedApi(group="tenant.domains")
public class DomainController implements com.sma.admin.common.request.RequestAuthorizer {
    private final TenantDirectoryService service;
    private final com.sma.admin.tenant.internal.service.PlatformOperators operators;
    private final DomainLifecycleService lifecycle;private final DomainVerificationService verification;
    public DomainController(TenantDirectoryService service,com.sma.admin.tenant.internal.service.PlatformOperators operators,DomainLifecycleService lifecycle,DomainVerificationService verification) { this.service=service;this.operators=operators;this.lifecycle=lifecycle;this.verification=verification; }
    public record VersionInput(@Min(1) long expectedVersion) {}
    public record EnableInput(@Min(1) long expectedVersion,@NotNull Boolean enabled) {}
    public record CheckInput(@Min(1) long expectedVersion,@NotBlank @Pattern(regexp="[a-zA-Z0-9_-]{16,64}") String operationId) {}
    public record TenantOption(Long id,String name,String code,String status) {}
    @Override public void authorizeRequest(String method,java.util.Map<String,String> pathVariables){operators.require();}
    @GetMapping @RequiresPermission("tenant:domain:view")
    public ApiResponse<List<TenantDirectoryService.DomainView>> list(@RequestParam long tenantId) { return ApiResponse.ok(service.domains(tenantId)); }
    @PostMapping @RequiresPermission("tenant:domain:create") @AuditAction("tenant:domain:create")
    public ApiResponse<TenantDirectoryService.DomainView> create(@Valid @RequestBody TenantDirectoryService.DomainInput input) { return ApiResponse.ok(service.registerDomain(input)); }
    @GetMapping("/tenant-options") @RequiresPermission("tenant:domain:view")
    public ApiResponse<PageResult<TenantOption>> options(@ModelAttribute PageQuery page,@RequestParam(required=false) String keyword) {
        var tenants=service.page(page,keyword);
        return ApiResponse.ok(new PageResult<>(tenants.items().stream().map(row->new TenantOption(row.id(),row.name(),row.code(),row.status())).toList(),tenants.total(),tenants.page(),tenants.size()));
    }
    @GetMapping("/verification-settings") @RequiresPermission("tenant:domain:view")
    public ApiResponse<DomainLifecycleService.Settings> settings() {return ApiResponse.ok(lifecycle.settings());}
    @GetMapping("/{id}") @RequiresPermission("tenant:domain:view")
    public ApiResponse<TenantDirectoryService.DomainView> get(@PathVariable long id) {return ApiResponse.ok(lifecycle.get(id));}
    @PostMapping("/{id}/challenges") @RequiresPermission("tenant:domain:verify") @AuditAction("tenant:domain:challenge")
    public ApiResponse<TenantDirectoryService.DomainView> challenge(@PathVariable long id,@Valid @RequestBody VersionInput input) {return ApiResponse.ok(lifecycle.challenge(id,input.expectedVersion()));}
    @PostMapping("/{id}/checks") @RequiresPermission("tenant:domain:verify") @AuditAction("tenant:domain:verify")
    @com.sma.admin.common.request.GovernedApi(group="tenant.domains",mode=com.sma.admin.common.request.GovernedApi.Mode.NATIVE,reason="Domain-owned verification receipts; bounded DNS/TLS runs between separate short transactions")
    public ApiResponse<DomainLifecycleService.CheckView> verify(@PathVariable long id,@Valid @RequestBody CheckInput input) {return ApiResponse.ok(verification.verify(id,input.expectedVersion(),input.operationId()));}
    @GetMapping("/{id}/checks/{operationId}") @RequiresPermission("tenant:domain:verify")
    public ApiResponse<DomainLifecycleService.CheckView> check(@PathVariable long id,@PathVariable String operationId) {return ApiResponse.ok(lifecycle.check(id,operationId));}
    @PutMapping("/{id}/enabled") @RequiresPermission("tenant:domain:update") @AuditAction("tenant:domain:update")
    public ApiResponse<TenantDirectoryService.DomainView> enabled(@PathVariable long id,@Valid @RequestBody EnableInput input) {return ApiResponse.ok(lifecycle.enabled(id,input.expectedVersion(),input.enabled()));}
    @PutMapping("/{id}/primary") @RequiresPermission("tenant:domain:update") @AuditAction("tenant:domain:primary")
    public ApiResponse<TenantDirectoryService.DomainView> primary(@PathVariable long id,@Valid @RequestBody VersionInput input) {return ApiResponse.ok(lifecycle.primary(id,input.expectedVersion()));}
}
