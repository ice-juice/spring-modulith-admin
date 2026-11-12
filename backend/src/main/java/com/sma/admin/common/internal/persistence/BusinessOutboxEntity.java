package com.sma.admin.common.internal.persistence;

import com.baomidou.mybatisplus.annotation.TableName;
import com.sma.admin.common.persistence.TenantEntity;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

/** Intent committed with a business resource's local transaction. */
@Getter @Setter @TableName("common_business_outbox")
public class BusinessOutboxEntity extends TenantEntity {
    private String eventType;
    private String keyHash;
    private String payloadHash;
    private byte[] payload;
    private String state;
    private Long deliveryFence;
    private Instant leaseUntil;
    private Integer attempts;
}
