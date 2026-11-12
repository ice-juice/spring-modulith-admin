package com.sma.admin.tenant.internal.web;

import com.sma.admin.common.ApiResponse;
import com.sma.admin.common.BizException;
import com.sma.admin.tenant.TenantErrorCode;
import com.sma.admin.common.request.GovernedApi;
import com.sma.admin.common.request.RequestAuthorizer;
import com.sma.admin.common.security.AuditAction;
import com.sma.admin.common.security.RequiresPermission;
import com.sma.admin.tenant.internal.service.PlatformOperators;
import com.sma.admin.tenant.internal.service.StorageProvisionService;
import com.sma.admin.tenant.internal.service.StorageProvisionState;
import com.sma.admin.tenant.internal.service.StorageSwitchCoordinator;
import com.sma.admin.tenant.internal.service.StorageSwitchState;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

/** Native platform task receipts; physical work and platform claims commit separately. */
@RestController
@RequestMapping("/api/tenant")
@GovernedApi(group="tenant.storage-operations", mode=GovernedApi.Mode.NATIVE,
        reason="Provision and switch own durable operation keys, physical verification and separate platform transactions")
public class StorageOperationsController implements RequestAuthorizer {
    public record ProvisionInput(@Min(1) long bindingId, @Min(1) long expectedVersion,
                                 @Pattern(regexp="[a-zA-Z0-9_-]{16,64}") String operationKey) {}
    public record SwitchInput(@Min(1) long bindingId, @Min(1) long expectedVersion,
                              @Pattern(regexp="[a-z][a-z0-9_-]{0,63}") String targetResourceId,
                              @Min(1) long targetRevision,
                              @Pattern(regexp="[a-zA-Z0-9_-]{16,64}") String operationKey) {}
    public record ProvisionReceipt(String operationKey, String state) {}
    private final PlatformOperators operators;
    private final StorageProvisionService provisioning;
    private final StorageProvisionState provisionState;
    private final StorageSwitchCoordinator switching;
    private final StorageSwitchState switchState;

    public StorageOperationsController(PlatformOperators operators, StorageProvisionService provisioning,
                                       StorageProvisionState provisionState, StorageSwitchCoordinator switching,
                                       StorageSwitchState switchState) {
        this.operators=operators; this.provisioning=provisioning; this.provisionState=provisionState;
        this.switching=switching; this.switchState=switchState;
    }
    @Override public void authorizeRequest(String method, Map<String,String> pathVariables) { operators.require(); }
    @Override public boolean readOnlyRequest(String method, Object[] arguments) {
        return "provisionReceipt".equals(method) || "switchReceipt".equals(method);
    }

    @PostMapping("/storage-provisions") @RequiresPermission("tenant:storage:provision")
    @AuditAction("tenant:storage:provision")
    public ApiResponse<ProvisionReceipt> provision(@Valid @RequestBody ProvisionInput input) {
        try {
            return ApiResponse.ok(new ProvisionReceipt(input.operationKey(), provisioning.provision(
                    input.bindingId(), input.expectedVersion(), input.operationKey())));
        } catch (IllegalStateException unavailable) { throw new BizException(TenantErrorCode.STATE); }
    }
    @PostMapping("/storage-provisions/retries") @RequiresPermission("tenant:storage:provision")
    @AuditAction("tenant:storage:provision-retry")
    public ApiResponse<ProvisionReceipt> retry(@Valid @RequestBody ProvisionInput input) {
        try {
            return ApiResponse.ok(new ProvisionReceipt(input.operationKey(), provisioning.retryAfterReview(
                    input.bindingId(), input.expectedVersion(), input.operationKey())));
        } catch (IllegalStateException unavailable) { throw new BizException(TenantErrorCode.STATE); }
    }
    @GetMapping("/storage-provisions/{operationKey}") @RequiresPermission("tenant:storage:view")
    public ApiResponse<ProvisionReceipt> provisionReceipt(@PathVariable String operationKey) {
        return ApiResponse.ok(new ProvisionReceipt(operationKey, provisionState.state(operationKey)));
    }

    @PostMapping("/storage-switches") @RequiresPermission("tenant:storage:switch")
    @AuditAction("tenant:storage:switch-begin")
    public ApiResponse<StorageSwitchState.Task> begin(@Valid @RequestBody SwitchInput input) {
        try { return ApiResponse.ok(switching.begin(input.bindingId(), input.expectedVersion(), input.targetResourceId(),
                input.targetRevision(), input.operationKey())); }
        catch (IllegalStateException unavailable) { throw new BizException(TenantErrorCode.STATE); }
    }
    @GetMapping("/storage-switches/{id}") @RequiresPermission("tenant:storage:view")
    public ApiResponse<StorageSwitchState.Task> switchReceipt(@PathVariable long id) {
        return ApiResponse.ok(switchState.inspect(id));
    }
    @PostMapping("/storage-switches/{id}/copy-checks") @RequiresPermission("tenant:storage:switch")
    @AuditAction("tenant:storage:switch-copy")
    public ApiResponse<StorageSwitchState.Task> copy(@PathVariable long id) { return switchAction(() -> switching.verifyCopy(id)); }
    @PostMapping("/storage-switches/{id}/stops") @RequiresPermission("tenant:storage:switch")
    @AuditAction("tenant:storage:switch-stop")
    public ApiResponse<StorageSwitchState.Task> stop(@PathVariable long id) { return switchAction(() -> switching.stop(id)); }
    @PostMapping("/storage-switches/{id}/final-checks") @RequiresPermission("tenant:storage:switch")
    @AuditAction("tenant:storage:switch-final")
    public ApiResponse<StorageSwitchState.Task> finalCheck(@PathVariable long id) { return switchAction(() -> switching.verifyFinal(id)); }
    @PostMapping("/storage-switches/{id}/commits") @RequiresPermission("tenant:storage:switch")
    @AuditAction("tenant:storage:switch-commit")
    public ApiResponse<StorageSwitchState.Task> commit(@PathVariable long id) { return switchAction(() -> switching.commit(id)); }
    @PostMapping("/storage-switches/{id}/aborts") @RequiresPermission("tenant:storage:switch")
    @AuditAction("tenant:storage:switch-abort")
    public ApiResponse<StorageSwitchState.Task> abort(@PathVariable long id) { return switchAction(() -> switching.abort(id)); }

    private ApiResponse<StorageSwitchState.Task> switchAction(java.util.function.Supplier<StorageSwitchState.Task> action) {
        try { return ApiResponse.ok(action.get()); }
        catch (IllegalStateException unavailable) { throw new BizException(TenantErrorCode.STATE); }
    }
}
