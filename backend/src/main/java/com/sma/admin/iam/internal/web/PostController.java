package com.sma.admin.iam.internal.web;

import java.util.List;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import com.sma.admin.common.*;
import com.sma.admin.common.security.RequiresPermission;
import com.sma.admin.iam.internal.service.PostService;

@RestController
@RequestMapping("/api/iam/posts")
public class PostController {
    private final PostService service;
    public PostController(PostService service) { this.service = service; }
    @GetMapping @RequiresPermission("iam:post:view")
    public ApiResponse<PageResult<PostService.View>> page(@ModelAttribute PageQuery page, @RequestParam(required=false) String keyword, @RequestParam(required=false) Boolean enabled) { return ApiResponse.ok(service.page(page, keyword, enabled)); }
    @GetMapping("/options") @RequiresPermission("iam:post:view")
    public ApiResponse<List<OptionItem>> options() { return ApiResponse.ok(service.options()); }
    @PostMapping @RequiresPermission("iam:post:create")
    public ApiResponse<PostService.View> create(@Valid @RequestBody PostService.Input input) { return ApiResponse.ok(service.save(null, input)); }
    @PutMapping("/{id}") @RequiresPermission("iam:post:update")
    public ApiResponse<PostService.View> update(@PathVariable long id, @Valid @RequestBody PostService.Input input) { return ApiResponse.ok(service.save(id, input)); }
    @DeleteMapping("/{id}") @RequiresPermission("iam:post:delete")
    public ApiResponse<Void> delete(@PathVariable long id) { service.delete(id); return ApiResponse.ok(); }
}
