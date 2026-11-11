package com.sma.admin.common.internal.security;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.Semaphore;
import java.util.Locale;
import java.util.Set;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ReadListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import com.sma.admin.common.CommonErrorCode;
import com.sma.admin.common.ErrorResponseWriter;
import com.sma.admin.common.security.WebAddresses;
import com.sma.admin.common.security.ActorContext;
import com.sma.admin.common.request.RequestRejected;

/** Browser defense in depth. Machine /open requests retain their separate signed protocol. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 50)
@EnableConfigurationProperties(BrowserRequestProperties.class)
public class BrowserRequestFilter extends OncePerRequestFilter {
    private static final Set<String> SAFE = Set.of("GET", "HEAD", "OPTIONS");
    private final BrowserRequestProperties properties;
    private final ErrorResponseWriter errors;
    private final Semaphore jsonSlots;
    private final Semaphore anonymousJsonSlots;
    public BrowserRequestFilter(BrowserRequestProperties properties, ErrorResponseWriter errors) {
        this.properties = properties; this.errors = errors;this.jsonSlots=new Semaphore(properties.maximumConcurrentJson());
        this.anonymousJsonSlots=new Semaphore(properties.maximumConcurrentAnonymousJson());
    }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().substring(request.getContextPath().length()).startsWith("/api/");
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
        if (!SAFE.contains(request.getMethod())) {
            String origin = request.getHeader("Origin");
            boolean knownOrigin = false;
            if (origin != null) {
                var parsed = WebAddresses.origin(origin);
                String host = request.getServerName();
                if (host.contains(":") && !host.startsWith("[")) host = "[" + host + "]";
                String current = WebAddresses.origin(request.getScheme() + "://" + host + ":" + request.getServerPort()).orElse("");
                knownOrigin = parsed.isPresent() && (parsed.get().equals(current) || properties.allowedOrigins().contains(parsed.get()));
                if (!knownOrigin) { errors.write(request, response, CommonErrorCode.FORBIDDEN); return; }
            }
            if ("cross-site".equalsIgnoreCase(request.getHeader("Sec-Fetch-Site")) && !knownOrigin) {
                errors.write(request, response, CommonErrorCode.FORBIDDEN); return;
            }
            String type = request.getContentType();
            if (type != null) {
                String media = type.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
                if (media.equals("text/plain") || media.equals("application/x-www-form-urlencoded")) {
                    errors.write(request, response, CommonErrorCode.UNSUPPORTED_MEDIA_TYPE); return;
                }
                if(media.equals("application/json")||media.endsWith("+json")){
                    json(request,response,chain);return;
                }
            }
        }
        chain.doFilter(request, response);
    }
    /** Buffer only when the downstream consumer reads, after authentication has had a chance to reject.
     * Bound raw bytes before Jackson allocation and retain exact bytes for native proxy protocols. */
    private void json(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws IOException,ServletException{
        if(request.getContentLengthLong()>properties.maximumJsonBytes()){errors.write(request,response,RequestRejected.Reason.TOO_LARGE);return;}
        var bounded=new JsonRequest(request);
        try{chain.doFilter(bounded,response);}
        catch(BodyRejected rejected){
            if(response.isCommitted())throw rejected;
            if(rejected.retryAfterSeconds()>0)response.setHeader("Retry-After",Long.toString(rejected.retryAfterSeconds()));
            errors.write(request,response,rejected.reason());
        }finally{bounded.close();}
    }
    /** Carries only a fixed resource rejection; never body data. */
    public static final class BodyRejected extends RequestRejected {
        BodyRejected(Reason reason,long retry){super(reason,retry);}
    }
    private final class JsonRequest extends HttpServletRequestWrapper {
        private byte[] bytes;private Semaphore permit;private BodyRejected rejected;private IOException readFailure;private RuntimeException readRuntimeFailure;private boolean closed;
        JsonRequest(HttpServletRequest request){super(request);}
        private synchronized byte[] body()throws IOException{
            if(closed)throw new IOException("JSON request scope ended");
            if(rejected!=null)throw rejected;if(readFailure!=null)throw readFailure;if(readRuntimeFailure!=null)throw readRuntimeFailure;
            if(bytes==null){
                // Header presence is not identity. This is evaluated lazily at the actual body read.
                var authentication=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
                var selected=ActorContext.isAdminUser(authentication)?jsonSlots:anonymousJsonSlots;
                if(!selected.tryAcquire()){rejected=new BodyRejected(RequestRejected.Reason.UNAVAILABLE,1);throw rejected;}
                permit=selected;
                try{bytes=super.getInputStream().readNBytes(properties.maximumJsonBytes()+1);}
                catch(IOException failure){readFailure=failure;throw failure;}
                catch(RuntimeException failure){readRuntimeFailure=failure;throw failure;}
                if(bytes.length>properties.maximumJsonBytes()){rejected=new BodyRejected(RequestRejected.Reason.TOO_LARGE,0);throw rejected;}
            }
            return bytes;
        }
        synchronized void close(){closed=true;if(bytes!=null)Arrays.fill(bytes,(byte)0);if(permit!=null){permit.release();permit=null;}}
        @Override public int getContentLength(){return bytes==null?super.getContentLength():bytes.length;}
        @Override public long getContentLengthLong(){return bytes==null?super.getContentLengthLong():bytes.length;}
        @Override public ServletInputStream getInputStream()throws IOException{
            var stream=new ByteArrayInputStream(body());
            return new ServletInputStream(){
                @Override public int read(){return stream.read();}
                @Override public int read(byte[] target,int offset,int length){return stream.read(target,offset,length);}
                @Override public boolean isFinished(){return stream.available()==0;}
                @Override public boolean isReady(){return true;}
                @Override public void setReadListener(ReadListener listener){throw new UnsupportedOperationException("Bounded blocking API JSON");}
            };
        }
        @Override public BufferedReader getReader()throws IOException{return new BufferedReader(new InputStreamReader(getInputStream(),StandardCharsets.UTF_8));}
    }
}
