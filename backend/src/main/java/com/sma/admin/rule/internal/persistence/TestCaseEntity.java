package com.sma.admin.rule.internal.persistence;

@lombok.Getter @lombok.Setter @com.baomidou.mybatisplus.annotation.TableName("rule_testcase")
public class TestCaseEntity extends com.sma.admin.common.persistence.TenantEntity {
    private String portal;
    private Long templateId;
    private Long revision;
    private Boolean enabled;
    private String payloadJson;
}
