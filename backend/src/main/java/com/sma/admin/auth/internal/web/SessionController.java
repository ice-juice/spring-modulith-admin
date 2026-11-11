package com.sma.admin.auth.internal.web;
import org.springframework.web.bind.annotation.*;
import com.sma.admin.common.*;
import com.sma.admin.common.security.RequiresPermission;
import com.sma.admin.auth.internal.service.SessionManager;

@RestController @RequestMapping("/api/auth/sessions")
@com.sma.admin.common.request.GovernedApi(group="auth.sessions",ownResource=false,mode=com.sma.admin.common.request.GovernedApi.Mode.NATIVE,reason="Session revocation and current native session state")
public class SessionController implements com.sma.admin.common.request.RequestAuthorizer {
    @Override public void authorizeRequest(String method,java.util.Map<String,String> variables){com.sma.admin.common.security.CurrentUser.require();}
    private final SessionManager manager;
    public SessionController(SessionManager manager) { this.manager=manager; }
    @GetMapping @RequiresPermission("auth:session:view")
    public ApiResponse<PageResult<SessionManager.View>> page(@ModelAttribute PageQuery page,@RequestParam(required=false) String keyword,
            @RequestAttribute("sma.session-id") long sessionId) { return ApiResponse.ok(manager.page(page,keyword,sessionId)); }
    @PostMapping("/{id}/revoke") @RequiresPermission("auth:session:revoke")
    public ApiResponse<Void> revoke(@PathVariable long id) { manager.forceLogout(id); return ApiResponse.ok(); }
}
