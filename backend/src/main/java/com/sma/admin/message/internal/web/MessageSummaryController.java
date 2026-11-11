package com.sma.admin.message.internal.web;
import org.springframework.web.bind.annotation.*;
import com.sma.admin.common.ApiResponse;
import com.sma.admin.common.security.PermitLoggedIn;
import com.sma.admin.message.internal.service.MessageSummaryService;
@RestController @RequestMapping("/api/message/summary") @PermitLoggedIn
@com.sma.admin.common.request.GovernedApi(group="message.summary")
public class MessageSummaryController implements com.sma.admin.common.request.RequestAuthorizer {
 @Override public void authorizeRequest(String method,java.util.Map<String,String> variables){
  // Only actor-owned operation metadata can replay. Actual content remains behind
  // the module's recipient/owner, tenant and portal checks on every domain call.
  com.sma.admin.common.security.CurrentUser.require();
 }
 private final MessageSummaryService service;public MessageSummaryController(MessageSummaryService service){this.service=service;}
 @GetMapping public ApiResponse<MessageSummaryService.View> get(){return ApiResponse.ok(service.get());}
}
