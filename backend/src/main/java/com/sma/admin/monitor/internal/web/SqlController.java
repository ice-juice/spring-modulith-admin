package com.sma.admin.monitor.internal.web;
import org.springframework.web.bind.annotation.*;
import com.sma.admin.common.*;
import com.sma.admin.common.persistence.DatabaseTelemetry;
import com.sma.admin.common.security.RequiresPermission;
import com.sma.admin.monitor.internal.service.SqlService;
@RestController @RequestMapping("/api/monitor/sql")
public class SqlController {
 private final SqlService service;public SqlController(SqlService service){this.service=service;}
 @GetMapping @RequiresPermission("monitor:sql:view") public ApiResponse<PageResult<DatabaseTelemetry.Statement>> page(@ModelAttribute PageQuery page,@RequestParam(required=false)String keyword){return ApiResponse.ok(service.page(page,keyword));}
 @DeleteMapping @RequiresPermission("monitor:sql:reset") public ApiResponse<Void> reset(){service.reset();return ApiResponse.ok();}
}
