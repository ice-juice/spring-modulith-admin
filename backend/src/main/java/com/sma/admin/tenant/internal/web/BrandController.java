package com.sma.admin.tenant.internal.web;

import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import com.sma.admin.common.*;
import com.sma.admin.common.security.*;
import com.sma.admin.tenant.BrandContent;
import com.sma.admin.tenant.internal.service.*;

@RestController @RequestMapping("/api/tenant/brands")
@com.sma.admin.common.request.GovernedApi(group="tenant.brands")
public class BrandController implements com.sma.admin.common.request.RequestAuthorizer {
    @Override public void authorizeRequest(String method,java.util.Map<String,String> pathVariables){CurrentUser.require();}
    private final BrandDraftService drafts;private final BrandPublicationService publications;private final LoginTemplates templates;private final BrandPreviewService previews;
    public BrandController(BrandDraftService drafts,BrandPublicationService publications,LoginTemplates templates,BrandPreviewService previews) { this.drafts=drafts;this.publications=publications;this.templates=templates;this.previews=previews; }
    public record DraftInput(@Min(0) long expectedVersion,@NotNull BrandContent content) {}
    public record PublishInput(@NotBlank @Pattern(regexp="[a-zA-Z0-9_-]{16,64}") String operationId,@Min(1) long expectedDraftVersion,Long expectedPublishedRevisionId) {}
    public record RollbackInput(@NotBlank @Pattern(regexp="[a-zA-Z0-9_-]{16,64}") String operationId,@Min(1) long sourceRevisionId,@NotNull @Min(1) Long expectedPublishedRevisionId) {}
    @GetMapping("/draft") @RequiresPermission("tenant:brand:view")
    public ApiResponse<BrandDraftService.Draft> draft() { return ApiResponse.ok(drafts.get()); }
    @PutMapping("/draft") @RequiresPermission("tenant:brand:edit") @AuditAction("tenant:brand:edit")
    public ApiResponse<BrandDraftService.Draft> save(@Valid @RequestBody DraftInput input) { return ApiResponse.ok(drafts.save(input.expectedVersion(),input.content())); }
    @GetMapping("/templates") @RequiresPermission("tenant:brand:view")
    public ApiResponse<List<LoginTemplates.Template>> templates() { return ApiResponse.ok(templates.all()); }
    @PostMapping("/publications") @RequiresPermission("tenant:brand:publish") @AuditAction("tenant:brand:publish")
    @com.sma.admin.common.request.GovernedApi(group="tenant.brands",mode=com.sma.admin.common.request.GovernedApi.Mode.NATIVE,reason="Immutable brand publication and operationId receipt owned by tenant")
    public ApiResponse<BrandPublicationService.Receipt> publish(@Valid @RequestBody PublishInput input) { return ApiResponse.ok(publications.publish(input.operationId(),input.expectedDraftVersion(),input.expectedPublishedRevisionId())); }
    @PostMapping("/rollbacks") @RequiresPermission("tenant:brand:rollback") @AuditAction("tenant:brand:rollback")
    @com.sma.admin.common.request.GovernedApi(group="tenant.brands",mode=com.sma.admin.common.request.GovernedApi.Mode.NATIVE,reason="Brand rollback appends a revision using its native operationId receipt")
    public ApiResponse<BrandPublicationService.Receipt> rollback(@Valid @RequestBody RollbackInput input) { return ApiResponse.ok(publications.rollback(input.operationId(),input.sourceRevisionId(),input.expectedPublishedRevisionId())); }
    @GetMapping("/revisions") @RequiresPermission("tenant:brand:view")
    public ApiResponse<PageResult<BrandPublicationService.Revision>> history(@ModelAttribute PageQuery page) { return ApiResponse.ok(publications.history(page)); }
    @GetMapping("/revisions/{id}") @RequiresPermission("tenant:brand:view")
    public ApiResponse<BrandPublicationService.Revision> revision(@PathVariable long id) { return ApiResponse.ok(publications.get(id)); }
    @GetMapping("/operations/{operationId}") @RequiresPermission("tenant:brand:view")
    public ApiResponse<BrandPublicationService.OperationResult> operation(@PathVariable String operationId) { return ApiResponse.ok(publications.operationResult(operationId)); }
    @PostMapping("/previews") @RequiresPermission("tenant:brand:view")
    @com.sma.admin.common.request.GovernedApi(group="tenant.brands",mode=com.sma.admin.common.request.GovernedApi.Mode.QUERY,reason="Bounded transient projection; no draft, reference or publication is saved")
    public org.springframework.http.ResponseEntity<ApiResponse<BrandPreviewService.Preview>> preview(@Valid @RequestBody DraftInput input) {
        return org.springframework.http.ResponseEntity.ok().header("Cache-Control","no-store")
                .body(ApiResponse.ok(previews.preview(input.expectedVersion(),input.content())));
    }
}
