package com.sma.admin.rule.internal.web;

import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import com.sma.admin.common.ApiResponse;
import com.sma.admin.common.security.RequiresPermission;
import com.sma.admin.rule.RuleCommands;
import com.sma.admin.rule.RuleExecutionApi;
import com.sma.admin.rule.internal.service.RuleService;

/** Raw JSON is decoded by the module's exact-decimal mapper, preserving money before Object binding. */
@RestController
@RequestMapping(value = "/api/rule", produces = MediaType.APPLICATION_JSON_VALUE)
@com.sma.admin.common.request.GovernedApi(group="rule.assets")
public class RuleController implements com.sma.admin.common.request.RequestAuthorizer {
 @Override public void authorizeRequest(String method,java.util.Map<String,String> variables){com.sma.admin.common.security.CurrentUser.require();}
    private final RuleService service;
    public RuleController(RuleService service) { this.service = service; }
    @GetMapping("/functions") @RequiresPermission("rule:template:view")
    public ApiResponse<List<String>> functions() { return ApiResponse.ok(service.functions()); }
    @PostMapping(value = "/scenes", consumes = MediaType.APPLICATION_JSON_VALUE) @RequiresPermission("rule:scene:edit")
    public ApiResponse<RuleCommands.Asset> createScene(@RequestBody String body) {
        return ApiResponse.ok(service.createScene(service.decode(body, RuleCommands.Scene.class)));
    }
    @GetMapping("/scenes") @RequiresPermission("rule:scene:view")
    public ApiResponse<List<RuleCommands.Asset>> scenes(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.list("SCENE", page, size));
    }
    @PutMapping(value = "/scenes/{id}", consumes = MediaType.APPLICATION_JSON_VALUE) @RequiresPermission("rule:scene:edit")
    public ApiResponse<RuleCommands.Asset> scene(@PathVariable long id, @RequestBody String body) {
        return ApiResponse.ok(service.saveScene(id, service.decode(body, RuleCommands.SaveScene.class)));
    }
    @PostMapping(value = "/templates", consumes = MediaType.APPLICATION_JSON_VALUE) @RequiresPermission("rule:template:edit")
    public ApiResponse<RuleCommands.Asset> createTemplate(@RequestBody String body) {
        return ApiResponse.ok(service.createTemplate(service.decode(body, RuleCommands.Template.class)));
    }
    @GetMapping("/templates") @RequiresPermission("rule:template:view")
    public ApiResponse<List<RuleCommands.Asset>> templates(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.list("TEMPLATE", page, size));
    }
    @GetMapping("/templates/{id}") @RequiresPermission("rule:template:view")
    public ApiResponse<RuleCommands.Asset> template(@PathVariable long id) { return ApiResponse.ok(service.get(id, "TEMPLATE")); }
    @GetMapping("/templates/{id}/scene") @RequiresPermission("rule:template:view")
    public ApiResponse<RuleCommands.Asset> templateScene(@PathVariable long id) { return ApiResponse.ok(service.templateScene(id)); }
    @PutMapping(value = "/templates/{id}/draft", consumes = MediaType.APPLICATION_JSON_VALUE) @RequiresPermission("rule:template:edit")
    public ApiResponse<RuleCommands.Asset> saveTemplate(@PathVariable long id, @RequestBody String body) {
        return ApiResponse.ok(service.saveTemplate(id, service.decode(body, RuleCommands.Save.class)));
    }
    @PostMapping(value = "/templates/{id}/publish", consumes = MediaType.APPLICATION_JSON_VALUE) @RequiresPermission("rule:template:publish")
    @com.sma.admin.common.request.GovernedApi(group="rule.assets",mode=com.sma.admin.common.request.GovernedApi.Mode.NATIVE,reason="Release operationKey owns replay; candidate reports commit independently")
    public ApiResponse<RuleCommands.Release> publishTemplate(@PathVariable long id, @RequestBody String body) {
        return ApiResponse.ok(service.publishTemplate(id, service.decode(body, RuleCommands.Publish.class)));
    }
    @GetMapping("/templates/{id}/publications/{operationKey}") @RequiresPermission("rule:template:publish")
    public ApiResponse<RuleCommands.PublicationLookup> publication(@PathVariable long id, @PathVariable String operationKey,
            jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return ApiResponse.ok(service.publication(id, operationKey));
    }
    @PostMapping("/templates/{id}/deprecate") @RequiresPermission("rule:template:lifecycle")
    public ApiResponse<RuleCommands.Release> deprecate(@PathVariable long id, @RequestBody String body) {
        return ApiResponse.ok(service.lifecycle(id, service.decode(body, RuleCommands.Publish.class), "DEPRECATE"));
    }
    @PostMapping("/templates/{id}/revoke") @RequiresPermission("rule:template:lifecycle")
    public ApiResponse<RuleCommands.Release> revoke(@PathVariable long id, @RequestBody String body) {
        return ApiResponse.ok(service.lifecycle(id, service.decode(body, RuleCommands.Publish.class), "REVOKE"));
    }
    @PostMapping(value = "/templates/{id}/copy", consumes = MediaType.APPLICATION_JSON_VALUE) @RequiresPermission("rule:template:edit")
    public ApiResponse<RuleCommands.Asset> copyTemplate(@PathVariable long id, @RequestBody String body) {
        return ApiResponse.ok(service.copyTemplate(id, service.decode(body, RuleCommands.Copy.class)));
    }
    @GetMapping("/templates/{id}/instances") @RequiresPermission("rule:template:view")
    public ApiResponse<List<RuleCommands.Asset>> impacted(@PathVariable long id, @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) { return ApiResponse.ok(service.impacted(id, page, size)); }
    @GetMapping("/assets/{id}/diff") @RequiresPermission("rule:template:view")
    public ApiResponse<RuleCommands.VersionDiff> diff(@PathVariable long id, @RequestParam long fromVersionId, @RequestParam long toVersionId) {
        return ApiResponse.ok(service.diff(id, fromVersionId, toVersionId));
    }
    @PostMapping(value = "/instances/{id}/upgrade-preview", consumes = MediaType.APPLICATION_JSON_VALUE) @RequiresPermission("rule:instance:edit")
    @com.sma.admin.common.request.GovernedApi(group="rule.assets",mode=com.sma.admin.common.request.GovernedApi.Mode.QUERY,reason="Migration preview does not save a draft")
    public ApiResponse<RuleCommands.Preview> preview(@PathVariable long id, @RequestBody String body) {
        return ApiResponse.ok(service.preview(id, service.decode(body, RuleCommands.Migration.class)));
    }
    @PostMapping(value = "/instances/{id}/upgrade-draft", consumes = MediaType.APPLICATION_JSON_VALUE) @RequiresPermission("rule:instance:edit")
    public ApiResponse<RuleCommands.Asset> migrate(@PathVariable long id, @RequestBody String body) {
        return ApiResponse.ok(service.migrateDraft(id, service.decode(body, RuleCommands.Migration.class)));
    }
    @GetMapping("/catalog") @RequiresPermission("rule:catalog:view")
    public ApiResponse<List<RuleCommands.Version>> catalog(@RequestParam long sceneId) { return ApiResponse.ok(service.catalog(sceneId)); }
    @GetMapping("/catalog/entries") @RequiresPermission("rule:catalog:view")
    public ApiResponse<List<RuleCommands.CatalogEntry>> catalogEntries(@RequestParam long sceneId) { return ApiResponse.ok(service.catalogEntries(sceneId)); }
    @GetMapping("/catalog/scenes") @RequiresPermission("rule:catalog:view")
    public ApiResponse<List<RuleCommands.Asset>> catalogScenes(@RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="20") int size) {
        return ApiResponse.ok(service.catalogScenes(page, size));
    }
    @GetMapping("/instances/{id}/template") @RequiresPermission("rule:instance:view")
    public ApiResponse<RuleCommands.Version> instanceTemplate(@PathVariable long id) { return ApiResponse.ok(service.instanceTemplate(id)); }
    @PostMapping(value = "/validate", consumes = MediaType.APPLICATION_JSON_VALUE) @RequiresPermission("rule:simulation:run")
    @com.sma.admin.common.request.GovernedApi(group="rule.assets",mode=com.sma.admin.common.request.GovernedApi.Mode.QUERY,reason="Bounded validation without business writes")
    public ApiResponse<RuleCommands.Validation> validate(@RequestBody String body) {
        return ApiResponse.ok(service.validate(service.decode(body, RuleCommands.Simulate.class)));
    }
    @PostMapping(value = "/simulations", consumes = MediaType.APPLICATION_JSON_VALUE) @RequiresPermission("rule:simulation:run")
    @com.sma.admin.common.request.GovernedApi(group="rule.assets",mode=com.sma.admin.common.request.GovernedApi.Mode.QUERY,reason="Bounded simulation without a persisted execution record")
    public ApiResponse<RuleExecutionApi.Result> simulate(@RequestBody String body) {
        return ApiResponse.ok(service.simulate(service.decode(body, RuleCommands.Simulate.class)));
    }
    @PostMapping(value = "/instances", consumes = MediaType.APPLICATION_JSON_VALUE) @RequiresPermission("rule:instance:edit")
    public ApiResponse<RuleCommands.Asset> createInstance(@RequestBody String body) {
        return ApiResponse.ok(service.createInstance(service.decode(body, RuleCommands.Instance.class)));
    }
    @GetMapping("/instances") @RequiresPermission("rule:instance:view")
    public ApiResponse<List<RuleCommands.Asset>> instances(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.list("INSTANCE", page, size));
    }
    @GetMapping("/instances/{id}") @RequiresPermission("rule:instance:view")
    public ApiResponse<RuleCommands.Asset> instance(@PathVariable long id) { return ApiResponse.ok(service.get(id, "INSTANCE")); }
    @PutMapping(value = "/instances/{id}/draft", consumes = MediaType.APPLICATION_JSON_VALUE) @RequiresPermission("rule:instance:edit")
    public ApiResponse<RuleCommands.Asset> saveInstance(@PathVariable long id, @RequestBody String body) {
        return ApiResponse.ok(service.saveInstance(id, service.decode(body, RuleCommands.SaveInstance.class)));
    }
    @PostMapping(value = "/instances/{id}/trial", consumes = MediaType.APPLICATION_JSON_VALUE) @RequiresPermission("rule:instance:edit")
    public ApiResponse<RuleExecutionApi.Result> trial(@PathVariable long id, @RequestBody String body) {
        return ApiResponse.ok(service.trial(id, service.decode(body, RuleCommands.Trial.class)));
    }
    @PostMapping(value = "/instances/{id}/publish", consumes = MediaType.APPLICATION_JSON_VALUE) @RequiresPermission("rule:instance:publish")
    public ApiResponse<RuleCommands.Release> publishInstance(@PathVariable long id, @RequestBody String body) {
        return ApiResponse.ok(service.publishInstance(id, service.decode(body, RuleCommands.Publish.class)));
    }
    @PostMapping(value = "/instances/{id}/disable", consumes = MediaType.APPLICATION_JSON_VALUE) @RequiresPermission("rule:instance:disable")
    public ApiResponse<RuleCommands.Release> disable(@PathVariable long id, @RequestBody String body) {
        return ApiResponse.ok(service.disable(id, service.decode(body, RuleCommands.Publish.class)));
    }
    @PostMapping(value = "/assets/{id}/rollback", consumes = MediaType.APPLICATION_JSON_VALUE) @RequiresPermission("rule:release:rollback")
    public ApiResponse<RuleCommands.Release> rollback(@PathVariable long id, @RequestBody String body) {
        return ApiResponse.ok(service.rollback(id, service.decode(body, RuleCommands.Rollback.class)));
    }
    @GetMapping("/assets/{id}/versions") @RequiresPermission("rule:template:view")
    public ApiResponse<List<RuleCommands.Version>> versions(@PathVariable long id) { return ApiResponse.ok(service.versions(id)); }
    @PostMapping(value = "/instances/{id}/execute", consumes = MediaType.APPLICATION_JSON_VALUE) @RequiresPermission("rule:execution:run")
    public ApiResponse<RuleExecutionApi.Result> execute(@PathVariable long id, @RequestBody String body) {
        var command = service.decode(body, RuleCommands.Execute.class);
        return ApiResponse.ok(service.execute(new RuleExecutionApi.Request(id, command.input())));
    }
    @GetMapping("/instances/{id}/executions") @RequiresPermission("rule:execution:view")
    public ApiResponse<List<Map<String, Object>>> executions(@PathVariable long id, @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(service.executionList(id, limit));
    }
}
