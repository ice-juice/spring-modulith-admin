package com.sma.admin.iam.internal.web;
import java.util.List;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import com.sma.admin.common.*;
import com.sma.admin.common.security.RequiresPermission;
import com.sma.admin.iam.internal.service.PermissionService;

@RestController @RequestMapping("/api/iam/permissions")
public class PermissionController {
    private final PermissionService service;
    public PermissionController(PermissionService service) { this.service=service; }
    @GetMapping @RequiresPermission("iam:permission:view")
    public ApiResponse<List<PermissionService.View>> list() { return ApiResponse.ok(service.list()); }
    @GetMapping("/tree") @RequiresPermission("iam:permission:view")
    public ApiResponse<List<PermissionService.View>> tree() { return ApiResponse.ok(service.tree()); }
    @PostMapping @RequiresPermission("iam:permission:create")
    public ApiResponse<PermissionService.View> create(@Valid @RequestBody PermissionService.Input input) { return ApiResponse.ok(service.save(null,input)); }
    @PutMapping("/{id}") @RequiresPermission("iam:permission:update")
    public ApiResponse<PermissionService.View> update(@PathVariable long id,@Valid @RequestBody PermissionService.Input input) { return ApiResponse.ok(service.save(id,input)); }
    @DeleteMapping("/{id}") @RequiresPermission("iam:permission:delete")
    public ApiResponse<Void> delete(@PathVariable long id) { service.delete(id); return ApiResponse.ok(); }
}
