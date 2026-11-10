package com.sma.admin.message.internal.web;
import org.springframework.web.bind.annotation.*;
import com.sma.admin.common.ApiResponse;
import com.sma.admin.common.security.PermitLoggedIn;
import com.sma.admin.message.internal.service.MessageSummaryService;
@RestController @RequestMapping("/api/message/summary") @PermitLoggedIn
public class MessageSummaryController {
 private final MessageSummaryService service;public MessageSummaryController(MessageSummaryService service){this.service=service;}
 @GetMapping public ApiResponse<MessageSummaryService.View> get(){return ApiResponse.ok(service.get());}
}
