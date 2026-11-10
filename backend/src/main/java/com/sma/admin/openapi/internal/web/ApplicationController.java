package com.sma.admin.openapi.internal.web;
import java.util.List;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import com.sma.admin.common.*;
import com.sma.admin.common.security.RequiresPermission;
import com.sma.admin.openapi.internal.service.ApplicationService;
@RestController @RequestMapping("/api/openapi/apps")
public class ApplicationController {
 private final ApplicationService service;public ApplicationController(ApplicationService service){this.service=service;}
 @GetMapping @RequiresPermission("openapi:app:view") public ApiResponse<PageResult<ApplicationService.View>> page(@ModelAttribute PageQuery page,@RequestParam(required=false)String keyword,@RequestParam(required=false)Integer type,@RequestParam(required=false)Boolean enabled){return ApiResponse.ok(service.page(page,keyword,type,enabled));}
 @GetMapping("/{id}") @RequiresPermission("openapi:app:view") public ApiResponse<ApplicationService.View> get(@PathVariable long id){return ApiResponse.ok(service.get(id));}
 @GetMapping("/types") @RequiresPermission("openapi:app:view") public ApiResponse<List<OptionItem>> types(){return ApiResponse.ok(service.types());}
 @PostMapping @RequiresPermission("openapi:app:create") public ApiResponse<ApplicationService.Credentials> create(@Valid @RequestBody ApplicationService.Input input){return ApiResponse.ok(service.create(input));}
 @PutMapping("/{id}") @RequiresPermission("openapi:app:update") public ApiResponse<ApplicationService.View> update(@PathVariable long id,@Valid @RequestBody ApplicationService.Input input){return ApiResponse.ok(service.update(id,input));}
 @PostMapping("/{id}/rotate") @RequiresPermission("openapi:app:rotate") public ApiResponse<ApplicationService.Credentials> rotate(@PathVariable long id){return ApiResponse.ok(service.rotate(id));}
 @PutMapping("/{id}/target") @RequiresPermission("openapi:app:link") public ApiResponse<ApplicationService.View> link(@PathVariable long id,@Valid @RequestBody ApplicationService.Target input){return ApiResponse.ok(service.link(id,input));}
 @DeleteMapping("/{id}/target") @RequiresPermission("openapi:app:link") public ApiResponse<ApplicationService.View> unlink(@PathVariable long id){return ApiResponse.ok(service.unlink(id));}
 @DeleteMapping("/{id}") @RequiresPermission("openapi:app:delete") public ApiResponse<Void> delete(@PathVariable long id){service.delete(id);return ApiResponse.ok();}
}
