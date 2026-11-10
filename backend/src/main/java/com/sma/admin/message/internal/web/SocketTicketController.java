package com.sma.admin.message.internal.web;
import org.springframework.web.bind.annotation.*;
import com.sma.admin.common.ApiResponse;
import com.sma.admin.common.security.PermitLoggedIn;
import com.sma.admin.message.internal.service.SocketTicketService;
@RestController @RequestMapping("/api/message/socket-ticket") @PermitLoggedIn
public class SocketTicketController {
 private final SocketTicketService service;public SocketTicketController(SocketTicketService service){this.service=service;}
 @PostMapping public ApiResponse<SocketTicketService.Ticket> issue(@RequestAttribute("sma.session-id") long sessionId){return ApiResponse.ok(service.issue(sessionId));}
}
