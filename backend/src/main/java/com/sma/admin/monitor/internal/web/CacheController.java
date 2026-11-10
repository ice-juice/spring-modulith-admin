package com.sma.admin.monitor.internal.web;
import java.util.List;
import org.springframework.web.bind.annotation.*;
import com.sma.admin.common.ApiResponse;
import com.sma.admin.common.security.RequiresPermission;
import com.sma.admin.monitor.internal.service.CacheService;
@RestController @RequestMapping("/api/monitor/caches")
public class CacheController {
 private final CacheService service;public CacheController(CacheService service){this.service=service;}
 @GetMapping @RequiresPermission("monitor:cache:view") public ApiResponse<List<CacheService.View>> list(){return ApiResponse.ok(service.list());}
 @DeleteMapping("/{id}") @RequiresPermission("monitor:cache:clear") public ApiResponse<Void> clear(@PathVariable String id){service.clear(id);return ApiResponse.ok();}
 @DeleteMapping @RequiresPermission("monitor:cache:clear") public ApiResponse<Void> clearAll(){service.clearAll();return ApiResponse.ok();}
}
