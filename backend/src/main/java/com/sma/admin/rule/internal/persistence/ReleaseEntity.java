package com.sma.admin.rule.internal.persistence;

@lombok.Getter @lombok.Setter @com.baomidou.mybatisplus.annotation.TableName("rule_release")
public class ReleaseEntity extends com.sma.admin.common.persistence.TenantEntity {
    private String portal;
    private Long assetId;
    private Long versionId;
    private Long releaseRevision;
    private String action;
    private String operationKey;
    private String fingerprint;
}
