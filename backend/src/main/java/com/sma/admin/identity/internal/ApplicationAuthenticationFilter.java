package com.sma.admin.identity.internal;

import java.io.IOException;
import java.util.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import com.sma.admin.common.*;
import com.sma.admin.common.security.ActorContext;
import com.sma.admin.identity.internal.service.IdentitySessionStore;

final class ApplicationAuthenticationFilter extends OncePerRequestFilter {
    private final IdentitySessionStore sessions;private final ErrorResponseWriter errors;
    ApplicationAuthenticationFilter(IdentitySessionStore sessions,ErrorResponseWriter errors){this.sessions=sessions;this.errors=errors;}
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws IOException,ServletException{
        try{
            var client=ApplicationHttp.client(request);String authorization=ApplicationHttp.header(request,"Authorization");
            String accessCookie=ApplicationHttp.cookie(request,client,"access"),refreshCookie=ApplicationHttp.cookie(request,client,"refresh");
            if(client.web() && authorization!=null || !client.web() && (accessCookie!=null || refreshCookie!=null))throw new BizException(CommonErrorCode.FORBIDDEN);
            if(ApplicationHttp.publicIdentity(request)){
                if(authorization!=null)throw new BizException(CommonErrorCode.BAD_REQUEST);
            }else{
                String raw=client.web()?accessCookie:authorization!=null && authorization.startsWith("Bearer ")?authorization.substring(7):null;
                var access=ActorContext.withinJob(client.authority().tenantId(),"identity-http-authenticate",()->sessions.authenticate(raw,client.handle(),client.web()?"WEB":"NATIVE"));
                if(client.web() && !Set.of("GET","HEAD","OPTIONS").contains(request.getMethod())
                        && !IdentitySessionStore.csrfMatches(ApplicationHttp.header(request,ApplicationHttp.CSRF_HEADER),access.csrfHash()))throw new BizException(CommonErrorCode.FORBIDDEN);
                var context=SecurityContextHolder.createEmptyContext();context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(access.principal(),null,List.of()));
                SecurityContextHolder.setContext(context);request.setAttribute(ApplicationHttp.ACCESS,access);
            }
        }catch(BizException denied){errors.write(request,response,denied.getErrorCode());return;}
         catch(org.springframework.security.access.AccessDeniedException denied){errors.write(request,response,CommonErrorCode.FORBIDDEN);return;}
        chain.doFilter(request,response);
    }
}
