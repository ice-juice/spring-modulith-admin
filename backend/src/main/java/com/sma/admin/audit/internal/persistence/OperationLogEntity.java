package com.sma.admin.audit.internal.persistence;
import java.time.Instant;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sma.admin.common.persistence.TenantEntity;
import lombok.Getter;
import lombok.Setter;
@Getter @Setter @TableName("audit_operation_log")
public class OperationLogEntity extends TenantEntity {
 private String eventId;private Long userId;private Long deptId;private String username;private String portal;private String action;private String messageKey;
 private String messageArgs;private String defaultMessage;private String objectId;private String clientPath;private String requestPath;private Integer resultCode;
 private String errorKey;private Long durationMs;private String ip;private String userAgent;private Instant occurredAt;
}
