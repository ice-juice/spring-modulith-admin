package com.sma.admin.monitor.internal.web;
import java.util.List;
import org.springframework.web.bind.annotation.*;
import com.sma.admin.common.ApiResponse;
import com.sma.admin.common.security.RequiresPermission;
import com.sma.admin.monitor.internal.service.CacheService;
import com.sma.admin.monitor.internal.service.CacheDirectoryService;
import com.sma.admin.monitor.internal.service.CacheControlService;
import com.sma.admin.common.cache.CacheGovernance;
import jakarta.validation.Valid;
@RestController @RequestMapping("/api/monitor/caches")
@com.sma.admin.common.request.GovernedApi(group="monitor.caches")
public class CacheController implements com.sma.admin.common.request.RequestAuthorizer {
 @Override public void authorizeRequest(String method,java.util.Map<String,String> variables){com.sma.admin.common.security.CurrentUser.require();}
 private final CacheService service;private final CacheDirectoryService directory;private final CacheControlService controls;
 public CacheController(CacheService service,CacheDirectoryService directory,CacheControlService controls){this.service=service;this.directory=directory;this.controls=controls;}
 @GetMapping("/{id}/policy") @RequiresPermission("monitor:cache:view") public ApiResponse<CacheGovernance.State> policy(@PathVariable String id){return ApiResponse.ok(controls.state(id));}
 @PutMapping("/{id}/policy") @RequiresPermission("monitor:cache:policy") public ApiResponse<CacheGovernance.State> policy(@PathVariable String id,@Valid @RequestBody CacheControlService.PolicyInput input){return ApiResponse.ok(controls.policy(id,input));}
 @PostMapping("/{id}/policy/reset") @RequiresPermission("monitor:cache:policy") public ApiResponse<CacheGovernance.State> resetPolicy(@PathVariable String id,@Valid @RequestBody CacheControlService.Version input){return ApiResponse.ok(controls.command(id,CacheGovernance.Action.RESET_POLICY,input));}
 @PostMapping("/{id}/pause") @RequiresPermission("monitor:cache:pause") public ApiResponse<CacheGovernance.State> pause(@PathVariable String id,@Valid @RequestBody CacheControlService.Version input){return ApiResponse.ok(controls.command(id,CacheGovernance.Action.PAUSE,input));}
 @PostMapping("/{id}/resume") @RequiresPermission("monitor:cache:pause") public ApiResponse<CacheGovernance.State> resume(@PathVariable String id,@Valid @RequestBody CacheControlService.Version input){return ApiResponse.ok(controls.command(id,CacheGovernance.Action.RESUME,input));}
 @PostMapping("/{id}/statistics/reset") @RequiresPermission("monitor:cache:reset-stats") public ApiResponse<CacheGovernance.State> resetStatistics(@PathVariable String id,@Valid @RequestBody CacheControlService.Version input){return ApiResponse.ok(controls.command(id,CacheGovernance.Action.RESET_STATS,input));}
 @GetMapping("/{id}/history") @RequiresPermission("monitor:cache:view") public ApiResponse<List<CacheGovernance.History>> history(@PathVariable String id,@RequestParam(defaultValue="50") int limit){return ApiResponse.ok(controls.history(id,limit));}
 @GetMapping("/directory") @RequiresPermission("monitor:cache:view") public ApiResponse<List<CacheDirectoryService.View>> directory(@ModelAttribute CacheDirectoryService.Filter filter){return ApiResponse.ok(directory.list(filter));}
 @GetMapping("/directory/{id}") @RequiresPermission("monitor:cache:view") public ApiResponse<CacheDirectoryService.View> detail(@PathVariable String id){return ApiResponse.ok(directory.detail(id));}
 @GetMapping @RequiresPermission("monitor:cache:view") public ApiResponse<List<CacheService.View>> list(){return ApiResponse.ok(service.list());}
 @com.sma.admin.common.request.GovernedApi(group="monitor.caches",mode=com.sma.admin.common.request.GovernedApi.Mode.NATIVE,reason="Registered cache adapters may have runtime effects; inspect region state on uncertainty")
 @DeleteMapping("/{id}") @RequiresPermission("monitor:cache:clear") public ApiResponse<Void> clear(@PathVariable String id){service.clear(id);return ApiResponse.ok();}
 @com.sma.admin.common.request.GovernedApi(group="monitor.caches",mode=com.sma.admin.common.request.GovernedApi.Mode.NATIVE,reason="Sequential cache adapters can partially apply; never claim an atomic local receipt")
 @DeleteMapping @RequiresPermission("monitor:cache:clear") public ApiResponse<Void> clearAll(){service.clearAll();return ApiResponse.ok();}
}
