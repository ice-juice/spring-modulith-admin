package com.sma.admin.common.internal.persistence;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sma.admin.common.persistence.TenantEntity;
import lombok.Getter;
import lombok.Setter;
@Getter @Setter @TableName("common_cache_task_item")
public class CacheTaskItemEntity extends TenantEntity {
    private Long taskId;
    private Integer ordinal;
    private String region;
    private String state;
    private Long dataRevision;
    private Long controlVersion;
    private String failureCode;
    private Integer attempts;
    private String warmCursor;
    private Long warmReads;
    private String warmProcess;
    private java.time.Instant warmStartedAt;
    private String nativeProcess;
    private java.time.Instant nativeDeadline;
    private java.time.Instant nativeStartedAt;
    private java.time.Instant nativeCompletedAt;
    private Long nativeAffected;
    private Long nativeRemaining;
}
