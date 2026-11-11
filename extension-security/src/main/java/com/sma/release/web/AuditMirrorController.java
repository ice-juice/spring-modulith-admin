package com.sma.release.web;

import org.springframework.web.bind.annotation.*;
import com.sma.extension.protocol.AuditStream;
import com.sma.release.auth.MachineService;
import com.sma.release.store.ReleaseDatabase;

/** Pull-only identity, distinct from builders/nodes. Its security environment grants global release audit metadata access. */
@RestController @RequestMapping("/release/v1/machines/auditors")
public class AuditMirrorController {
    private final ReleaseDatabase database;private final MachineService machines;private final com.sma.release.store.AuditContinuity continuity;
    public AuditMirrorController(ReleaseDatabase database,MachineService machines,com.sma.release.store.AuditContinuity continuity){this.database=database;this.machines=machines;this.continuity=continuity;}
    @PostMapping("/ack") ApiReply<Void> ack(@RequestHeader("Authorization")String auth,@RequestBody com.sma.release.store.AuditContinuity.Acknowledgement receipt) {
        return ApiReply.ok(database.transaction(tx -> {var auditor=machines.authenticate(tx,ReleaseController.token(auth),MachineService.Kind.AUDITOR,"security");continuity.acknowledge(tx,auditor,receipt);return null;}));
    }
    @GetMapping("/audit") ApiReply<AuditStream.Batch> export(@RequestHeader("Authorization")String auth,@RequestParam long after,@RequestParam String expectedHash,@RequestParam(defaultValue="100")int limit) {
        return ApiReply.ok(database.transaction(tx -> {machines.authenticate(tx,ReleaseController.token(auth),MachineService.Kind.AUDITOR,"security");return tx.auditExport(after,expectedHash,limit);}));
    }
}
