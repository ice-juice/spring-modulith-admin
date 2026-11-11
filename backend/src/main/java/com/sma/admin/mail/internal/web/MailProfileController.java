package com.sma.admin.mail.internal.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.*;
import com.sma.admin.common.ApiResponse;
import com.sma.admin.common.PageQuery;
import com.sma.admin.common.PageResult;
import com.sma.admin.common.security.RequiresPermission;
import com.sma.admin.mail.internal.service.MailProfileService;

@RestController @RequestMapping("/api/mail/profiles")
@com.sma.admin.common.request.GovernedApi(group="mail.profiles")
public class MailProfileController implements com.sma.admin.common.request.RequestAuthorizer {
    @Override public void authorizeRequest(String method,java.util.Map<String,String> pathVariables){com.sma.admin.common.security.CurrentUser.require();}
    private final MailProfileService service;
    public MailProfileController(MailProfileService service) { this.service = service; }
    public record Update(@Positive long expectedVersion, @Valid MailProfileService.Input configuration) {}
    public record Version(@Positive long expectedVersion) {}

    @GetMapping @RequiresPermission("mail:profile:view")
    public ApiResponse<PageResult<MailProfileService.View>> page(@ModelAttribute PageQuery page,
            @RequestParam(required = false) String keyword) { return ApiResponse.ok(service.page(page, keyword)); }

    @GetMapping("/{id}") @RequiresPermission("mail:profile:view")
    public ApiResponse<MailProfileService.View> get(@PathVariable long id) { return ApiResponse.ok(service.get(id)); }

    @PostMapping @RequiresPermission("mail:profile:manage")
    public ApiResponse<MailProfileService.View> create(@Valid @RequestBody MailProfileService.Input input) { return ApiResponse.ok(service.create(input)); }

    @PutMapping("/{id}") @RequiresPermission("mail:profile:manage")
    public ApiResponse<MailProfileService.View> update(@PathVariable long id, @Valid @RequestBody Update input) {
        return ApiResponse.ok(service.update(id, input.expectedVersion(), input.configuration()));
    }

    @PostMapping("/{id}/disable") @RequiresPermission("mail:profile:manage")
    public ApiResponse<MailProfileService.View> disable(@PathVariable long id, @Valid @RequestBody Version input) {
        return ApiResponse.ok(service.disable(id, input.expectedVersion()));
    }
}
