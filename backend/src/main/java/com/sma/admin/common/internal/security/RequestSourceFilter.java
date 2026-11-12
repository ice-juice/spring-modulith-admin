package com.sma.admin.common.internal.security;

import java.io.IOException;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Before tenant lookup so rejected/unknown Hosts also have network evidence. Never rewrites the request. */
@Component @Order(Ordered.HIGHEST_PRECEDENCE+30)
@EnableConfigurationProperties(RequestSourceProperties.class)
public class RequestSourceFilter extends OncePerRequestFilter implements org.springframework.context.EnvironmentAware {
    private final RequestSourceResolver resolver;
    private final RequestSourceScope scope;
    public RequestSourceFilter(RequestSourceProperties properties,RequestSourceScope scope){this.resolver=new RequestSourceResolver(properties);this.scope=scope;}
    @Override public void setEnvironment(org.springframework.core.env.Environment environment){
        if(!"none".equalsIgnoreCase(environment.getProperty("server.forward-headers-strategy","none")))
            throw new IllegalStateException("Source observations require container forwarding disabled");
    }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{
        try(var binding=scope.open(resolver.resolve(request),request.getMethod())){chain.doFilter(request,response);}
    }
}
