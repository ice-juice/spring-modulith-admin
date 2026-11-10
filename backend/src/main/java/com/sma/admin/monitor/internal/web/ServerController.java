package com.sma.admin.monitor.internal.web;
import org.springframework.web.bind.annotation.*;
import com.sma.admin.common.ApiResponse;
import com.sma.admin.common.security.RequiresPermission;
import com.sma.admin.monitor.internal.service.ServerService;
@RestController @RequestMapping("/api/monitor/server") @RequiresPermission("monitor:server:view")
public class ServerController {
 private final ServerService service;public ServerController(ServerService service){this.service=service;}
 @GetMapping public ApiResponse<ServerService.View> get(){return ApiResponse.ok(service.get());}
}
