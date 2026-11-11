package com.sma.admin.iam.internal.web;

import java.util.List;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import com.sma.admin.common.*;
import com.sma.admin.common.security.RequiresPermission;
import com.sma.admin.iam.internal.service.DepartmentService;

@RestController
@RequestMapping("/api/iam/departments")
@com.sma.admin.common.request.GovernedApi(group="iam.departments")
public class DepartmentController implements com.sma.admin.common.request.RequestAuthorizer {
    @Override public void authorizeRequest(String method,java.util.Map<String,String> variables) {
        // The receipt is actor-owned metadata; domain reads and writes retain their
        // current tenant, portal, data-scope and object authorization in the service.
        com.sma.admin.common.security.CurrentUser.require();
    }
    private final DepartmentService service;
    public DepartmentController(DepartmentService service) { this.service = service; }
    @GetMapping @RequiresPermission("iam:department:view")
    public ApiResponse<List<DepartmentService.View>> list(@RequestParam(required = false) String keyword) { return ApiResponse.ok(service.list(keyword)); }
    @GetMapping("/tree") @RequiresPermission("iam:department:view")
    public ApiResponse<List<TreeNode>> tree(@RequestParam(defaultValue = "false") boolean enabledOnly) { return ApiResponse.ok(service.tree(enabledOnly)); }
    @PostMapping @RequiresPermission("iam:department:create")
    public ApiResponse<DepartmentService.View> create(@Valid @RequestBody DepartmentService.Input input) { return ApiResponse.ok(service.save(null, input)); }
    @PutMapping("/{id}") @RequiresPermission("iam:department:update")
    public ApiResponse<DepartmentService.View> update(@PathVariable long id, @Valid @RequestBody DepartmentService.Input input) { return ApiResponse.ok(service.save(id, input)); }
    @DeleteMapping("/{id}") @RequiresPermission("iam:department:delete")
    public ApiResponse<Void> delete(@PathVariable long id) { service.delete(id); return ApiResponse.ok(); }
}
