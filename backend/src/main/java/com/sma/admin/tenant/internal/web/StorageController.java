package com.sma.admin.tenant.internal.web;

import com.sma.admin.common.ApiResponse;
import com.sma.admin.common.security.AuditAction;
import com.sma.admin.common.PageQuery;
import com.sma.admin.common.PageResult;
import com.sma.admin.common.request.GovernedApi;
import com.sma.admin.common.request.RequestAuthorizer;
import com.sma.admin.common.security.RequiresPermission;
import com.sma.admin.tenant.internal.service.PlatformOperators;
import com.sma.admin.tenant.internal.service.StorageDirectory;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Platform operator directory; provisioning and activation are separate S7 operations. */
@RestController
@RequestMapping("/api/tenant")
@GovernedApi(group = "tenant.storage")
public class StorageController implements RequestAuthorizer {
    private final StorageDirectory directory;
    private final PlatformOperators operators;

    public StorageController(StorageDirectory directory, PlatformOperators operators) {
        this.directory = directory;
        this.operators = operators;
    }

    @Override public void authorizeRequest(String method, Map<String, String> pathVariables) {
        operators.require();
    }

    @GetMapping("/storage-resources")
    @RequiresPermission("tenant:storage:view")
    public ApiResponse<PageResult<StorageDirectory.ResourceView>> resources(
            @ModelAttribute PageQuery page, @RequestParam(required = false) String resourceId) {
        return ApiResponse.ok(directory.resources(page, resourceId));
    }

    @PostMapping("/storage-resources")
    @RequiresPermission("tenant:storage:register")
    @AuditAction("tenant:storage:register")
    public ApiResponse<StorageDirectory.ResourceView> register(@RequestBody StorageDirectory.ResourceInput input) {
        return ApiResponse.ok(StorageDirectory.view(directory.register(input)));
    }

    @GetMapping("/storage-bindings")
    @RequiresPermission("tenant:storage:view")
    public ApiResponse<PageResult<StorageDirectory.BindingView>> bindings(
            @ModelAttribute PageQuery page, @RequestParam long tenantId) {
        return ApiResponse.ok(directory.bindings(page, tenantId));
    }

    @PostMapping("/storage-bindings")
    @RequiresPermission("tenant:storage:bind")
    @AuditAction("tenant:storage:bind")
    public ApiResponse<StorageDirectory.BindingView> bind(@RequestBody StorageDirectory.BindingInput input) {
        return ApiResponse.ok(StorageDirectory.view(directory.bindInitial(input)));
    }
}
