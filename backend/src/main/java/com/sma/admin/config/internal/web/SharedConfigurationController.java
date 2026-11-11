package com.sma.admin.config.internal.web;

import java.util.Map;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import com.sma.admin.common.*;
import com.sma.admin.common.request.*;
import com.sma.admin.common.security.*;
import com.sma.admin.config.internal.service.ConfigurationCatalogRegistry.Kind;
import com.sma.admin.config.internal.service.ConfigurationPlatformGuard;
import com.sma.admin.config.internal.service.SharedConfigurationService;
import com.sma.admin.config.internal.service.SharedConfigurationService.*;

@RestController @RequestMapping("/api/config/shared-configurations")
@GovernedApi(group="config.shared")
public class SharedConfigurationController implements RequestAuthorizer {
    private final SharedConfigurationService shared;
    private final ConfigurationPlatformGuard access;
    public SharedConfigurationController(SharedConfigurationService shared, ConfigurationPlatformGuard access) { this.shared = shared; this.access = access; }
    public record ParameterInput(@Min(1) long expectedVersion, @NotNull ParameterContent content) {}
    public record DictionaryInput(@Min(1) long expectedVersion, @NotNull DictionaryContent content) {}
    public record PublishInput(@Min(1) long expectedDraftVersion, @NotNull @Min(1) Long expectedPublishedRevisionId) {}
    @Override public void authorizeRequest(String method, Map<String,String> variables) {
        // LOCAL replays do not re-enter the service. Recheck deployment authority and the original action here too.
        access.require(switch (method) {
            case "saveParameter", "saveDictionary" -> "config:shared:edit";
            case "publish" -> "config:shared:publish";
            default -> "config:shared:view";
        });
    }
    @GetMapping @RequiresPermission("config:shared:view")
    public ApiResponse<PageResult<Summary>> page(@ModelAttribute PageQuery page, @RequestParam(required=false) Kind kind, @RequestParam(required=false) String keyword) {
        return ApiResponse.ok(shared.page(page, kind, keyword));
    }
    @GetMapping("/{id}") @RequiresPermission("config:shared:view")
    public ApiResponse<Detail> get(@PathVariable long id) { return ApiResponse.ok(shared.get(id)); }
    @PutMapping("/{id}/parameter-draft") @RequiresPermission("config:shared:edit") @AuditAction("config:shared:edit")
    public ApiResponse<Detail> saveParameter(@PathVariable long id, @Valid @RequestBody ParameterInput input) { return ApiResponse.ok(shared.saveParameter(id, input.expectedVersion(), input.content())); }
    @PutMapping("/{id}/dictionary-draft") @RequiresPermission("config:shared:edit") @AuditAction("config:shared:edit")
    public ApiResponse<Detail> saveDictionary(@PathVariable long id, @Valid @RequestBody DictionaryInput input) { return ApiResponse.ok(shared.saveDictionary(id, input.expectedVersion(), input.content())); }
    @PostMapping("/{id}/publications") @RequiresPermission("config:shared:publish") @AuditAction("config:shared:publish")
    public ApiResponse<Publication> publish(@PathVariable long id, @Valid @RequestBody PublishInput input) { return ApiResponse.ok(shared.publish(id, input.expectedDraftVersion(), input.expectedPublishedRevisionId())); }
    @GetMapping("/{id}/revisions") @RequiresPermission("config:shared:view")
    public ApiResponse<PageResult<RevisionSummary>> history(@PathVariable long id, @ModelAttribute PageQuery page) { return ApiResponse.ok(shared.history(id, page)); }
    @GetMapping("/{id}/revisions/{revisionId}") @RequiresPermission("config:shared:view")
    public ApiResponse<RevisionDetail> revision(@PathVariable long id, @PathVariable long revisionId) { return ApiResponse.ok(shared.revision(id, revisionId)); }
}
