package com.sma.admin.rule.internal.persistence;

@lombok.Getter @lombok.Setter @com.baomidou.mybatisplus.annotation.TableName("rule_version")
public class VersionEntity extends com.sma.admin.common.persistence.TenantEntity {
    private String portal;
    private Long assetId;
    private String kind;
    private Long revision;
    private String contentJson;
    private String contentHash;
    private Long dependencyId;
    private Long testRunId;
    private String caseSetHash;
}
