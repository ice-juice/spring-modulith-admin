package com.sma.admin.identity.internal;

import java.io.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Semaphore;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;
import com.sma.admin.common.*;
import com.sma.admin.common.security.WebAddresses;
import com.sma.admin.identity.internal.service.*;

/** Context/CORS selection does not authenticate. appClient is a public route selector, always scoped by trusted Host. */
final class ApplicationRequestFilter extends OncePerRequestFilter {
    private static final Set<String> SAFE=Set.of("GET","HEAD","OPTIONS");
    private static final Set<String> METHODS=Set.of("GET","HEAD","POST","PUT","PATCH","DELETE");
    private static final Set<String> HEADERS=Set.of("content-type","x-app-csrf","authorization","accept-language","x-time-zone","idempotency-key","x-upload-size","x-upload-sha256");
    private final ApplicationClientResolver clients;private final ErrorResponseWriter errors;private final IdentityHttpProperties settings;private final Semaphore slots;
    ApplicationRequestFilter(ApplicationClientResolver clients,ErrorResponseWriter errors,IdentityHttpProperties settings){this.clients=clients;this.errors=errors;this.settings=settings;slots=new Semaphore(settings.maximumConcurrent());}
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws IOException,ServletException{
        response.setHeader("Cache-Control","no-store");response.setHeader("Pragma","no-cache");response.setHeader("Referrer-Policy","no-referrer");response.setHeader("X-Content-Type-Options","nosniff");
        if(!slots.tryAcquire()){errors.write(request,response,CommonErrorCode.SERVICE_UNAVAILABLE);return;}
        try{
            HttpServletRequest effective=request;
            try{
                if(!request.isSecure())throw new BizException(CommonErrorCode.FORBIDDEN);
                var client=clients.resolve(handle(request));request.setAttribute(ApplicationHttp.CLIENT,client);
                ApplicationHttp.header(request,ApplicationHttp.CSRF_HEADER);ApplicationHttp.cookie(request,client,"csrf");
                String rawOrigin=ApplicationHttp.header(request,"Origin"),fetchSite=ApplicationHttp.header(request,"Sec-Fetch-Site");
                String origin=rawOrigin==null?null:WebAddresses.origin(rawOrigin).orElseThrow(()->new BizException(CommonErrorCode.FORBIDDEN));
                if(client.web()){
                    if(origin==null){
                        String host=request.getServerName();if(host.contains(":") && !host.startsWith("["))host="["+host+"]";
                        String own=WebAddresses.origin(request.getScheme()+"://"+host+":"+request.getServerPort()).orElse("");
                        if(!SAFE.contains(request.getMethod()) || "cross-site".equalsIgnoreCase(fetchSite) || !client.origins().contains(own))throw new BizException(CommonErrorCode.FORBIDDEN);
                        origin=own;
                    }
                    if(!client.origins().contains(origin))throw new BizException(CommonErrorCode.FORBIDDEN);
                    request.setAttribute(ApplicationHttp.ORIGIN,origin);
                    if(rawOrigin!=null){response.setHeader("Access-Control-Allow-Origin",origin);response.setHeader("Access-Control-Allow-Credentials","true");response.addHeader("Vary","Origin");}
                }else{
                    if(rawOrigin!=null)throw new BizException(CommonErrorCode.FORBIDDEN);
                    for(String kind:List.of("csrf","login-proof","registration-proof","password-reset-proof","reauth-google-proof","link-google-proof","binding-proof"))
                        if(ApplicationHttp.cookie(request,client,kind)!=null)throw new BizException(CommonErrorCode.FORBIDDEN);
                }
                if(request.getMethod().equals("OPTIONS")){
                    if(!client.web() || rawOrigin==null)throw new BizException(CommonErrorCode.FORBIDDEN);
                    String method=ApplicationHttp.header(request,"Access-Control-Request-Method");
                    if(!METHODS.contains(method==null?"":method))throw new BizException(CommonErrorCode.FORBIDDEN);
                    String requested=ApplicationHttp.header(request,"Access-Control-Request-Headers");
                    if(requested!=null){if(requested.length()>1024)throw new BizException(CommonErrorCode.BAD_REQUEST);
                        for(String header:requested.split(","))if(!HEADERS.contains(header.trim().toLowerCase(Locale.ROOT)))throw new BizException(CommonErrorCode.FORBIDDEN);}
                    response.addHeader("Vary","Access-Control-Request-Method");response.addHeader("Vary","Access-Control-Request-Headers");
                    response.setHeader("Access-Control-Allow-Methods",String.join(", ",new TreeSet<>(METHODS)));response.setHeader("Access-Control-Allow-Headers",String.join(", ",new TreeSet<>(HEADERS)));
                    response.setHeader("Access-Control-Max-Age","300");response.setStatus(204);return;
                }
                if(ApplicationHttp.path(request).startsWith("/app/identity/") && !SAFE.contains(request.getMethod())){
                    String type=request.getContentType();
                    if(type==null || !type.split(";",2)[0].trim().equalsIgnoreCase("application/json"))throw new BizException(CommonErrorCode.UNSUPPORTED_MEDIA_TYPE);
                    if(request.getContentLengthLong()>settings.maximumJsonBytes())throw new BizException(CommonErrorCode.PAYLOAD_TOO_LARGE);
                    byte[] body=request.getInputStream().readNBytes(settings.maximumJsonBytes()+1);
                    if(body.length>settings.maximumJsonBytes())throw new BizException(CommonErrorCode.PAYLOAD_TOO_LARGE);
                    effective=new JsonRequest(request,body);
                }
            }catch(BizException rejected){errors.write(request,response,rejected.getErrorCode());return;}
             catch(IllegalArgumentException invalid){errors.write(request,response,CommonErrorCode.BAD_REQUEST);return;}
             catch(org.springframework.security.access.AccessDeniedException denied){errors.write(request,response,CommonErrorCode.FORBIDDEN);return;}
            chain.doFilter(effective,response);
        }finally{slots.release();}
    }
    private String handle(HttpServletRequest request){
        String query=request.getQueryString();if(query==null || query.length()>4096)throw new BizException(CommonErrorCode.BAD_REQUEST);
        String found=null;String[] items=query.split("&",-1);if(items.length>64)throw new BizException(CommonErrorCode.BAD_REQUEST);
        for(String item:items){String[] pair=item.split("=",2);if(URLDecoder.decode(pair[0],StandardCharsets.UTF_8).equals("appClient")){
            if(found!=null || pair.length!=2 || !pair[1].matches("[A-Za-z0-9_-]{43}"))throw new BizException(CommonErrorCode.BAD_REQUEST);found=pair[1];}}
        if(found==null)throw new BizException(CommonErrorCode.BAD_REQUEST);return found;
    }
    private static final class JsonRequest extends HttpServletRequestWrapper {
        private final byte[] body;JsonRequest(HttpServletRequest request,byte[] body){super(request);this.body=body;}
        @Override public int getContentLength(){return body.length;}@Override public long getContentLengthLong(){return body.length;}
        @Override public ServletInputStream getInputStream(){var input=new ByteArrayInputStream(body);return new ServletInputStream(){
            public int read(){return input.read();}public boolean isFinished(){return input.available()==0;}public boolean isReady(){return true;}
            public void setReadListener(ReadListener listener){throw new UnsupportedOperationException("Bounded blocking JSON body");}};}
        @Override public BufferedReader getReader(){return new BufferedReader(new InputStreamReader(getInputStream(),StandardCharsets.UTF_8));}
    }
}
