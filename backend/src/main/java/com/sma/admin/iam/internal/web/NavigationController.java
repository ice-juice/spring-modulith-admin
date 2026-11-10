package com.sma.admin.iam.internal.web;
import java.util.List;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import com.sma.admin.common.ApiResponse;
import com.sma.admin.common.security.RequiresPermission;
import com.sma.admin.iam.internal.service.NavigationService;
@RestController @RequestMapping("/api/iam/navigation")
public class NavigationController {
    private final NavigationService service;
    public NavigationController(NavigationService service) { this.service=service; }
    @GetMapping @RequiresPermission("iam:navigation:view")
    public ApiResponse<List<NavigationService.View>> list() { return ApiResponse.ok(service.list()); }
    @PostMapping @RequiresPermission("iam:navigation:create")
    public ApiResponse<NavigationService.View> create(@Valid @RequestBody NavigationService.Input input) { return ApiResponse.ok(service.save(null,input)); }
    @PutMapping("/{id}") @RequiresPermission("iam:navigation:update")
    public ApiResponse<NavigationService.View> update(@PathVariable long id,@Valid @RequestBody NavigationService.Input input) { return ApiResponse.ok(service.save(id,input)); }
    @DeleteMapping("/{id}") @RequiresPermission("iam:navigation:delete")
    public ApiResponse<Void> delete(@PathVariable long id) { service.delete(id); return ApiResponse.ok(); }
}
