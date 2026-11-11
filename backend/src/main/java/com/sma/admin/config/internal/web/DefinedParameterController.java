package com.sma.admin.config.internal.web;

import java.util.Map;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import com.sma.admin.common.*;
import com.sma.admin.common.request.*;
import com.sma.admin.common.security.*;
import com.sma.admin.config.ConfigurationRevision;
import com.sma.admin.config.internal.service.DefinedParameterManagement;
import com.sma.admin.config.internal.service.DefinedParameterManagement.View;

@RestController @RequestMapping("/api/config/effective-parameters")
@GovernedApi(group="config.parameters")
public class DefinedParameterController implements RequestAuthorizer {
    private final DefinedParameterManagement management;
    public DefinedParameterController(DefinedParameterManagement management){this.management=management;}
    public record OverrideInput(@NotNull @Min(0) Long expectedPlatformRevision,@NotNull @Min(0) Long expectedTenantRevision,
            @JsonProperty(required=true) @Size(max=4000) String value) {
        @Override public String toString(){return "ParameterOverrideInput[redacted]";}
    }
    public record InheritInput(@NotNull @Min(0) Long expectedPlatformRevision,@NotNull @Min(0) Long expectedTenantRevision) {}
    @Override public void authorizeRequest(String method,Map<String,String> variables) {
        management.require(switch(method){case "override","inherit"->"config:parameter:update";default->"config:parameter:view";});
    }
    @GetMapping @RequiresPermission("config:parameter:view")
    public ApiResponse<PageResult<View>> page(@ModelAttribute PageQuery page,@RequestParam(required=false) String group,@RequestParam(required=false) String keyword) {
        return ApiResponse.ok(management.page(page,group,keyword));
    }
    @GetMapping("/{key}") @RequiresPermission("config:parameter:view")
    public ApiResponse<View> get(@PathVariable String key){return ApiResponse.ok(management.get(key));}
    @PutMapping("/{key}/override") @RequiresPermission("config:parameter:update") @AuditAction("config:parameter:update")
    public ApiResponse<View> override(@PathVariable String key,@Valid @RequestBody OverrideInput input) {
        return ApiResponse.ok(management.override(key,new ConfigurationRevision(input.expectedPlatformRevision(),input.expectedTenantRevision()),input.value()));
    }
    @PostMapping("/{key}/inherit") @RequiresPermission("config:parameter:update") @AuditAction("config:parameter:inherit")
    public ApiResponse<View> inherit(@PathVariable String key,@Valid @RequestBody InheritInput input) {
        return ApiResponse.ok(management.inherit(key,new ConfigurationRevision(input.expectedPlatformRevision(),input.expectedTenantRevision())));
    }
}
