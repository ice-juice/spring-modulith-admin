package com.sma.admin.identity.internal;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import jakarta.servlet.http.*;
import org.springframework.http.ResponseCookie;
import org.springframework.web.util.UriUtils;
import com.sma.admin.common.*;
import com.sma.admin.identity.internal.service.ApplicationClientResolver;

public final class ApplicationHttp {
    public static final String CLIENT="sma.identity.client",ORIGIN="sma.identity.origin",ACCESS="sma.identity.access",CSRF_HEADER="X-App-CSRF";
    private ApplicationHttp(){}
    public static ApplicationClientResolver.Client client(HttpServletRequest request){
        var value=request.getAttribute(CLIENT);if(!(value instanceof ApplicationClientResolver.Client client))throw new BizException(CommonErrorCode.UNAUTHORIZED);return client;
    }
    public static String path(HttpServletRequest request){return UriUtils.decode(request.getRequestURI().substring(request.getContextPath().length()),StandardCharsets.UTF_8);}
    public static boolean publicIdentity(HttpServletRequest request){
        String path=path(request),method=request.getMethod();
        return (method.equals("GET") || method.equals("HEAD")) && path.equals("/app/identity/bootstrap")
                || method.equals("POST") && Set.of("/app/identity/google/transactions","/app/identity/google/completions","/app/identity/sessions/refresh",
                        "/app/identity/email/challenges","/app/identity/email/verifications","/app/identity/registrations","/app/identity/password/resets","/app/identity/password/sessions").contains(path);
    }
    public static String header(HttpServletRequest request,String name){
        var values=Collections.list(request.getHeaders(name));if(values.size()>1)throw new BizException(CommonErrorCode.BAD_REQUEST);return values.isEmpty()?null:values.getFirst();
    }
    public static String name(ApplicationClientResolver.Client client,String kind){return "__Host-sma-"+kind+"-"+client.handle();}
    public static String cookie(HttpServletRequest request,ApplicationClientResolver.Client client,String kind){
        String value=null;int count=0;var cookies=request.getCookies();
        if(cookies!=null)for(var cookie:cookies)if(cookie.getName().equals(name(client,kind))){value=cookie.getValue();count++;}
        if(count>1)throw new BizException(CommonErrorCode.BAD_REQUEST);return value;
    }
    public static void cookie(HttpServletResponse response,ApplicationClientResolver.Client client,String kind,String value,boolean httpOnly,Duration maxAge){
        response.addHeader("Set-Cookie",ResponseCookie.from(name(client,kind),value==null?"":value).path("/").secure(true).httpOnly(httpOnly)
                .sameSite(client.sameSite()).maxAge(maxAge.isNegative()?Duration.ZERO:maxAge).build().toString());
    }
    public static void clear(HttpServletResponse response,ApplicationClientResolver.Client client,String kind){cookie(response,client,kind,"",!kind.equals("csrf"),Duration.ZERO);}
}
