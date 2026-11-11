package com.sma.admin.auth.internal;
import java.io.IOException;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import com.sma.admin.auth.internal.service.SessionService;
import com.sma.admin.common.*;

final class TokenAuthenticationFilter extends OncePerRequestFilter {
    static final String SESSION_ATTRIBUTE="sma.session-id";
    private static final java.util.Set<String> ANONYMOUS=java.util.Set.of("/api/auth/login","/api/auth/login-challenges/verify","/api/auth/captcha");
    private final SessionService sessions;
    private final ErrorResponseWriter errors;
    TokenAuthenticationFilter(SessionService sessions,ErrorResponseWriter errors) { this.sessions=sessions; this.errors=errors; }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        String path=path(request);
        return path.equals("/error") || path.equals("/actuator/info") || path.equals("/actuator/health") || path.startsWith("/actuator/health/");
    }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        // SecurityContext may have been established after the outer entry filter by another trusted mechanism.
        try(var checked=com.sma.admin.common.security.TenantEntryScope.open(com.sma.admin.common.security.TenantEntryScope.require())) { /* verify only */ }
        catch(org.springframework.security.access.AccessDeniedException mismatch) { errors.write(request,response,CommonErrorCode.FORBIDDEN); return; }
        if(ANONYMOUS.contains(path(request)) || publicTenantRead(request)) { chain.doFilter(request,response); return; }
        String authorization=request.getHeader("Authorization");
        if(authorization==null) { chain.doFilter(request,response); return; }
        if(!authorization.startsWith("Bearer ")) { errors.write(request,response,CommonErrorCode.UNAUTHORIZED); return; }
        var resolved=sessions.resolve(authorization.substring(7));
        if(resolved.isEmpty()) { errors.write(request,response,CommonErrorCode.UNAUTHORIZED); return; }
        var session=resolved.get();
        var entry=com.sma.admin.common.security.TenantEntryScope.require();
        if(session.principal().tenantId()!=entry.tenantId() || !session.principal().portal().equals(entry.portal())) {
            errors.write(request,response,CommonErrorCode.UNAUTHORIZED); return;
        }
        var authentication=new UsernamePasswordAuthenticationToken(session.principal(),null,
                session.principal().permissions().stream().map(SimpleGrantedAuthority::new).toList());
        var context=SecurityContextHolder.createEmptyContext(); context.setAuthentication(authentication); SecurityContextHolder.setContext(context);
        request.setAttribute(SESSION_ATTRIBUTE,session.session().getId());
        if(session.session().getLocked()&&!java.util.Set.of("/api/auth/me","/api/auth/logout","/api/auth/unlock").contains(path(request))) {
            errors.write(request,response,com.sma.admin.auth.AuthErrorCode.SCREEN_LOCKED); return;
        }
        chain.doFilter(request,response);
    }
    static boolean publicTenantRead(HttpServletRequest request) {
        if(!java.util.Set.of("GET","HEAD").contains(request.getMethod())) return false;
        String path=path(request);
        return path.equals("/api/tenant/entry") || path.matches("/api/tenant/brand-images/[1-9][0-9]{0,18}/[A-Z_]{1,32}");
    }
    private static String path(HttpServletRequest request) { return request.getRequestURI().substring(request.getContextPath().length()); }
}
