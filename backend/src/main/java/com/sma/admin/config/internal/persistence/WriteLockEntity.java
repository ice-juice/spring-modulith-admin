package com.sma.admin.config.internal.persistence;
@com.baomidou.mybatisplus.annotation.TableName("config_write_lock")
@lombok.Getter @lombok.Setter
public class WriteLockEntity extends com.sma.admin.common.persistence.TenantEntity {
    private Long parameterRevision;
    private Long dictionaryRevision;
}
