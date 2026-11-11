package com.sma.admin.file.internal.web;

import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import com.sma.admin.common.*;
import com.sma.admin.common.request.*;
import com.sma.admin.common.security.*;
import com.sma.admin.file.FileSceneCatalog;
import com.sma.admin.file.internal.service.StorageSceneService;

@RestController @RequestMapping("/api/file/scenes") @GovernedApi(group="file.scenes")
public class StorageSceneController implements RequestAuthorizer {
    private final StorageSceneService service;
    public StorageSceneController(StorageSceneService service){this.service=service;}
    @Override public void authorizeRequest(String method,java.util.Map<String,String> pathVariables){CurrentUser.require();}
    public record Update(@Positive long expectedVersion,@NotNull StorageSceneService.Input configuration){}
    public record Version(@Positive long expectedVersion){}
    @GetMapping("/catalog") @RequiresPermission("file:scene:view") public ApiResponse<List<FileSceneCatalog.Definition>> catalog(){return ApiResponse.ok(service.catalog());}
    @GetMapping @RequiresPermission("file:scene:view") public ApiResponse<PageResult<StorageSceneService.View>> page(@ModelAttribute PageQuery page,@RequestParam(required=false)String keyword){return ApiResponse.ok(service.page(page,keyword));}
    @GetMapping("/{id}") @RequiresPermission("file:scene:view") public ApiResponse<StorageSceneService.View> get(@PathVariable long id){return ApiResponse.ok(service.get(id));}
    @GetMapping("/{id}/published") @RequiresPermission("file:scene:view") public ApiResponse<StorageSceneService.Publication> published(@PathVariable long id){return ApiResponse.ok(service.published(id));}
    @PostMapping @RequiresPermission("file:scene:manage") public ApiResponse<StorageSceneService.View> create(@RequestBody StorageSceneService.Input input){return ApiResponse.ok(service.create(input));}
    @PutMapping("/{id}") @RequiresPermission("file:scene:manage") public ApiResponse<StorageSceneService.View> update(@PathVariable long id,@Valid @RequestBody Update input){return ApiResponse.ok(service.update(id,input.expectedVersion(),input.configuration()));}
    @PostMapping("/{id}/publish") @RequiresPermission("file:scene:publish") public ApiResponse<StorageSceneService.View> publish(@PathVariable long id,@Valid @RequestBody Version input){return ApiResponse.ok(service.publish(id,input.expectedVersion()));}
    @PostMapping("/{id}/disable") @RequiresPermission("file:scene:manage") public ApiResponse<StorageSceneService.View> disable(@PathVariable long id,@Valid @RequestBody Version input){return ApiResponse.ok(service.disable(id,input.expectedVersion()));}
}
