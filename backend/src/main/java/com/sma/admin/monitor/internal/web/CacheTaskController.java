package com.sma.admin.monitor.internal.web;

import java.util.List;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import com.sma.admin.common.ApiResponse;
import com.sma.admin.common.cache.CacheTasks;
import com.sma.admin.common.security.RequiresPermission;
import com.sma.admin.common.security.AuditAction;
import com.sma.admin.monitor.internal.service.CacheTaskService;

@RestController
@RequestMapping("/api/monitor/caches/tasks")
@com.sma.admin.common.request.GovernedApi(group="monitor.cache-tasks",mode=com.sma.admin.common.request.GovernedApi.Mode.NATIVE,reason="CacheTasks owns preview, submit idempotency and durable per-target state")
public class CacheTaskController implements com.sma.admin.common.request.RequestAuthorizer {
    @Override public void authorizeRequest(String method,java.util.Map<String,String> variables){com.sma.admin.common.security.CurrentUser.require();}
    private final CacheTaskService service;
    public CacheTaskController(CacheTaskService service){this.service=service;}
    @PostMapping("/preview") @RequiresPermission("monitor:cache:view") @AuditAction(value="monitor:cache:task-preview",path="/monitor/caches")
    public ApiResponse<CacheTasks.View> preview(@Valid @RequestBody CacheTaskService.PreviewInput input){return ApiResponse.ok(service.preview(input));}
    @PostMapping("/{id}/submit") @RequiresPermission("monitor:cache:view") @AuditAction(value="monitor:cache:task-submit",path="/monitor/caches")
    public ApiResponse<CacheTasks.View> submit(@PathVariable String id,@RequestHeader(value="Idempotency-Key",required=false) String key){return ApiResponse.ok(service.submit(id,key));}
    @GetMapping @RequiresPermission("monitor:cache:view")
    public ApiResponse<List<CacheTasks.View>> recent(@RequestParam(defaultValue="20") int limit){return ApiResponse.ok(service.recent(limit));}
    @GetMapping("/{id}") @RequiresPermission("monitor:cache:view")
    public ApiResponse<CacheTasks.View> get(@PathVariable String id){return ApiResponse.ok(service.get(id));}
    @GetMapping("/{id}/nodes") @RequiresPermission("monitor:cache:view")
    public ApiResponse<com.sma.admin.common.cache.CacheNodeScope> nodes(@PathVariable String id){return ApiResponse.ok(service.nodeScope(id));}
    @PostMapping("/{id}/cancel") @RequiresPermission("monitor:cache:view") @AuditAction(value="monitor:cache:task-cancel",path="/monitor/caches")
    public ApiResponse<CacheTasks.View> cancel(@PathVariable String id){return ApiResponse.ok(service.cancel(id));}
}
