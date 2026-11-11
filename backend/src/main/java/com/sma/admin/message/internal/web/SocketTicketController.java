package com.sma.admin.message.internal.web;
import org.springframework.web.bind.annotation.*;
import com.sma.admin.common.ApiResponse;
import com.sma.admin.common.security.PermitLoggedIn;
import com.sma.admin.message.internal.service.SocketTicketService;
@RestController @RequestMapping("/api/message/socket-ticket") @PermitLoggedIn
@com.sma.admin.common.request.GovernedApi(group="message.socket-ticket",ownResource=true,mode=com.sma.admin.common.request.GovernedApi.Mode.NATIVE,reason="Bound one-use socket ticket; never persist or replay ticket material")
public class SocketTicketController implements com.sma.admin.common.request.RequestAuthorizer {
    @Override public void authorizeRequest(String method,java.util.Map<String,String> variables){com.sma.admin.common.security.CurrentUser.require();}
 private final SocketTicketService service;public SocketTicketController(SocketTicketService service){this.service=service;}
 @PostMapping public ApiResponse<SocketTicketService.Ticket> issue(@RequestAttribute("sma.session-id") long sessionId){return ApiResponse.ok(service.issue(sessionId));}
}
