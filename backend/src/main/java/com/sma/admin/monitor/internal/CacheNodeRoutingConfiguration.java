package com.sma.admin.monitor.internal;
import java.util.Collections;
import jakarta.servlet.http.*;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.*;
import com.sma.admin.common.cache.*;
import com.sma.admin.common.security.CurrentUser;

/** LB routes only configured IDs; the receiving host independently refuses wrong node/process requests. */
@Configuration(proxyBeanMethods=false)
public class CacheNodeRoutingConfiguration implements WebMvcConfigurer {
    private final CacheNodeSelection selection;
    public CacheNodeRoutingConfiguration(CacheNodeSelection selection){this.selection=selection;}
    @Override public void addInterceptors(InterceptorRegistry registry){
        registry.addInterceptor(interceptor()).addPathPatterns("/api/monitor/caches","/api/monitor/caches/**");
    }
    HandlerInterceptor interceptor(){return new HandlerInterceptor(){
        @Override public boolean preHandle(HttpServletRequest request,HttpServletResponse response,Object handler){
            var nodes=Collections.list(request.getHeaders(CacheNodeSelection.NODE_HEADER));
            var processes=Collections.list(request.getHeaders(CacheNodeSelection.PROCESS_HEADER));
            boolean targeted=!nodes.isEmpty()||!processes.isEmpty();
            if(!targeted && CurrentUser.optional().filter(user->!user.machine()&&user.permissions().contains("monitor:cache:view")).isEmpty())return true;
            // Authenticate before validating or exposing process metadata. Action/object permissions still run normally.
            var local=selection.select(null,null);
            if(nodes.size()>1||processes.size()>1)throw new CacheNodeSelectionRejected(CacheNodeSelectionRejected.Reason.INVALID);
            if(targeted)local=selection.select(nodes.isEmpty()?null:nodes.getFirst(),processes.isEmpty()?null:processes.getFirst());
            response.setHeader(CacheNodeSelection.NODE_HEADER,local.nodeId());response.setHeader(CacheNodeSelection.PROCESS_HEADER,local.processId());
            response.setHeader("Cache-Control","no-store");return true;
        }
    };}
}
