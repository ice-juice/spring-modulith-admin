package com.sma.release.web;

import java.util.List;
import org.springframework.web.bind.annotation.*;
import com.sma.release.deployment.DeploymentService;

@RestController
@RequestMapping("/release/v1/deployments")
public class DeploymentController {
    private final DeploymentService deployments;
    public DeploymentController(DeploymentService deployments){this.deployments=deployments;}
    @GetMapping("/{id}/guard") ApiReply<DeploymentService.GuardView> guard(@RequestHeader("Authorization")String auth,@RequestParam String environment,@PathVariable String id){return ApiReply.ok(deployments.guard(ReleaseController.token(auth),environment,id));}
    @GetMapping("/{id}/history") ApiReply<List<com.sma.extension.protocol.ExecutionDocuments.Release>> history(@RequestHeader("Authorization")String authorization,@RequestParam String environment,@PathVariable String id) {
        return ApiReply.ok(deployments.history(ReleaseController.token(authorization),environment,id));
    }
    @GetMapping ApiReply<List<DeploymentService.DeploymentView>> list(@RequestHeader("Authorization")String authorization,@RequestParam String environment,
            @RequestParam(defaultValue="0")int offset,@RequestParam(defaultValue="100")int limit) {
        return ApiReply.ok(deployments.list(ReleaseController.token(authorization),environment,offset,limit));
    }
    @GetMapping("/{id}") ApiReply<DeploymentService.DeploymentView> get(@RequestHeader("Authorization")String authorization,@RequestParam String environment,@PathVariable String id) {
        return ApiReply.ok(deployments.view(ReleaseController.token(authorization),environment,id));
    }
}
