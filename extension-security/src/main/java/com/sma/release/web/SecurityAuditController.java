package com.sma.release.web;

import java.util.List;
import org.springframework.web.bind.annotation.*;
import com.sma.extension.protocol.Checks;
import com.sma.release.auth.IdentityService;
import com.sma.release.crypto.SigningService;
import com.sma.release.store.ReleaseDatabase;

@RestController @RequestMapping("/release/v1/security")
public class SecurityAuditController {
    private final IdentityService identities;private final ReleaseDatabase database;private final SigningService signing;
    public SecurityAuditController(IdentityService identities,ReleaseDatabase database,SigningService signing){this.identities=identities;this.database=database;this.signing=signing;}
    @GetMapping("/audit") public ApiReply<List<ReleaseDatabase.AuditEntry>> audit(@RequestHeader("Authorization")String authorization,
            @RequestParam(defaultValue="0")long after,@RequestParam(defaultValue="100")int limit) {
        return ApiReply.ok(database.transaction(tx -> {Checks.require(identities.principal(tx,ReleaseController.token(authorization)).securityAdmin(),"forbidden");return tx.auditPage(after,limit);}));
    }
    @GetMapping("/public-keys") public ApiReply<List<SigningService.PublicTrustKey>> keys(@RequestHeader("Authorization")String authorization) {
        return ApiReply.ok(database.transaction(tx -> {Checks.require(identities.principal(tx,ReleaseController.token(authorization)).securityAdmin(),"forbidden");return signing.publicKeys(tx);}));
    }
}
