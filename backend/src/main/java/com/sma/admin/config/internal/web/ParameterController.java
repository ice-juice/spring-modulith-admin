package com.sma.admin.config.internal.web;
import java.util.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import com.sma.admin.common.ApiResponse;
import com.sma.admin.common.security.*;
import com.sma.admin.config.internal.service.ParameterService;

@RestController @RequestMapping("/api/config/parameters")
public class ParameterController {
 private final ParameterService service;
 private final com.sma.admin.config.internal.service.BuiltinParameters builtin;
 public ParameterController(ParameterService service,com.sma.admin.config.internal.service.BuiltinParameters builtin){this.service=service;this.builtin=builtin;}
 @PostMapping("/reset-builtins") @RequiresPermission("config:parameter:update") @AuditAction("config:parameter:reset")
 public ApiResponse<Void> resetBuiltins(){builtin.resetDefaults();return ApiResponse.ok();}
 @GetMapping @RequiresPermission("config:parameter:view")
 public ApiResponse<List<ParameterService.View>> list(@RequestParam(required=false)String group,@RequestParam(required=false)String keyword){return ApiResponse.ok(service.list(group,keyword));}
 @GetMapping("/groups") @RequiresPermission("config:parameter:view")
 public ApiResponse<List<ParameterService.GroupView>> groups(){return ApiResponse.ok(service.groups());}
 @PostMapping("/groups") @RequiresPermission("config:parameter:create")
 public ApiResponse<ParameterService.GroupView> createGroup(@Valid @RequestBody ParameterService.GroupInput input){return ApiResponse.ok(service.saveGroup(null,input));}
 @PutMapping("/groups/{id}") @RequiresPermission("config:parameter:update")
 public ApiResponse<ParameterService.GroupView> updateGroup(@PathVariable long id,@Valid @RequestBody ParameterService.GroupInput input){return ApiResponse.ok(service.saveGroup(id,input));}
 @DeleteMapping("/groups/{id}") @RequiresPermission("config:parameter:delete")
 public ApiResponse<Void> deleteGroup(@PathVariable long id){service.deleteGroup(id);return ApiResponse.ok();}
 @PostMapping @RequiresPermission("config:parameter:create")
 public ApiResponse<ParameterService.View> create(@Valid @RequestBody ParameterService.Input input){return ApiResponse.ok(service.save(null,input));}
 @PutMapping("/{id}") @RequiresPermission("config:parameter:update")
 public ApiResponse<ParameterService.View> update(@PathVariable long id,@Valid @RequestBody ParameterService.Input input){return ApiResponse.ok(service.save(id,input));}
 @DeleteMapping("/{id}") @RequiresPermission("config:parameter:delete")
 public ApiResponse<Void> delete(@PathVariable long id){service.delete(id);return ApiResponse.ok();}
 @PostMapping("/refresh") @RequiresPermission("config:parameter:update")
 public ApiResponse<Void> refresh(){service.refresh();return ApiResponse.ok();}
 @GetMapping("/public") @PermitAnonymous
 public ApiResponse<Map<String,String>> publicValues(){return ApiResponse.ok(service.publicValues());}
}
