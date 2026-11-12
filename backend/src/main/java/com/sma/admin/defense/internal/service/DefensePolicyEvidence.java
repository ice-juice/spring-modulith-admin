package com.sma.admin.defense.internal.service;
import org.springframework.stereotype.Component;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.sma.admin.common.deployment.*;
@Component @EnableConfigurationProperties(DefenseStorageProperties.class)
public class DefensePolicyEvidence implements DeploymentEvidenceSource {
    private final DefenseStorageProperties settings;private final DefenseCollectionProperties collection;private final DefenseMaintenanceProperties maintenance;
    @org.springframework.beans.factory.annotation.Autowired
    public DefensePolicyEvidence(DefenseStorageProperties settings,DefenseCollectionProperties collection,DefenseMaintenanceProperties maintenance){this.settings=settings;this.collection=collection;this.maintenance=maintenance;}
    public DefensePolicyEvidence(DefenseStorageProperties settings,DefenseCollectionProperties collection){this(settings,collection,new DefenseMaintenanceProperties(null,null,null));}
    public DefensePolicyEvidence(DefenseStorageProperties settings){this(settings,new DefenseCollectionProperties(false,null,null,null,null,null,null,null,null,null));}
    @Override public String id(){return "defense.storage-policy";}
    @Override public DeploymentEvidence.Proof collect(DeploymentEvidence.Challenge challenge){
        DeploymentEvidence.requireHost();
        return new DeploymentFields().text("protocol","3").number("platform-records",settings.maximumPlatformRecords())
            .number("tenant-records",settings.maximumTenantRecords()).number("tenant-total",settings.maximumTenantTotalRecords())
            .duration("retention",settings.retention()).number("cleanup-batch",settings.cleanupBatch())
            .flag("collection",collection.enabled()).number("memory-records",collection.memoryRecords()).number("disk-records",collection.diskRecords())
            .number("disk-bytes",collection.diskBytes()).number("per-second",collection.perSecond()).number("batch",collection.batch())
            .duration("retry-delay",collection.retryDelay()).flag("maintenance",maintenance.enabled())
            .duration("maintenance-interval",maintenance.interval()).number("maintenance-tenants",maintenance.tenantBatch())
            .proof(id(),challenge); // Paths, scope and per-node encryption key excluded; spool is node-owned.
    }
}
