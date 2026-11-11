package com.sma.admin.common.internal.persistence;
import java.time.Instant;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sma.admin.common.persistence.TenantEntity;
import lombok.Getter;
import lombok.Setter;
@Getter @Setter @TableName("common_cache_confirmation")
public class CacheConfirmationEntity extends TenantEntity {
    private Long taskId;
    private String region;
    private String nodeId;
    private String expectedProcess;
    private Long expectedEpoch;
    private String manifestHash;
    private Long requiredRevision;
    private Long requiredControl;
    private Long observedRevision;
    private Long observedControl;
    private String state;
    private Instant confirmedAt;
    private String lastError;
    private Instant lastAttemptAt;
    private Instant nextAttemptAt;
}
