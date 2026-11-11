package com.sma.admin.mail.internal.web;

import org.springframework.web.bind.annotation.*;
import com.sma.admin.common.*;
import com.sma.admin.common.security.RequiresPermission;
import com.sma.admin.mail.*;
import com.sma.admin.mail.internal.service.MailDeliveryAdminService;

/** Management only. Business send operations go through an authorized business scene, not an open mail relay. */
@RestController @RequestMapping("/api/mail/deliveries")
@com.sma.admin.common.request.GovernedApi(group="mail.deliveries",mode=com.sma.admin.common.request.GovernedApi.Mode.NATIVE,reason="Mail outbox owns cancellation state, queue reservation and delivery receipts")
public class MailDeliveryController implements com.sma.admin.common.request.RequestAuthorizer {
    @Override public void authorizeRequest(String method,java.util.Map<String,String> pathVariables){com.sma.admin.common.security.CurrentUser.require();}
    private final MailDeliveryAdminService admin;private final MailSendApi sending;private final MailStatusQuery status;
    public MailDeliveryController(MailDeliveryAdminService admin,MailSendApi sending,MailStatusQuery status){this.admin=admin;this.sending=sending;this.status=status;}
    @GetMapping @RequiresPermission("mail:delivery:view")
    public ApiResponse<PageResult<MailDeliveryView>> page(@ModelAttribute PageQuery page,@RequestParam(required=false)String state,@RequestParam(required=false)String application){return ApiResponse.ok(admin.page(page,state,application));}
    @GetMapping("/{id}") @RequiresPermission("mail:delivery:view")
    public ApiResponse<MailDeliveryView> get(@PathVariable long id){return ApiResponse.ok(status.get(id));}
    @PostMapping("/{id}/cancel") @RequiresPermission("mail:delivery:manage")
    public ApiResponse<MailDeliveryView> cancel(@PathVariable long id){return ApiResponse.ok(sending.cancel(id));}
}
