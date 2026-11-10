package com.sma.admin.audit.internal.web;
import java.time.Instant;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import com.sma.admin.common.*;
import com.sma.admin.common.security.RequiresPermission;
import com.sma.admin.audit.internal.service.OperationLogService;
@RestController @RequestMapping("/api/audit/operations")
public class OperationLogController {
 private final OperationLogService service;public OperationLogController(OperationLogService service){this.service=service;}
 @GetMapping @RequiresPermission("audit:operation:view")
 public ApiResponse<PageResult<OperationLogService.View>> page(@ModelAttribute PageQuery page,@RequestParam(required=false)String username,@RequestParam(required=false)String action,@RequestParam(required=false)Boolean success,
  @RequestParam(required=false)@DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME)Instant from,@RequestParam(required=false)@DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME)Instant to){
  return ApiResponse.ok(service.page(page,username,action,success,from,to));
 }
}
