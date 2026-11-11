package com.sma.admin.identity.internal.web;
import jakarta.servlet.http.*;
import org.springframework.web.bind.annotation.*;
import com.sma.admin.common.ApiResponse;
import com.sma.admin.common.security.PermitApplicationSelf;
import com.sma.admin.identity.internal.ApplicationHttp;
import com.sma.admin.identity.internal.ApplicationSessionCookies;
import com.sma.admin.identity.internal.service.IdentitySessionStore;

/** Shared platform self-service, authenticated for the exact requested client before the handler runs. */
@RestController @RequestMapping("/app/identity/sessions")
public class IdentitySessionController {
    private final IdentitySessionStore sessions;private final ApplicationSessionCookies cookies;
    public IdentitySessionController(IdentitySessionStore sessions,ApplicationSessionCookies cookies){this.sessions=sessions;this.cookies=cookies;}
    @GetMapping("/current") @PermitApplicationSelf
    public ApiResponse<IdentityApplicationViews.Session> current(HttpServletRequest request){
        var access=(IdentitySessionStore.Access)request.getAttribute(ApplicationHttp.ACCESS);var user=access.principal();var client=ApplicationHttp.client(request);
        String csrf=client.web()?ApplicationHttp.cookie(request,client,"csrf"):null;
        if(!IdentitySessionStore.csrfMatches(csrf,access.csrfHash()))csrf=null;
        return ApiResponse.ok(new IdentityApplicationViews.Session(user.subjectId(),user.realmId(),user.clientId(),user.audience(),access.mode(),access.accessExpiresAt(),access.refreshExpiresAt(),csrf,null,null));
    }
    @DeleteMapping("/current") @PermitApplicationSelf
    public ApiResponse<Void> logout(HttpServletRequest request,HttpServletResponse response){
        sessions.logout();var client=ApplicationHttp.client(request);
        cookies.clear(response,client);
        return ApiResponse.ok();
    }
}
