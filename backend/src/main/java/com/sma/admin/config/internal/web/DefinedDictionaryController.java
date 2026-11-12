package com.sma.admin.config.internal.web;

import java.util.Map;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import com.sma.admin.common.*;
import com.sma.admin.common.request.*;
import com.sma.admin.common.security.*;
import com.sma.admin.config.ConfigurationLabel;
import com.sma.admin.config.ConfigurationRevision;
import com.sma.admin.config.internal.service.DefinedDictionaryManagement;
import com.sma.admin.config.internal.service.DefinedDictionaryManagement.*;
import com.sma.admin.config.internal.service.DefinedDictionaryService.*;

/** Exact dictionary codes travel in JSON bodies, never in URL paths where encoding could change the stable code. */
@RestController @RequestMapping("/api/config/effective-dictionaries")
@GovernedApi(group="config.dictionaries")
public class DefinedDictionaryController implements RequestAuthorizer {
    private final DefinedDictionaryManagement management;
    public DefinedDictionaryController(DefinedDictionaryManagement management){this.management=management;}
    public record Versions(@NotNull @Min(0) Long expectedPlatformRevision,@NotNull @Min(0) Long expectedTenantRevision) {
        ConfigurationRevision revision(){return new ConfigurationRevision(expectedPlatformRevision,expectedTenantRevision);}
    }
    public record OverrideInput(@NotNull @Valid Versions versions,@NotBlank @Size(max=128) String code,ConfigurationLabel label,@Min(0) Integer sort,Boolean hidden) {}
    public record RestoreInput(@NotNull @Valid Versions versions,@NotBlank @Size(max=128) String code) {}
    public record PrivateInput(@NotNull @Valid Versions versions,@NotBlank @Size(max=128) String code,@Size(max=128) String parentCode,
            @NotNull ConfigurationLabel label,@NotNull @Min(0) Integer sort,@NotNull Boolean enabled) {}
    public record EnabledInput(@NotNull @Valid Versions versions,@NotNull Boolean enabled) {}
    @Override public void authorizeRequest(String method,Map<String,String> variables){
        management.require(switch(method){case "overrideItem","restoreItem","savePrivate","enabled"->"config:dictionary:update";default->"config:dictionary:view";});
    }
    @GetMapping @RequiresPermission("config:dictionary:view")
    public ApiResponse<PageResult<Summary>> page(@ModelAttribute PageQuery page,@RequestParam(required=false) String keyword){return ApiResponse.ok(management.page(page,keyword));}
    @GetMapping("/{key}") @RequiresPermission("config:dictionary:view")
    public ApiResponse<View> get(@PathVariable String key){return ApiResponse.ok(management.get(key));}
    @PutMapping("/{key}/item-overrides") @RequiresPermission("config:dictionary:update") @AuditAction("config:dictionary:update")
    public ApiResponse<View> overrideItem(@PathVariable String key,@Valid @RequestBody OverrideInput input){
        return ApiResponse.ok(management.overrideItem(key,input.versions().revision(),input.code(),new ItemChange(input.label(),input.sort(),input.hidden())));
    }
    @PostMapping("/{key}/item-overrides/restore") @RequiresPermission("config:dictionary:update") @AuditAction("config:dictionary:inherit")
    public ApiResponse<View> restoreItem(@PathVariable String key,@Valid @RequestBody RestoreInput input){
        return ApiResponse.ok(management.restoreItem(key,input.versions().revision(),input.code()));
    }
    @PutMapping("/{key}/private-items") @RequiresPermission("config:dictionary:update") @AuditAction("config:dictionary:update")
    public ApiResponse<View> savePrivate(@PathVariable String key,@Valid @RequestBody PrivateInput input){
        return ApiResponse.ok(management.savePrivate(key,input.versions().revision(),new PrivateItem(input.code(),input.parentCode(),input.label(),input.sort(),input.enabled())));
    }
    @PutMapping("/{key}/enabled") @RequiresPermission("config:dictionary:update") @AuditAction("config:dictionary:update")
    public ApiResponse<View> enabled(@PathVariable String key,@Valid @RequestBody EnabledInput input){
        return ApiResponse.ok(management.enabled(key,input.versions().revision(),input.enabled()));
    }
}
