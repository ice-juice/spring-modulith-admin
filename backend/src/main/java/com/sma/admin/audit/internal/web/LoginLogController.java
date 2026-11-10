package com.sma.admin.audit.internal.web;
import java.time.Instant;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import com.sma.admin.common.*;
import com.sma.admin.common.security.RequiresPermission;
import com.sma.admin.audit.internal.service.LoginLogService;

@RestController @RequestMapping("/api/audit/logins")
public class LoginLogController {
 private final LoginLogService service;
 public LoginLogController(LoginLogService service){this.service=service;}
 @GetMapping @RequiresPermission("audit:login:view")
 public ApiResponse<PageResult<LoginLogService.View>> page(@ModelAttribute PageQuery page,@RequestParam(required=false)String username,@RequestParam(required=false)String status,
  @RequestParam(required=false)@DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) Instant from,@RequestParam(required=false)@DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME)Instant to){
  return ApiResponse.ok(service.page(page,username,status,from,to));
 }
}
