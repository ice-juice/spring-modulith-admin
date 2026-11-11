package com.sma.admin.rule.internal.web;

import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import com.sma.admin.common.ApiResponse;
import com.sma.admin.common.security.RequiresPermission;
import com.sma.admin.rule.RuleCommands.*;
import com.sma.admin.rule.internal.service.RuleService;
import com.sma.admin.rule.internal.service.RuleTestService;

@RestController @RequestMapping("/api/rule/templates/{templateId}")
@com.sma.admin.common.request.GovernedApi(group="rule.tests")
public class RuleTestController implements com.sma.admin.common.request.RequestAuthorizer {
 @Override public void authorizeRequest(String method,java.util.Map<String,String> variables){com.sma.admin.common.security.CurrentUser.require();}
    private final RuleService commands;
    private final RuleTestService tests;
    public RuleTestController(RuleService commands, RuleTestService tests) { this.commands = commands; this.tests = tests; }
    @GetMapping("/testcases") @RequiresPermission("rule:test:view")
    public ApiResponse<List<CaseView>> list(@PathVariable long templateId) { return ApiResponse.ok(tests.list(templateId)); }
    @PostMapping(value = "/testcases", consumes = MediaType.APPLICATION_JSON_VALUE) @RequiresPermission("rule:test:edit")
    public ApiResponse<CaseView> create(@PathVariable long templateId, @RequestBody String body) {
        return ApiResponse.ok(tests.save(templateId, null, commands.decode(body, CaseSave.class)));
    }
    @PutMapping(value = "/testcases/{id}", consumes = MediaType.APPLICATION_JSON_VALUE) @RequiresPermission("rule:test:edit")
    public ApiResponse<CaseView> save(@PathVariable long templateId, @PathVariable long id, @RequestBody String body) {
        return ApiResponse.ok(tests.save(templateId, id, commands.decode(body, CaseSave.class)));
    }
    @PostMapping(value = "/test-runs", consumes = MediaType.APPLICATION_JSON_VALUE) @RequiresPermission("rule:test:run")
    public ApiResponse<TestRun> run(@PathVariable long templateId, @RequestBody String body) {
        return ApiResponse.ok(tests.run(templateId, commands.decode(body, RunInput.class)));
    }
    @GetMapping("/test-runs") @RequiresPermission("rule:test:view")
    public ApiResponse<List<TestRun>> runs(@PathVariable long templateId, @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(tests.runs(templateId, limit));
    }
}
