package com.sma.admin.iam.internal.web;

import java.util.Set;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import com.sma.admin.common.ApiResponse;
import com.sma.admin.common.security.RequiresPermission;
import com.sma.admin.iam.internal.service.RolePermissionService;

@RestController @RequestMapping("/api/iam/roles")
public class RolePermissionController {
    private final RolePermissionService service;
    public RolePermissionController(RolePermissionService service) { this.service=service; }
    public record Grant(@NotNull @Size(max=2000) Set<@NotNull Long> permissionIds) {}
    @GetMapping("/{id}/permissions") @RequiresPermission("iam:role:grant")
    public ApiResponse<Set<Long>> selected(@PathVariable long id) { return ApiResponse.ok(service.selected(id)); }
    @PutMapping("/{id}/permissions") @RequiresPermission("iam:role:grant")
    public ApiResponse<Void> grant(@PathVariable long id,@Valid @RequestBody Grant input) { service.replace(id,input.permissionIds()); return ApiResponse.ok(); }
}
