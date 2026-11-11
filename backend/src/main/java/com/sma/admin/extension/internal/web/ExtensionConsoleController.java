package com.sma.admin.extension.internal.web;

import java.io.IOException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import com.sma.admin.common.BizException;
import com.sma.admin.common.security.RequiresPermission;
import com.sma.admin.extension.ExtensionErrorCode;
import com.sma.admin.extension.internal.service.ConsoleProxyService;

@RestController @RequestMapping("/api/extension/consoles")
@com.sma.admin.common.request.GovernedApi(group="extension.console",ownResource=true,mode=com.sma.admin.common.request.GovernedApi.Mode.NATIVE,
        writeLimit=600,reason="Allowlisted proxy routes; release grants, chunk identities and remote work receipts retain their protocols")
public class ExtensionConsoleController implements com.sma.admin.common.request.RequestAuthorizer {
    @Override public void authorizeRequest(String method,java.util.Map<String,String> variables){com.sma.admin.common.security.CurrentUser.require();}
    @Override public boolean readOnlyRequest(String method,Object[] arguments){return "GET".equals(protocolRequest(arguments).getHeader("X-Release-Method"));}
    @Override public java.util.Map<String,String> requestResources(String method,Object[] arguments){
        var request=protocolRequest(arguments);
        String path=request.getHeader("X-Release-Path").split("\\?",2)[0];
        if("PUT".equals(request.getHeader("X-Release-Method"))) {
            String[] segments=path.split("/");
            path="/uploads/"+segments[2]+"/files/"+Integer.parseInt(segments[4])+"/chunks/"+Integer.parseInt(segments[6]);
        }
        String scope=request.getHeader("X-Release-Method")+"\n"+path;
        return java.util.Map.of("proxy-route",com.sma.extension.protocol.Hashes.sha256(scope.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
    private HttpServletRequest protocolRequest(Object[] arguments){
        if(arguments.length!=1 || !(arguments[0] instanceof HttpServletRequest request))throw new IllegalStateException("Missing console protocol request");
        if(!ConsoleProxyService.allowedRoute(request.getHeader("X-Release-Method"),request.getHeader("X-Release-Path")))throw new BizException(ExtensionErrorCode.CONSOLE_PATH_DENIED);
        return request;
    }
    private final ConsoleProxyService proxy;
    public ExtensionConsoleController(ConsoleProxyService proxy){this.proxy=proxy;}
    @PostMapping(value="/requests",consumes={"application/json","application/octet-stream"}) @RequiresPermission("extension:console:view")
    public ResponseEntity<byte[]> request(HttpServletRequest request)throws IOException {
        byte[] bytes=request.getInputStream().readNBytes(ConsoleProxyService.MAX_BYTES+1);
        if(bytes.length>ConsoleProxyService.MAX_BYTES)throw new BizException(ExtensionErrorCode.CONSOLE_PATH_DENIED);
        var input=new ConsoleProxyService.Request(request.getHeader("X-Release-Method"),request.getHeader("X-Release-Path"),bytes.length==0?null:bytes);
        var reply=proxy.forward(input,request.getHeader("Authorization"),request.getHeader("Accept-Language"));
        return ResponseEntity.status(reply.status()).contentType(MediaType.APPLICATION_JSON).cacheControl(CacheControl.noStore()).body(reply.bytes());
    }
}
