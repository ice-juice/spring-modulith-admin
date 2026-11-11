package com.sma.admin.file.internal.web;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.Semaphore;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import com.sma.admin.common.*;
import com.sma.admin.common.security.ActorContext;
import com.sma.admin.common.security.ActorRef;
import com.sma.admin.file.internal.service.UploadTaskProperties;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Runs after the security chain, before MVC. Blocking-only bounded JSON and binary bodies; no multipart parsing. */
@Component @Order(-90)
public class UploadTaskRequestFilter extends OncePerRequestFilter {
    private static final int JSON_LIMIT=65536;
    private final ErrorResponseWriter errors;
    private final UploadTaskProperties settings;
    private final Semaphore slots=new Semaphore(16);
    public UploadTaskRequestFilter(ErrorResponseWriter errors,UploadTaskProperties settings){this.errors=errors;this.settings=settings;}
    @Override protected boolean shouldNotFilter(HttpServletRequest request){
        String path=path(request);
        return !(path.equals("/api/file/upload-tasks") || path.startsWith("/api/file/upload-tasks/")
                || path.equals("/app/file/upload-tasks") || path.startsWith("/app/file/upload-tasks/"));
    }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws IOException,ServletException{
        response.setHeader("Cache-Control","no-store");response.setHeader("X-Content-Type-Options","nosniff");
        try{
            var actor=ActorContext.require();boolean app=path(request).startsWith("/app/");
            if(actor.kind()!=(app?ActorRef.Kind.APP_USER:ActorRef.Kind.ADMIN_USER))throw new BizException(CommonErrorCode.FORBIDDEN);
        }catch(org.springframework.security.access.AccessDeniedException denied){errors.write(request,response,CommonErrorCode.UNAUTHORIZED);return;}
         catch(BizException denied){errors.write(request,response,denied.getErrorCode());return;}
        if(!slots.tryAcquire()){errors.write(request,response,CommonErrorCode.SERVICE_UNAVAILABLE);return;}
        try{
            HttpServletRequest effective=request;
            try{
                if(!java.util.Set.of("GET","HEAD","OPTIONS").contains(request.getMethod())){
                    if(single(request,"Content-Encoding")!=null)throw new BizException(CommonErrorCode.UNSUPPORTED_MEDIA_TYPE);
                    String type=single(request,"Content-Type");if(type==null)throw new BizException(CommonErrorCode.UNSUPPORTED_MEDIA_TYPE);
                    single(request,"Idempotency-Key");type=type.split(";",2)[0].trim();
                    if(request.getMethod().equals("PUT") && path(request).matches("/(api|app)/file/upload-tasks/[^/]+/parts/[^/]+")){
                        if(!type.equalsIgnoreCase("application/octet-stream"))throw new BizException(CommonErrorCode.UNSUPPORTED_MEDIA_TYPE);
                        String declared=single(request,"X-Upload-Size"),checksum=single(request,"X-Upload-SHA256");
                        if(declared==null || !declared.matches("[1-9][0-9]{0,8}") || checksum==null || !checksum.matches("[0-9a-f]{64}"))throw new BizException(CommonErrorCode.BAD_REQUEST);
                        long size=Long.parseLong(declared);
                        if(size>settings.partBytes())throw new BizException(CommonErrorCode.PAYLOAD_TOO_LARGE);
                        if(request.getContentLengthLong()>=0 && request.getContentLengthLong()!=size)throw new BizException(CommonErrorCode.BAD_REQUEST);
                        effective=new BinaryRequest(request,size);
                    }else{
                        if(!type.equalsIgnoreCase("application/json"))throw new BizException(CommonErrorCode.UNSUPPORTED_MEDIA_TYPE);
                        if(request.getContentLengthLong()>JSON_LIMIT)throw new BizException(CommonErrorCode.PAYLOAD_TOO_LARGE);
                        byte[] body=request.getInputStream().readNBytes(JSON_LIMIT+1);
                        if(body.length>JSON_LIMIT)throw new BizException(CommonErrorCode.PAYLOAD_TOO_LARGE);
                        effective=new JsonRequest(request,body);
                    }
                }
            }catch(BizException rejected){errors.write(request,response,rejected.getErrorCode());return;}
            chain.doFilter(effective,response);
        }finally{slots.release();}
    }
    private String path(HttpServletRequest request){return request.getRequestURI().substring(request.getContextPath().length());}
    private String single(HttpServletRequest request,String name){
        var values=Collections.list(request.getHeaders(name));
        if(values.size()>1 || !values.isEmpty() && values.getFirst().length()>256)throw new BizException(CommonErrorCode.BAD_REQUEST);
        return values.isEmpty()?null:values.getFirst();
    }
    private static ServletInputStream blocking(InputStream source,long limit){return new ServletInputStream(){
        long count;boolean finished;
        private int measured(int read){if(read<0){finished=true;return read;}count+=read;if(count>limit)throw new BizException(CommonErrorCode.PAYLOAD_TOO_LARGE);return read;}
        @Override public int read()throws IOException{int value=source.read();measured(value<0?-1:1);return value;}
        @Override public int read(byte[] bytes,int offset,int length)throws IOException{
            if(length==0)return 0;return measured(source.read(bytes,offset,(int)Math.min(length,limit-count+1)));
        }
        @Override public void close()throws IOException{source.close();}
        @Override public boolean isFinished(){return finished;}
        @Override public boolean isReady(){return true;}
        @Override public void setReadListener(ReadListener listener){throw new UnsupportedOperationException("Synchronous upload only");}
    };}
    private static final class BinaryRequest extends HttpServletRequestWrapper {
        private final ServletInputStream input;
        BinaryRequest(HttpServletRequest request,long size)throws IOException{super(request);input=blocking(request.getInputStream(),size);}
        @Override public ServletInputStream getInputStream(){return input;}
        @Override public BufferedReader getReader(){throw new UnsupportedOperationException("Binary upload requires input stream");}
    }
    private static final class JsonRequest extends HttpServletRequestWrapper {
        private final byte[] body;
        JsonRequest(HttpServletRequest request,byte[] body){super(request);this.body=body;}
        @Override public int getContentLength(){return body.length;}
        @Override public long getContentLengthLong(){return body.length;}
        @Override public ServletInputStream getInputStream(){return blocking(new ByteArrayInputStream(body),body.length);}
        @Override public BufferedReader getReader(){return new BufferedReader(new InputStreamReader(getInputStream(),StandardCharsets.UTF_8));}
    }
}
