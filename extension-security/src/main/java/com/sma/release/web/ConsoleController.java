package com.sma.release.web;

import java.util.List;
import org.springframework.web.bind.annotation.*;
import com.sma.release.catalog.*;
import com.sma.release.auth.SecurityRecords;
import com.sma.release.deployment.DeploymentRecords;

@RestController @RequestMapping("/release/v1/console")
public class ConsoleController {
    private final ConsoleQueries queries;
    public ConsoleController(ConsoleQueries queries){this.queries=queries;}
    @GetMapping("/access") ApiReply<ConsoleQueries.Access> access(@RequestHeader("Authorization")String auth){return ApiReply.ok(queries.access(ReleaseController.token(auth)));}
    @GetMapping("/artifacts") ApiReply<ConsoleQueries.Page<CatalogRecords.Artifact>> artifacts(@RequestHeader("Authorization")String auth,@RequestParam String plugin,@RequestParam String environment,
            @RequestParam(defaultValue="0")int offset,@RequestParam(defaultValue="20")int limit){return ApiReply.ok(queries.artifacts(ReleaseController.token(auth),plugin,environment,offset,limit));}
    @GetMapping("/jobs") ApiReply<List<DeploymentRecords.ValidationJob>> jobs(@RequestHeader("Authorization")String auth,@RequestParam String plugin,@RequestParam String environment){return ApiReply.ok(queries.jobs(ReleaseController.token(auth),plugin,environment));}
    @GetMapping("/actions/{key}") ApiReply<CatalogRecords.ActionReceipt> action(@RequestHeader("Authorization")String auth,@PathVariable String key){return ApiReply.ok(queries.byKey(ReleaseController.token(auth),key));}
    @GetMapping("/storage") ApiReply<ConsoleQueries.Storage> storage(@RequestHeader("Authorization")String auth){return ApiReply.ok(queries.storage(ReleaseController.token(auth)));}
    @GetMapping("/grants") ApiReply<List<SecurityRecords.ScopeGrant>> grants(@RequestHeader("Authorization")String auth,@RequestParam String group,@RequestParam String environment){return ApiReply.ok(queries.grants(ReleaseController.token(auth),group,environment));}
}
