package com.sma.admin.monitor.internal.web;
import org.springframework.web.bind.annotation.*;
import com.sma.admin.common.ApiResponse;
import com.sma.admin.common.cache.CacheConfirmations;
import com.sma.admin.common.security.RequiresPermission;
import com.sma.admin.monitor.internal.service.CacheConfirmationService;
@RestController @RequestMapping("/api/monitor/caches")
@com.sma.admin.common.request.GovernedApi(group="monitor.caches",mode=com.sma.admin.common.request.GovernedApi.Mode.QUERY,reason="Authorized node inventory and existing task confirmation query")
public class CacheConfirmationController implements com.sma.admin.common.request.RequestAuthorizer {
    @Override public void authorizeRequest(String method,java.util.Map<String,String> variables){com.sma.admin.common.security.CurrentUser.require();}
    private final CacheConfirmationService service;
    public CacheConfirmationController(CacheConfirmationService service){this.service=service;}
    @GetMapping("/nodes") @RequiresPermission("monitor:cache:view")
    public ApiResponse<CacheConfirmations.Runtime> nodes(){return ApiResponse.ok(service.nodes());}
    @GetMapping("/tasks/{id}/confirmation") @RequiresPermission("monitor:cache:view")
    public ApiResponse<CacheConfirmations.Report> task(@PathVariable String id){return ApiResponse.ok(service.task(id));}
}
