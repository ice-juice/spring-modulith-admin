package com.sma.admin.identity.internal.web;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import com.sma.admin.common.ApiResponse;
import com.sma.admin.common.security.RequiresPermission;
import com.sma.admin.identity.internal.service.IdentityEmailConfigurationService;

@RestController @RequestMapping("/api/identity/clients")
@com.sma.admin.common.request.GovernedApi(group="identity.clients")
public class IdentityEmailConfigurationController implements com.sma.admin.common.request.RequestAuthorizer {
    @Override public void authorizeRequest(String method,java.util.Map<String,String> pathVariables){com.sma.admin.common.security.CurrentUser.require();}
    private final IdentityEmailConfigurationService service;
    public IdentityEmailConfigurationController(IdentityEmailConfigurationService service){this.service=service;}
    public record Update(@Min(0)long expectedVersion,@NotNull @Valid IdentityEmailConfigurationService.Input configuration){}
    @GetMapping("/{id}/email-configurations") @RequiresPermission("identity:client:view")
    public ApiResponse<List<IdentityEmailConfigurationService.View>> list(@PathVariable long id){return ApiResponse.ok(service.list(id));}
    @PutMapping("/{id}/email-configurations/{language}") @RequiresPermission("identity:client:manage")
    public ApiResponse<IdentityEmailConfigurationService.View> put(@PathVariable long id,@PathVariable String language,@Valid @RequestBody Update input){
        return ApiResponse.ok(service.put(id,language,input.expectedVersion(),input.configuration()));
    }
}
