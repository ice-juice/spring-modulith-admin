package com.sma.admin.rule.internal.persistence;

@lombok.Getter @lombok.Setter @com.baomidou.mybatisplus.annotation.TableName("rule_execution")
public class ExecutionEntity extends com.sma.admin.common.persistence.TenantEntity {
    private String portal;
    private Long instanceId;
    private Long instanceRevision;
    private Long releaseRevision;
    private Long templateVersionId;
    private String status;
    private String contentHash;
    private String inputHash;
    private String traceJson;
    private String problemsJson;
    private Long durationMicros;
}
