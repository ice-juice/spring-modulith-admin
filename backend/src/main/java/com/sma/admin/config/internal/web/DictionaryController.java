package com.sma.admin.config.internal.web;
import java.util.List;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import com.sma.admin.common.*;
import com.sma.admin.common.security.*;
import com.sma.admin.config.DictionaryOption;
import com.sma.admin.config.internal.service.DictionaryService;

@RestController @RequestMapping("/api/config/dictionaries")
@com.sma.admin.common.request.GovernedApi(group="config.dictionaries")
public class DictionaryController implements com.sma.admin.common.request.RequestAuthorizer {
    private final DictionaryService service;
    private final com.sma.admin.config.internal.service.BuiltinDictionaries builtin;
    public DictionaryController(DictionaryService service,com.sma.admin.config.internal.service.BuiltinDictionaries builtin) { this.service=service;this.builtin=builtin; }
    @Override public void authorizeRequest(String method,java.util.Map<String,String> variables){
        CurrentUser.require();
    }
    @PostMapping("/initialize") @RequiresPermission("config:dictionary:update") @AuditAction("config:dictionary:initialize")
    public ApiResponse<Void> initialize(){builtin.initialize();return ApiResponse.ok();}
    @GetMapping("/types") @RequiresPermission("config:dictionary:view")
    public ApiResponse<List<DictionaryService.TypeView>> types() { return ApiResponse.ok(service.types()); }
    @PostMapping("/types") @RequiresPermission("config:dictionary:create")
    public ApiResponse<DictionaryService.TypeView> createType(@Valid @RequestBody DictionaryService.TypeInput input) { return ApiResponse.ok(service.saveType(null,input)); }
    @PutMapping("/types/{id}") @RequiresPermission("config:dictionary:update")
    public ApiResponse<DictionaryService.TypeView> updateType(@PathVariable long id,@Valid @RequestBody DictionaryService.TypeInput input) { return ApiResponse.ok(service.saveType(id,input)); }
    @DeleteMapping("/types/{id}") @RequiresPermission("config:dictionary:delete")
    public ApiResponse<Void> deleteType(@PathVariable long id) { service.deleteType(id); return ApiResponse.ok(); }
    @GetMapping("/items") @RequiresPermission("config:dictionary:view")
    public ApiResponse<List<DictionaryService.ItemView>> items(@RequestParam long typeId) { return ApiResponse.ok(service.items(typeId)); }
    @PostMapping("/items") @RequiresPermission("config:dictionary:create")
    public ApiResponse<DictionaryService.ItemView> createItem(@Valid @RequestBody DictionaryService.ItemInput input) { return ApiResponse.ok(service.saveItem(null,input)); }
    @PutMapping("/items/{id}") @RequiresPermission("config:dictionary:update")
    public ApiResponse<DictionaryService.ItemView> updateItem(@PathVariable long id,@Valid @RequestBody DictionaryService.ItemInput input) { return ApiResponse.ok(service.saveItem(id,input)); }
    @DeleteMapping("/items/{id}") @RequiresPermission("config:dictionary:delete")
    public ApiResponse<Void> deleteItem(@PathVariable long id) { service.deleteItem(id); return ApiResponse.ok(); }
    @GetMapping("/{key}/options") @PermitLoggedIn
    public ApiResponse<List<DictionaryOption>> options(@PathVariable String key) { return ApiResponse.ok(service.options(key)); }
}
