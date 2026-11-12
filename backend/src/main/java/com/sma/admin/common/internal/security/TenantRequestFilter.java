package com.sma.admin.common.internal.security;

import java.io.IOException;
import java.util.Collections;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import com.sma.admin.common.*;
import com.sma.admin.common.security.*;

/** Runs before browser-origin and authentication filters. This filter does not create an identity. */
@Component @Order(Ordered.HIGHEST_PRECEDENCE + 40)
@EnableConfigurationProperties(TenantIngressProperties.class)
public class TenantRequestFilter extends OncePerRequestFilter implements org.springframework.context.EnvironmentAware {
    private final TenantEntryQuery entries;
    private final TenantIngressProperties properties;
    private final ErrorResponseWriter errors;
    public TenantRequestFilter(TenantEntryQuery entries,TenantIngressProperties properties,ErrorResponseWriter errors) {
        this.entries=entries; this.properties=properties; this.errors=errors;
    }
    @Override public void setEnvironment(org.springframework.core.env.Environment environment) {
        String strategy=environment.getProperty("server.forward-headers-strategy","none");
        if(!"none".equalsIgnoreCase(strategy)) throw new IllegalStateException("Tenant ingress requires server.forward-headers-strategy=none; configure trusted proxy addresses explicitly");
    }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        String path=request.getRequestURI().substring(request.getContextPath().length());
        return path.equals("/open") || path.startsWith("/open/") || path.equals("/error") || path.equals("/actuator/info")
                || path.equals("/actuator/health") || path.startsWith("/actuator/health/");
    }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        try(var requestScope=TenantEntryScope.beginRequest()) {
            ExternalRequest external;
            TenantEntry entry;
            try {
                external=external(request);
                entry=entries.resolve(external.host).orElse(null);
            } catch(IllegalArgumentException invalid) { reject(request,response,CommonErrorCode.BAD_REQUEST); return; }
              catch(DataAccessException unavailable) { reject(request,response,CommonErrorCode.SERVICE_UNAVAILABLE); return; }
            if(entry==null) { reject(request,response,CommonErrorCode.NOT_FOUND); return; }
            TenantEntryScope.Scope scope;
            try { scope=TenantEntryScope.open(entry); }
            catch(AccessDeniedException mismatch) { reject(request,response,CommonErrorCode.FORBIDDEN); return; }
            try(scope) { chain.doFilter(external,response); }
        }
    }
    private void reject(HttpServletRequest request,HttpServletResponse response,ErrorCode error) throws IOException {
        response.setHeader("Cache-Control","no-store");
        if(error==CommonErrorCode.BAD_REQUEST||error==CommonErrorCode.NOT_FOUND||error==CommonErrorCode.FORBIDDEN)
            errors.writeObserved(request,response,error,SecuritySignal.Fact.TENANT_ENTRY_REJECTED);
        else errors.write(request,response,error);
    }
    private ExternalRequest external(HttpServletRequest request) {
        String host=single(request,"Host"), scheme=request.getScheme();
        boolean rawHost=host!=null;
        if(host==null) host=request.getServerName().contains(":")&&!request.getServerName().startsWith("[")?"["+request.getServerName()+"]":request.getServerName();
        if(properties.trusts(request.getRemoteAddr())) {
            String forwarded=single(request,"X-Forwarded-Host");
            if(forwarded!=null) {
                host=forwarded; scheme=single(request,"X-Forwarded-Proto"); rawHost=true;
            }
        }
        if(!"https".equals(scheme) && !"http".equals(scheme)) throw new IllegalArgumentException("Invalid external scheme");
        String normalized=TenantHost.authority(host);
        int port=rawHost?(scheme.equals("https")?443:80):request.getServerPort();
        int colon=host.startsWith("[")?host.indexOf(':',host.indexOf(']')):host.indexOf(':');
        if(colon>=0) port=Integer.parseInt(host.substring(colon+1)); // authority already validated it.
        return new ExternalRequest(request,normalized,scheme,port);
    }
    private static String single(HttpServletRequest request,String name) {
        var values=Collections.list(request.getHeaders(name));
        if(values.size()>1) throw new IllegalArgumentException("Multiple host headers");
        return values.isEmpty()?null:values.getFirst();
    }
    private static final class ExternalRequest extends HttpServletRequestWrapper {
        private final String host,scheme;
        private final int port;
        private ExternalRequest(HttpServletRequest request,String host,String scheme,int port) { super(request);this.host=host;this.scheme=scheme;this.port=port; }
        @Override public String getServerName() { return host.startsWith("[")?host.substring(1,host.length()-1):host; }
        @Override public String getScheme() { return scheme; }
        @Override public int getServerPort() { return port; }
        @Override public boolean isSecure() { return scheme.equals("https"); }
        @Override public StringBuffer getRequestURL() { return new StringBuffer(scheme+"://"+host+":"+port+getRequestURI()); }
    }
}
