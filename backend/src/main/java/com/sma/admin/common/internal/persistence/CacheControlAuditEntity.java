package com.sma.admin.common.internal.persistence;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sma.admin.common.persistence.TenantEntity;
import lombok.Getter;
import lombok.Setter;
@Getter @Setter @TableName("common_cache_control_audit")
public class CacheControlAuditEntity extends TenantEntity {
    private String region;
    private Long controlVersion;
    private String action;
    private String reason;
    private String portal;
    private String beforeState;
    private String afterState;
}
