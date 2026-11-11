package com.sma.release.store;

import java.time.Instant;
import org.springframework.stereotype.Service;
import com.sma.release.AuditPolicy;
import com.sma.release.auth.MachineService;
import com.sma.extension.protocol.*;

@Service
public class AuditContinuity {
    private final AuditPolicy policy;
    public AuditContinuity(AuditPolicy policy){this.policy=policy;}
    public record Acknowledgement(long sequence,String hash) {public Acknowledgement {Checks.require(sequence>=0,"invalid-audit-sequence");Checks.hash(hash);}}
    public record Stored(String node,long machineRevision,Acknowledgement acknowledgement,Instant at) {}
    public void acknowledge(ReleaseDatabase.Tx tx,MachineService.Machine auditor,Acknowledgement receipt) {
        tx.auditExport(receipt.sequence(),receipt.hash(),1);
        var previous=tx.get("audit-ack",auditor.id(),Stored.class);Checks.require(previous.isEmpty() || receipt.sequence()>=previous.get().acknowledgement().sequence(),"audit-ack-rollback");
        tx.put("audit-ack",auditor.id(),new Stored(auditor.id(),auditor.revision(),receipt,tx.now()));
    }
    public void requireHealthy(ReleaseDatabase.Tx tx) {
        if(!policy.required())return;
        long head=tx.auditExport(0,"0".repeat(64),1).headSequence();
        boolean healthy=tx.list("audit-ack",Stored.class).stream().anyMatch(a -> tx.now().isBefore(a.at().plusSeconds(policy.maximumLagSeconds()))
                && head>=a.acknowledgement().sequence() && head-a.acknowledgement().sequence()<=policy.maximumUnmirroredEntries()
                && tx.get("machine",a.node(),MachineService.Machine.class).filter(m -> m.enabled() && m.kind()==MachineService.Kind.AUDITOR && m.revision()==a.machineRevision() && m.environments().contains("security")).isPresent());
        Checks.require(healthy,"external-audit-unavailable");
    }
}
