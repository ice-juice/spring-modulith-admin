package com.sma.admin.file.internal.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import com.sma.admin.common.*;
import com.sma.admin.common.request.*;
import com.sma.admin.common.security.*;
import com.sma.admin.file.internal.service.StorageProfileService;

@RestController @RequestMapping("/api/file/storage-profiles") @GovernedApi(group="file.storage-profiles")
public class StorageProfileController implements RequestAuthorizer {
    private final StorageProfileService service;
    public StorageProfileController(StorageProfileService service){this.service=service;}
    @Override public void authorizeRequest(String method,java.util.Map<String,String> pathVariables){CurrentUser.require();}
    public record Update(@Positive long expectedVersion,@NotNull @Valid StorageProfileService.Input configuration){}
    public record Stop(@Positive long expectedVersion,@NotNull StorageProfileService.Stop scope){}
    @GetMapping @RequiresPermission("file:storage:view")
    public ApiResponse<PageResult<StorageProfileService.View>> page(@ModelAttribute PageQuery page,@RequestParam(required=false)String keyword){return ApiResponse.ok(service.page(page,keyword));}
    @GetMapping("/options") @RequiresPermission("file:storage:view")
    public ApiResponse<StorageProfileService.Options> options(){return ApiResponse.ok(service.options());}
    @GetMapping("/{id}") @RequiresPermission("file:storage:view")
    public ApiResponse<StorageProfileService.View> get(@PathVariable long id){return ApiResponse.ok(service.get(id));}
    @PostMapping @RequiresPermission("file:storage:manage")
    public ApiResponse<StorageProfileService.View> create(@Valid @RequestBody StorageProfileService.Input input){return ApiResponse.ok(service.create(input));}
    @PutMapping("/{id}") @RequiresPermission("file:storage:manage")
    public ApiResponse<StorageProfileService.View> update(@PathVariable long id,@Valid @RequestBody Update input){return ApiResponse.ok(service.update(id,input.expectedVersion(),input.configuration()));}
    @PostMapping("/{id}/stop") @RequiresPermission("file:storage:manage")
    public ApiResponse<StorageProfileService.View> stop(@PathVariable long id,@Valid @RequestBody Stop input){return ApiResponse.ok(service.stop(id,input.expectedVersion(),input.scope()));}
}
