package com.sma.admin.rule.internal.web;

import org.springframework.web.bind.annotation.*;
import com.sma.admin.common.ApiResponse;
import com.sma.admin.common.security.RequiresPermission;
import com.sma.admin.rule.RuleCommands.*;
import com.sma.admin.rule.internal.service.RuleBatchService;
import com.sma.admin.rule.internal.service.RuleService;

@RestController @RequestMapping("/api/rule/migration-batches")
@com.sma.admin.common.request.GovernedApi(group="rule.batches",mode=com.sma.admin.common.request.GovernedApi.Mode.NATIVE,reason="Module operationKey and per-item transactions; recover via BatchReport")
public class RuleBatchController implements com.sma.admin.common.request.RequestAuthorizer {
 @Override public void authorizeRequest(String method,java.util.Map<String,String> variables){com.sma.admin.common.security.CurrentUser.require();}
    private final RuleBatchService batches;
    private final RuleService commands;
    public RuleBatchController(RuleBatchService batches, RuleService commands) { this.batches = batches; this.commands = commands; }
    @PostMapping(consumes = "application/json") @RequiresPermission("rule:batch:migrate")
    public ApiResponse<BatchReport> migrate(@RequestBody String body) { return ApiResponse.ok(batches.migrate(commands.decode(body, BatchMigration.class))); }
    @GetMapping("/{id}") @RequiresPermission("rule:batch:view")
    public ApiResponse<BatchReport> get(@PathVariable long id) { return ApiResponse.ok(batches.get(id)); }
    @GetMapping("/operations/{operationKey}") @RequiresPermission("rule:batch:view")
    public ApiResponse<BatchLookup> operation(@PathVariable String operationKey, jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return ApiResponse.ok(batches.byOperationKey(operationKey));
    }
}
