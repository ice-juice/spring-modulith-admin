package com.sma.admin.rule.internal.persistence;

@lombok.Getter @lombok.Setter @com.baomidou.mybatisplus.annotation.TableName("rule_testrun")
public class TestRunEntity extends com.sma.admin.common.persistence.TenantEntity {
    private String portal;
    private Long templateId;
    private Long versionId;
    private String contentHash;
    private String caseSetHash;
    private Boolean passed;
    private String reportJson;
}
