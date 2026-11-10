package com.sma.admin.iam.internal.web;

import java.util.List;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import com.sma.admin.common.*;
import com.sma.admin.common.security.RequiresPermission;
import com.sma.admin.iam.internal.service.RoleService;

@RestController
@RequestMapping("/api/iam/roles")
public class RoleController {
    private final RoleService service;
    public RoleController(RoleService service) { this.service = service; }
    @GetMapping @RequiresPermission("iam:role:view")
    public ApiResponse<List<RoleService.View>> list() { return ApiResponse.ok(service.list()); }
    @GetMapping("/tree") @RequiresPermission("iam:role:view")
    public ApiResponse<List<TreeNode>> tree() { return ApiResponse.ok(service.tree()); }
    @GetMapping("/options") @RequiresPermission("iam:role:view")
    public ApiResponse<List<OptionItem>> options() { return ApiResponse.ok(service.options()); }
    @PostMapping @RequiresPermission("iam:role:create")
    public ApiResponse<RoleService.View> create(@Valid @RequestBody RoleService.Input input) { return ApiResponse.ok(service.save(null,input)); }
    @PutMapping("/{id}") @RequiresPermission("iam:role:update")
    public ApiResponse<RoleService.View> update(@PathVariable long id, @Valid @RequestBody RoleService.Input input) { return ApiResponse.ok(service.save(id,input)); }
    @DeleteMapping("/{id}") @RequiresPermission("iam:role:delete")
    public ApiResponse<Void> delete(@PathVariable long id) { service.delete(id); return ApiResponse.ok(); }
}
