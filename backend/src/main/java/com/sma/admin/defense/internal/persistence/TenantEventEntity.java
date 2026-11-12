package com.sma.admin.defense.internal.persistence;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sma.admin.common.persistence.TenantEntity;
@Getter @Setter @TableName("defense_tenant_event")
public class TenantEventEntity extends TenantEntity {
    private String eventId;
    private String correlationId;
    private Instant occurredAt;
    private Instant receivedAt;
    private Instant expiresAt;
    private String fact;
    private String category;
    private String severity;
    private String outcome;
    private String assessment;
    private Integer ruleVersion;
    private String payload;
    private String payloadHash;
    private Boolean held;
}
