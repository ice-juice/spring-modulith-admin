package com.sma.admin.rule.internal.persistence;

@lombok.Getter @lombok.Setter @com.baomidou.mybatisplus.annotation.TableName("rule_asset")
public class AssetEntity extends com.sma.admin.common.persistence.TenantEntity {
    private String portal;
    private String kind;
    private String code;
    private String name;
    private Long referenceId;
    private String state;
    private String draftJson;
    private Long revision;
    private Long releaseRevision;
    private Long activeVersionId;
    private String trialHash;
}
