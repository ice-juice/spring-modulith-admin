package com.sma.admin.monitor.internal.web;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;
import com.sma.admin.common.*;
import com.sma.admin.common.security.RequiresPermission;
import com.sma.admin.monitor.internal.service.PublicationService;
@RestController @RequestMapping("/api/monitor/publications")
@com.sma.admin.common.request.GovernedApi(group="monitor.publications",mode=com.sma.admin.common.request.GovernedApi.Mode.NATIVE,reason="Framework publication state and at-least-once resubmission; no local atomic receipt")
public class PublicationController implements com.sma.admin.common.request.RequestAuthorizer {
 @Override public void authorizeRequest(String method,java.util.Map<String,String> variables){com.sma.admin.common.security.CurrentUser.require();}
 private final PublicationService service;public PublicationController(PublicationService service){this.service=service;}
 @GetMapping @RequiresPermission("monitor:event:view") public ApiResponse<PageResult<PublicationService.View>> page(@ModelAttribute PageQuery page,@RequestParam(required=false)String status){return ApiResponse.ok(service.page(page,status));}
 @PostMapping("/{id}/retry") @RequiresPermission("monitor:event:retry") public ApiResponse<Void> retry(@PathVariable UUID id){service.retry(id);return ApiResponse.ok();}
}
