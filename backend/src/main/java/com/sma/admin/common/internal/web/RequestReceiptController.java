package com.sma.admin.common.internal.web;

import java.time.Duration;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.*;
import com.sma.admin.common.ApiResponse;
import com.sma.admin.common.request.RequestExecution;
import com.sma.admin.common.request.RequestReceipt;
import com.sma.admin.common.security.PermitLoggedIn;

@RestController @RequestMapping("/api/common/requests")
@com.sma.admin.common.request.GovernedApi(group="common.requests",ownResource=true,mode=com.sma.admin.common.request.GovernedApi.Mode.QUERY,reason="Owned receipt lookup with an additional combined 120/min budget; never executes the original operation")
public class RequestReceiptController implements com.sma.admin.common.request.RequestAuthorizer {
    @Override public void authorizeRequest(String method,java.util.Map<String,String> variables){com.sma.admin.common.security.CurrentUser.require();}
    private final RequestExecution execution;
    public RequestReceiptController(RequestExecution execution) { this.execution = execution; }
    @GetMapping("/{id}") @PermitLoggedIn
    public ApiResponse<RequestReceipt> get(@PathVariable String id) {
        execution.claimBudget("common.receipts", 120, Duration.ofMinutes(1)); return ApiResponse.ok(execution.receipt(id));
    }
    public record Lookup(@NotNull @Pattern(regexp="[A-Za-z0-9_.:-]{16,128}") String key) {
        @Override public String toString() { return "Lookup[REDACTED]"; }
    }
    /** Read-only lookup uses a body so an operation key never enters URL/access logs. */
    @PostMapping("/lookup") @PermitLoggedIn
    public ApiResponse<RequestReceipt> lookup(@Valid @RequestBody Lookup input) {
        execution.claimBudget("common.receipts", 120, Duration.ofMinutes(1)); return ApiResponse.ok(execution.receiptByKey(input.key()));
    }
}
