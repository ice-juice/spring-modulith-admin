package com.sma.admin.common.internal.persistence;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.sma.admin.common.persistence.TenantEntity;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @TableName("common_cache_revision")
public class CacheRevisionEntity extends TenantEntity {
    private String region;
    private Long revision;
    private Long controlVersion;
    private Long statsEpoch;
    private Boolean paused;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private Long policyTtlMillis;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private Long policyNegativeTtlMillis;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private Long policyMaxEntries;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private Long policyMaxBytes;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private Integer policyMaxEntryBytes;
}
