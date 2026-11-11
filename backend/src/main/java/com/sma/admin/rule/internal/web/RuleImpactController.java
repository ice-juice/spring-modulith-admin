package com.sma.admin.rule.internal.web;

import org.springframework.web.bind.annotation.*;
import com.sma.admin.common.ApiResponse;
import com.sma.admin.common.security.RequiresPermission;
import com.sma.admin.rule.RuleCommands.DependencyImpact;
import com.sma.admin.rule.internal.service.RuleImpactService;

@RestController
@RequestMapping("/api/rule/templates")
@com.sma.admin.common.request.GovernedApi(group="rule.assets")
public class RuleImpactController implements com.sma.admin.common.request.RequestAuthorizer {
 @Override public void authorizeRequest(String method,java.util.Map<String,String> variables){com.sma.admin.common.security.CurrentUser.require();}
    private final RuleImpactService impacts;
    public RuleImpactController(RuleImpactService impacts) { this.impacts = impacts; }
    @GetMapping("/{id}/dependency-impact") @RequiresPermission("rule:template:view")
    public ApiResponse<DependencyImpact> impact(@PathVariable long id) { return ApiResponse.ok(impacts.impact(id)); }
}
