package com.sma.admin.mail.internal.persistence;

import com.baomidou.mybatisplus.annotation.*;
import com.sma.admin.common.persistence.TenantEntity;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @TableName("mail_delivery")
public class MailDeliveryEntity extends TenantEntity {
    private String guardOwner;
    private String guardReference;
    private String application;
    private String scene;
    private String idempotencyKey;
    private String requestFingerprint;
    private Long templateId;
    private Long templateVersion;
    private Long profileId;
    private Long profileVersion;
    private Long credentialVersion;
    private String actorKind;
    private String actorSubject;
    private Long actorRealm;
    private Long actorClient;
    private String recipientMasked;
    private String recipientKey;
    private String receiptState;
    @TableField(updateStrategy = FieldStrategy.ALWAYS) private String payloadEnvelope;
    private String state;
    private Integer attempts;
    private Long fence;
    @TableField(updateStrategy = FieldStrategy.ALWAYS) private String leaseOwner;
    @TableField(updateStrategy = FieldStrategy.ALWAYS) private java.time.Instant leaseUntil;
    private java.time.Instant nextAttemptAt;
    private java.time.Instant expiresAt;
    private String messageId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS) private String lastResult;
}

