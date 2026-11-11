package com.sma.admin.extension.internal.web;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.sma.admin.common.ApiResponse;
import com.sma.admin.common.BizException;
import com.sma.admin.common.CommonErrorCode;
import com.sma.admin.common.security.PermitLoggedIn;
import com.sma.admin.common.security.RequiresPermission;
import com.sma.admin.extension.BusinessExtensions;
import com.sma.admin.extension.ExtensionWork;
import com.sma.admin.extension.internal.service.ExtensionRuntimeService;

@RestController
@RequestMapping("/api/extension")
@com.sma.admin.common.request.GovernedApi(group="extension.runtime",ownResource=false,mode=com.sma.admin.common.request.GovernedApi.Mode.NATIVE,reason="Module runtime permissions and native work/reconciliation protocols; capabilities retain module-owned effects")
public class ExtensionController implements com.sma.admin.common.request.RequestAuthorizer {
    @Override public void authorizeRequest(String method,java.util.Map<String,String> variables){com.sma.admin.common.security.CurrentUser.require();}
    private final ExtensionRuntimeService runtime;
    private final BusinessExtensions extensions;
    private final com.sma.admin.extension.internal.service.ExtensionReconciliationService reconciliation;
    public ExtensionController(ExtensionRuntimeService runtime,BusinessExtensions extensions,com.sma.admin.extension.internal.service.ExtensionReconciliationService reconciliation) { this.runtime=runtime;this.extensions=extensions;this.reconciliation=reconciliation; }
    @PostMapping("/work/{id}/reconcile") @RequiresPermission("extension:work:reconcile")
    public ApiResponse<ExtensionWork> reconcile(@PathVariable long id){return ApiResponse.ok(reconciliation.reconcile(id));}
    @GetMapping("/runtime") @RequiresPermission("extension:runtime:read")
    public ApiResponse<ExtensionRuntimeService.Status> status() { return ApiResponse.ok(runtime.status()); }
    @GetMapping("/metrics") @RequiresPermission("extension:runtime:read")
    public ApiResponse<com.sma.admin.extension.internal.service.ExtensionTelemetry.Snapshot> metrics(){return ApiResponse.ok(runtime.metrics());}
    @PostMapping(value="/work/{deployment}/{plugin}/{entry}",consumes="application/json") @PermitLoggedIn
    public ApiResponse<ExtensionWork> invokeWork(@PathVariable String deployment,@PathVariable String plugin,@PathVariable String entry,
            @RequestHeader("Idempotency-Key") String key,HttpServletRequest request)throws IOException {
        byte[] body=request.getInputStream().readNBytes(1_048_577);if(body.length>1_048_576)throw new BizException(CommonErrorCode.PAYLOAD_TOO_LARGE);
        return ApiResponse.ok(extensions.invokeWork(deployment,plugin,entry,key,body));
    }
    @GetMapping("/work/{id}") @PermitLoggedIn
    public ApiResponse<ExtensionWork> work(@PathVariable long id){return ApiResponse.ok(extensions.work(id));}
    @PostMapping("/invoke/{deployment}/{plugin}/{entry}") @PermitLoggedIn
    public ResponseEntity<byte[]> invoke(@PathVariable String deployment,@PathVariable String plugin,@PathVariable String entry,HttpServletRequest request) throws IOException {
        byte[] body=request.getInputStream().readNBytes(1_048_577);
        if(body.length>1_048_576) throw new BizException(CommonErrorCode.PAYLOAD_TOO_LARGE);
        // Entry permission and tenant are rechecked by the runtime; this annotation is only the authentication floor.
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(extensions.invoke(deployment,plugin,entry,body));
    }
}
