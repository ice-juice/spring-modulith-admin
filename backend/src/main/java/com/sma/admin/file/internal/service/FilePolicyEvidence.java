package com.sma.admin.file.internal.service;
import org.springframework.stereotype.Component;
import com.sma.admin.common.deployment.*;
@Component
public class FilePolicyEvidence implements DeploymentEvidenceSource {
    private final FileProperties legacy;private final FileClusterStorage storage;private final UploadTaskProperties tasks;private final LocalTransferProperties transfer;
    public FilePolicyEvidence(FileProperties legacy,FileClusterStorage storage,UploadTaskProperties tasks,LocalTransferProperties transfer){
        this.legacy=legacy;this.storage=storage;this.tasks=tasks;this.transfer=transfer;
    }
    @Override public String id(){return "file.storage-policy";}
    @Override public DeploymentEvidence.Proof collect(DeploymentEvidence.Challenge challenge){
        DeploymentEvidence.requireHost();
        var fields=new DeploymentFields().text("protocol","1").number("legacy.maximum-bytes",legacy.maxBytes())
                .duration("tasks.lifetime",tasks.lifetime()).duration("tasks.retention",tasks.retention()).number("tasks.reserved-bytes",tasks.maximumReservedBytes())
                .number("tasks.active",tasks.maximumActive()).number("tasks.retained",tasks.maximumRetained()).number("tasks.minute",tasks.perMinute()).number("tasks.day",tasks.perDay())
                .number("tasks.part-bytes",tasks.partBytes()).number("tasks.parts",tasks.maximumParts()).number("tasks.concurrent-parts",tasks.maximumConcurrentParts())
                .number("transfer.concurrent",transfer.maximumConcurrent()).duration("transfer.deadline",transfer.deadline()).number("transfer.minimum-free-bytes",transfer.minimumFreeBytes());
        var resources=storage.resources();fields.number("storage.count",resources.size());
        for(int i=0;i<resources.size();i++)fields.text("storage."+i+".id",resources.get(i).id()).flag("storage."+i+".shared",resources.get(i).declaredShared());
        return fields.proof(id(),challenge); // Paths excluded. Actual cross-node filesystem access is M3b3.
    }
}
