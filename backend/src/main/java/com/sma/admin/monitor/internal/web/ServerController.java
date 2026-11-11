package com.sma.admin.monitor.internal.web;
import org.springframework.web.bind.annotation.*;
import com.sma.admin.common.ApiResponse;
import com.sma.admin.common.security.RequiresPermission;
import com.sma.admin.monitor.internal.service.ServerService;
@RestController @RequestMapping("/api/monitor/server") @RequiresPermission("monitor:server:view")
@com.sma.admin.common.request.GovernedApi(group="monitor.server")
public class ServerController implements com.sma.admin.common.request.RequestAuthorizer {
 @Override public void authorizeRequest(String method,java.util.Map<String,String> variables){com.sma.admin.common.security.CurrentUser.require();}
 private final ServerService service;public ServerController(ServerService service){this.service=service;}
 @GetMapping public ApiResponse<ServerService.View> get(){return ApiResponse.ok(service.get());}
}
