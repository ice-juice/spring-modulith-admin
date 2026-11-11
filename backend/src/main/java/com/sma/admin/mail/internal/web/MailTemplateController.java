package com.sma.admin.mail.internal.web;

import java.util.Map;
import org.springframework.web.bind.annotation.*;
import com.sma.admin.common.*;
import com.sma.admin.common.security.RequiresPermission;
import com.sma.admin.mail.internal.service.MailTemplateRenderer;
import com.sma.admin.mail.internal.service.MailTemplateService;

@RestController @RequestMapping("/api/mail/templates")
@com.sma.admin.common.request.GovernedApi(group="mail.templates")
public class MailTemplateController implements com.sma.admin.common.request.RequestAuthorizer {
    @Override public void authorizeRequest(String method,java.util.Map<String,String> pathVariables){com.sma.admin.common.security.CurrentUser.require();}
    private final MailTemplateService service;
    public MailTemplateController(MailTemplateService service) { this.service = service; }
    public record Update(long expectedVersion, MailTemplateService.Input configuration) {}
    public record Version(long expectedVersion) {}
    @GetMapping @RequiresPermission("mail:template:view")
    public ApiResponse<PageResult<MailTemplateService.View>> page(@ModelAttribute PageQuery page, @RequestParam(required=false) String keyword) { return ApiResponse.ok(service.page(page, keyword)); }
    @GetMapping("/{id}") @RequiresPermission("mail:template:view")
    public ApiResponse<MailTemplateService.View> get(@PathVariable long id) { return ApiResponse.ok(service.get(id)); }
    @PostMapping @RequiresPermission("mail:template:manage")
    public ApiResponse<MailTemplateService.View> create(@RequestBody MailTemplateService.Input input) { return ApiResponse.ok(service.create(input)); }
    @PutMapping("/{id}") @RequiresPermission("mail:template:manage")
    public ApiResponse<MailTemplateService.View> update(@PathVariable long id, @RequestBody Update input) { return ApiResponse.ok(service.update(id, input.expectedVersion(), input.configuration())); }
    @PostMapping("/{id}/publish") @RequiresPermission("mail:template:publish")
    public ApiResponse<MailTemplateService.View> publish(@PathVariable long id, @RequestBody Version input) { return ApiResponse.ok(service.publish(id, input.expectedVersion())); }
    @PostMapping("/{id}/disable") @RequiresPermission("mail:template:publish")
    public ApiResponse<MailTemplateService.View> disable(@PathVariable long id, @RequestBody Version input) { return ApiResponse.ok(service.disable(id, input.expectedVersion())); }
    @PostMapping("/{id}/preview") @RequiresPermission("mail:template:view")
    @com.sma.admin.common.request.GovernedApi(group="mail.templates",mode=com.sma.admin.common.request.GovernedApi.Mode.QUERY,reason="Pure template rendering; does not enqueue or send mail")
    public ApiResponse<MailTemplateRenderer.Rendered> preview(@PathVariable long id, @RequestBody Map<String,String> variables) { return ApiResponse.ok(service.preview(id, variables)); }
}
