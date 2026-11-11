package com.sma.release.web;

import java.io.IOException;
import java.util.Set;
import jakarta.servlet.http.HttpServletRequest;
import com.sma.extension.protocol.Checks;
import com.sma.release.catalog.ArtifactFiles;
import com.sma.release.catalog.CatalogRecords;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.InputStreamResource;
import org.springframework.web.bind.annotation.*;
import com.sma.release.deployment.*;
import com.sma.release.deployment.DeploymentRecords.*;

/** Workload tokens are distinct from human sessions, limited by kind and environment. */
@RestController
@RequestMapping("/release/v1/machines")
public class MachineController {
    private final ValidationService validation;
    private final DeploymentService deployments;
    public MachineController(ValidationService validation,DeploymentService deployments) { this.validation=validation; this.deployments=deployments; }
    @PostMapping("/nodes/observations") ApiReply<Void> observe(@RequestHeader("Authorization")String auth,@RequestParam String environment,@RequestBody com.sma.extension.protocol.CanaryObservation observation) {
        deployments.observe(ReleaseController.token(auth),environment,observation);return ApiReply.ok(null);
    }
    @PostMapping("/nodes/revocations") ApiReply<com.sma.extension.protocol.SignedEnvelope> revocations(@RequestHeader("Authorization") String authorization,@RequestParam String environment) {
        return ApiReply.ok(deployments.revocations(ReleaseController.token(authorization),environment));
    }
    @PostMapping("/nodes/policy") ApiReply<com.sma.extension.protocol.SignedEnvelope> policy(@RequestHeader("Authorization")String authorization,@RequestParam String environment) {
        return ApiReply.ok(deployments.policy(ReleaseController.token(authorization),environment));
    }
    @PostMapping("/nodes/trust-ack") ApiReply<Void> trust(@RequestHeader("Authorization")String authorization,@RequestParam String environment,@RequestBody java.util.Map<String,String> fingerprints) {
        deployments.acknowledgeKeys(ReleaseController.token(authorization),environment,fingerprints);return ApiReply.ok(null);
    }
    @PostMapping("/workers/claim") ApiReply<WorkAssignment> claim(@RequestHeader("Authorization") String authorization,@RequestParam String environment) {
        return ApiReply.ok(validation.claim(ReleaseController.token(authorization),environment));
    }
    @GetMapping("/workers/jobs/{id}/artifact") ResponseEntity<InputStreamResource> artifact(@RequestHeader("Authorization") String authorization,@PathVariable String id) throws IOException {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).body(new InputStreamResource(validation.artifact(ReleaseController.token(authorization),id)));
    }
    @GetMapping("/workers/jobs/{id}/inputs/{artifact}") ResponseEntity<InputStreamResource> source(@RequestHeader("Authorization") String authorization,@PathVariable String id,@PathVariable String artifact) throws IOException {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).body(new InputStreamResource(validation.source(ReleaseController.token(authorization),id,artifact)));
    }
    @PostMapping("/workers/jobs/{id}/output") ApiReply<BuildOutput> output(@RequestHeader("Authorization") String authorization,@PathVariable String id,@RequestBody OutputRequest request) {
        return ApiReply.ok(validation.beginOutput(ReleaseController.token(authorization),id,request));
    }
    @PutMapping(value="/workers/jobs/{id}/output/chunks/{index}",consumes="application/octet-stream")
    ApiReply<CatalogRecords.Chunk> outputChunk(@RequestHeader("Authorization") String authorization,@PathVariable String id,@PathVariable int index,HttpServletRequest request) throws IOException {
        byte[] bytes=request.getInputStream().readNBytes(ArtifactFiles.CHUNK_BYTES+1);Checks.require(bytes.length<=ArtifactFiles.CHUNK_BYTES,"invalid-chunk-size");
        return ApiReply.ok(validation.outputChunk(ReleaseController.token(authorization),id,index,bytes));
    }
    @PostMapping("/workers/jobs/{id}/output/complete") ApiReply<CatalogRecords.Artifact> outputComplete(@RequestHeader("Authorization") String authorization,@PathVariable String id) {
        return ApiReply.ok(validation.finishOutput(ReleaseController.token(authorization),id));
    }
    @PostMapping("/workers/jobs/{id}/complete") ApiReply<ValidationJob> complete(@RequestHeader("Authorization") String authorization,@PathVariable String id,@RequestBody ValidationResult result) {
        return ApiReply.ok(validation.complete(ReleaseController.token(authorization),id,result));
    }
    public record Failure(String code) {}
    @PostMapping("/workers/jobs/{id}/fail") ApiReply<Void> fail(@RequestHeader("Authorization") String authorization,@PathVariable String id,@RequestBody Failure failure) {
        validation.fail(ReleaseController.token(authorization),id,failure.code()); return ApiReply.ok(null);
    }
    @PostMapping("/nodes/{deployment}/desired") ApiReply<NodePackage> desired(@RequestHeader("Authorization") String authorization,@PathVariable String deployment,
            @RequestParam String environment,@RequestParam(defaultValue="false")boolean activeOnly,@RequestBody com.sma.extension.protocol.ExecutionDocuments.Retention retained) {
        return ApiReply.ok(deployments.desired(ReleaseController.token(authorization),environment,deployment,retained,activeOnly));
    }
    @PostMapping("/nodes/ready") ApiReply<Deployment> ready(@RequestHeader("Authorization") String authorization,@RequestParam String environment,@RequestBody Ready ready) {
        return ApiReply.ok(deployments.ready(ReleaseController.token(authorization),environment,ready));
    }
    @GetMapping("/nodes/{deployment}/artifacts/{artifact}") ResponseEntity<InputStreamResource> download(@RequestHeader("Authorization") String authorization,@PathVariable String deployment,
            @PathVariable String artifact,@RequestParam String environment) throws IOException {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).body(new InputStreamResource(deployments.download(ReleaseController.token(authorization),environment,deployment,artifact)));
    }
}
