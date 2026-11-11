package com.sma.admin.workspace.internal.web;
import org.springframework.web.bind.annotation.*;
import com.sma.admin.common.ApiResponse;
import com.sma.admin.common.security.PermitLoggedIn;
import com.sma.admin.workspace.internal.service.DashboardService;
@RestController @RequestMapping("/api/workspace/dashboard") @PermitLoggedIn
@com.sma.admin.common.request.GovernedApi(group="workspace.dashboard",mode=com.sma.admin.common.request.GovernedApi.Mode.QUERY,reason="Authorized dashboard projection")
public class DashboardController implements com.sma.admin.common.request.RequestAuthorizer {
 @Override public void authorizeRequest(String method,java.util.Map<String,String> variables){com.sma.admin.common.security.CurrentUser.require();}
 private final DashboardService service;public DashboardController(DashboardService service){this.service=service;}
 @GetMapping public ApiResponse<DashboardService.View> get(){return ApiResponse.ok(service.get());}
}
