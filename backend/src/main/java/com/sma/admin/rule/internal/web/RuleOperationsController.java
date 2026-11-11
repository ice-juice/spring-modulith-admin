package com.sma.admin.rule.internal.web;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
import com.sma.admin.common.ApiResponse;
import com.sma.admin.common.security.RequiresPermission;
import com.sma.admin.rule.RuleCommands.*;
import com.sma.admin.rule.internal.service.RuleOperationsService;
import com.sma.admin.rule.internal.service.RuleService;

@RestController
@RequestMapping("/api/rule")
@com.sma.admin.common.request.GovernedApi(group="rule.operations")
public class RuleOperationsController implements com.sma.admin.common.request.RequestAuthorizer {
 @Override public void authorizeRequest(String method,java.util.Map<String,String> variables){com.sma.admin.common.security.CurrentUser.require();}
    private final RuleOperationsService operations;
    private final RuleService rules;
    public RuleOperationsController(RuleOperationsService operations, RuleService rules) { this.operations = operations; this.rules = rules; }
    @GetMapping("/instances/{id}/executions/{executionId}") @RequiresPermission("rule:execution:view")
    public ApiResponse<ExecutionDetail> detail(@PathVariable long id, @PathVariable long executionId) { return ApiResponse.ok(operations.detail(id, executionId)); }
    @PostMapping("/executions/search") @RequiresPermission("rule:execution:view")
    @com.sma.admin.common.request.GovernedApi(group="rule.operations",mode=com.sma.admin.common.request.GovernedApi.Mode.QUERY,reason="Read-only execution search uses a body to avoid query data in URLs")
    public ApiResponse<List<ExecutionSummary>> search(@RequestBody String body) {
        return ApiResponse.ok(operations.search(rules.decode(body, ExecutionSearch.class)));
    }
    @GetMapping("/instances/{id}/execution-statistics") @RequiresPermission("rule:execution:view")
    public ApiResponse<List<Map<String, Object>>> statistics(@PathVariable long id, @RequestParam Instant from, @RequestParam Instant until) {
        return ApiResponse.ok(operations.statistics(id, from, until));
    }
    @GetMapping("/resources") @RequiresPermission("rule:maintenance:view")
    public ApiResponse<Map<String, Integer>> resources() { return ApiResponse.ok(operations.resources()); }
    @PostMapping("/resources/clear") @RequiresPermission("rule:maintenance:clean")
    @com.sma.admin.common.request.GovernedApi(group="rule.operations",mode=com.sma.admin.common.request.GovernedApi.Mode.NATIVE,reason="Node runtime cleanup; inspect resource state after an uncertain response")
    public ApiResponse<Map<String, Integer>> clear() { return ApiResponse.ok(operations.clearResources()); }
    @PostMapping("/retention/cleanup") @RequiresPermission("rule:maintenance:clean")
    public ApiResponse<Cleanup> cleanup(@RequestBody String body) { return ApiResponse.ok(operations.cleanup(rules.decode(body, Retention.class))); }
}
