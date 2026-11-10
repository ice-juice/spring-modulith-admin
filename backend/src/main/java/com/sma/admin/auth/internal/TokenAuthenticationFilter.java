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
    private final SessionService sessions;
    private final ErrorResponseWriter errors;
    TokenAuthenticationFilter(SessionService sessions,ErrorResponseWriter errors) { this.sessions=sessions; this.errors=errors; }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().equals("/api/auth/login")||request.getRequestURI().equals("/api/auth/captcha");
    }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        String authorization=request.getHeader("Authorization");
        if(authorization==null) { chain.doFilter(request,response); return; }
        if(!authorization.startsWith("Bearer ")) { errors.write(request,response,CommonErrorCode.UNAUTHORIZED); return; }
        var resolved=sessions.resolve(authorization.substring(7));
        if(resolved.isEmpty()) { errors.write(request,response,CommonErrorCode.UNAUTHORIZED); return; }
        var session=resolved.get();
        var authentication=new UsernamePasswordAuthenticationToken(session.principal(),null,
                session.principal().permissions().stream().map(SimpleGrantedAuthority::new).toList());
        var context=SecurityContextHolder.createEmptyContext(); context.setAuthentication(authentication); SecurityContextHolder.setContext(context);
        request.setAttribute(SESSION_ATTRIBUTE,session.session().getId());
        if(session.session().getLocked()&&!java.util.Set.of("/api/auth/me","/api/auth/logout","/api/auth/unlock").contains(request.getRequestURI())) {
            errors.write(request,response,com.sma.admin.auth.AuthErrorCode.SCREEN_LOCKED); return;
        }
        chain.doFilter(request,response);
    }
}
