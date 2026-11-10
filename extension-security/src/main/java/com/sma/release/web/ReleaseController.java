package com.sma.release.web;

import java.io.IOException;
import java.util.List;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import com.sma.extension.protocol.*;
import com.sma.release.auth.IdentityService;
import com.sma.release.auth.SecurityRecords;
import com.sma.release.catalog.CatalogService;
import com.sma.release.catalog.CatalogRecords;
import com.sma.release.catalog.ArtifactFiles;

/** Independent control-plane API. Every protected handler resolves and checks the opaque security-service session. */
@RestController
@RequestMapping("/release/v1")
public class ReleaseController {
    private final IdentityService identities;
    private final CatalogService catalog;
    public ReleaseController(IdentityService identities, CatalogService catalog) { this.identities = identities; this.catalog = catalog; }
    public record Login(String username, String password, String otp) { @Override public String toString() { return "Login[redacted]"; } }
    public record EnrollmentRequest(String username, String password, String enrollmentToken, String otp) { @Override public String toString() { return "EnrollmentRequest[redacted]"; } }
    public record Proof(String challengeId, String operationPassword, String otp) { @Override public String toString() { return "Proof[redacted]"; } }
    @PostMapping("/auth/login") ApiReply<SecurityRecords.LoginTicket> login(@RequestBody Login input, HttpServletRequest request) {
        return ApiReply.ok(identities.login(input.username(), input.password(), input.otp(), request.getRemoteAddr()));
    }
    @PostMapping("/auth/enrollment/begin") ApiReply<SecurityRecords.Enrollment> begin(@RequestBody EnrollmentRequest input, HttpServletRequest request) {
        return ApiReply.ok(identities.beginEnrollment(input.username(), input.password(), input.enrollmentToken(), request.getRemoteAddr()));
    }
    @PostMapping("/auth/enrollment/complete") ApiReply<List<String>> complete(@RequestBody EnrollmentRequest input, HttpServletRequest request) {
        return ApiReply.ok(identities.completeEnrollment(input.username(), input.password(), input.enrollmentToken(), input.otp(), request.getRemoteAddr()));
    }
    @GetMapping("/auth/session") ApiReply<SecurityRecords.Principal> session(@RequestHeader("Authorization") String authorization) {
        return ApiReply.ok(identities.principal(token(authorization)));
    }
    @PostMapping("/auth/logout") ApiReply<Void> logout(@RequestHeader("Authorization") String authorization) {
        identities.logout(token(authorization)); return ApiReply.ok(null);
    }
    @PostMapping("/challenges") ApiReply<SecurityRecords.ChallengeView> challenge(@RequestHeader("Authorization") String authorization, @RequestBody ActionIntent intent) {
        return ApiReply.ok(identities.challenge(token(authorization), intent));
    }
    @PostMapping("/authorizations") ApiReply<SecurityRecords.GrantView> authorize(@RequestHeader("Authorization") String authorization, @RequestBody Proof proof) {
        return ApiReply.ok(identities.authorize(token(authorization), proof.challengeId(), proof.operationPassword(), proof.otp()));
    }
    @GetMapping("/policy") ApiReply<ResourcePolicy> policy(@RequestHeader("Authorization") String authorization) {
        identities.principal(token(authorization)); return ApiReply.ok(catalog.policy());
    }
    @GetMapping("/groups") ApiReply<List<CatalogRecords.Group>> groups(@RequestHeader("Authorization") String authorization) { return ApiReply.ok(catalog.groups(token(authorization))); }
    @GetMapping("/groups/{group}/plugins") ApiReply<List<CatalogRecords.Plugin>> plugins(@RequestHeader("Authorization") String authorization, @PathVariable String group) { return ApiReply.ok(catalog.plugins(token(authorization), group)); }
    @GetMapping("/plugins/{plugin}/artifacts") ApiReply<List<CatalogRecords.Artifact>> artifacts(@RequestHeader("Authorization") String authorization, @PathVariable String plugin, @RequestParam String environment) { return ApiReply.ok(catalog.artifacts(token(authorization), plugin, environment)); }
    @PostMapping("/commands") ApiReply<CatalogRecords.ActionReceipt> command(@RequestHeader("Authorization") String authorization, @RequestBody CatalogRecords.Command command) { return ApiReply.ok(catalog.execute(token(authorization), command)); }
    @GetMapping("/actions/{id}") ApiReply<CatalogRecords.ActionReceipt> action(@RequestHeader("Authorization") String authorization, @PathVariable String id) { return ApiReply.ok(catalog.action(token(authorization), id)); }
    @PutMapping(value="/uploads/{upload}/files/{file}/chunks/{chunk}", consumes="application/octet-stream")
    ApiReply<CatalogRecords.Chunk> chunk(@RequestHeader("Authorization") String authorization, @PathVariable String upload,
            @PathVariable int file, @PathVariable int chunk, HttpServletRequest request) throws IOException {
        String session = token(authorization); identities.principal(session);
        byte[] bytes = request.getInputStream().readNBytes(ArtifactFiles.CHUNK_BYTES+1);
        Checks.require(bytes.length <= ArtifactFiles.CHUNK_BYTES, "invalid-chunk-size");
        return ApiReply.ok(catalog.uploadChunk(session, upload, file, chunk, bytes));
    }
    @PostMapping("/uploads/{upload}/complete") ApiReply<List<CatalogRecords.Artifact>> completeUpload(@RequestHeader("Authorization") String authorization, @PathVariable String upload) {
        return ApiReply.ok(catalog.completeUpload(token(authorization), upload));
    }
    public static String token(String authorization) {
        Checks.require(authorization != null && authorization.startsWith("Bearer ") && authorization.length() <= 140, "unauthenticated");
        return authorization.substring(7);
    }
}
