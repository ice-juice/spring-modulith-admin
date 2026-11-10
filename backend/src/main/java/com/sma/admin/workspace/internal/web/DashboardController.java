package com.sma.admin.workspace.internal.web;
import org.springframework.web.bind.annotation.*;
import com.sma.admin.common.ApiResponse;
import com.sma.admin.common.security.PermitLoggedIn;
import com.sma.admin.workspace.internal.service.DashboardService;
@RestController @RequestMapping("/api/workspace/dashboard") @PermitLoggedIn
public class DashboardController {
 private final DashboardService service;public DashboardController(DashboardService service){this.service=service;}
 @GetMapping public ApiResponse<DashboardService.View> get(){return ApiResponse.ok(service.get());}
}
