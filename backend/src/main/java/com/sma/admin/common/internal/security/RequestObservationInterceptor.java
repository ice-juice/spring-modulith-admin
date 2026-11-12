package com.sma.admin.common.internal.security;

import jakarta.servlet.http.*;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.resource.ResourceHttpRequestHandler;

/** No URI, query or body access. Only a selected handler's declared template may be retained. */
@Component
public class RequestObservationInterceptor implements HandlerInterceptor {
    private final RequestSourceScope scope;
    public RequestObservationInterceptor(RequestSourceScope scope){this.scope=scope;}
    @Override public boolean preHandle(HttpServletRequest request,HttpServletResponse response,Object handler){
        scope.handler(handler instanceof ResourceHttpRequestHandler);
        if(!(handler instanceof HandlerMethod method))return true;
        Object selected=request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if(!(selected instanceof String pattern)||pattern.length()>256)return true;
        var base=AnnotatedElementUtils.findMergedAnnotation(method.getBeanType(),RequestMapping.class);
        var endpoint=AnnotatedElementUtils.findMergedAnnotation(method.getMethod(),RequestMapping.class);
        if(endpoint==null)return true;
        var prefixes=paths(base);var suffixes=paths(endpoint);if(prefixes.length>16||suffixes.length>16)return true;
        for(String prefix:prefixes)for(String suffix:suffixes){
            if(prefix.length()>256||suffix.length()>256)continue;
            String combined=prefix.endsWith("/")&&suffix.startsWith("/")?prefix+suffix.substring(1):prefix+suffix;
            if(!combined.startsWith("/"))combined="/"+combined;
            if(combined.equals(pattern)){
                try{scope.matched(pattern);}catch(IllegalArgumentException unsuitable){/* Keep UNRESOLVED; do not substitute raw request data. */}
                return true;
            }
        }
        return true;
    }
    private static String[] paths(RequestMapping mapping){return mapping==null||mapping.path().length==0?new String[]{""}:mapping.path();}
}
