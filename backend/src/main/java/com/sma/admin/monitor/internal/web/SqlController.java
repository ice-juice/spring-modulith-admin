package com.sma.admin.monitor.internal.web;
import org.springframework.web.bind.annotation.*;
import com.sma.admin.common.*;
import com.sma.admin.common.persistence.DatabaseTelemetry;
import com.sma.admin.common.security.RequiresPermission;
import com.sma.admin.monitor.internal.service.SqlService;
@RestController @RequestMapping("/api/monitor/sql")
@com.sma.admin.common.request.GovernedApi(group="monitor.sql",mode=com.sma.admin.common.request.GovernedApi.Mode.NATIVE,reason="Node telemetry reset; inspect current metrics after an uncertain response")
public class SqlController implements com.sma.admin.common.request.RequestAuthorizer {
 @Override public void authorizeRequest(String method,java.util.Map<String,String> variables){com.sma.admin.common.security.CurrentUser.require();}
 private final SqlService service;public SqlController(SqlService service){this.service=service;}
 @GetMapping @RequiresPermission("monitor:sql:view") public ApiResponse<PageResult<DatabaseTelemetry.Statement>> page(@ModelAttribute PageQuery page,@RequestParam(required=false)String keyword){return ApiResponse.ok(service.page(page,keyword));}
 @DeleteMapping @RequiresPermission("monitor:sql:reset") public ApiResponse<Void> reset(){service.reset();return ApiResponse.ok();}
}
