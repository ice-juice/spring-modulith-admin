package com.sma.admin.rule.internal.persistence;

@lombok.Getter @lombok.Setter @com.baomidou.mybatisplus.annotation.TableName("rule_migration_batch")
public class MigrationBatchEntity extends com.sma.admin.common.persistence.TenantEntity {
    private String portal;
    private String operationKey;
    private String requestHash;
    private String state;
    private String reportJson;
}
