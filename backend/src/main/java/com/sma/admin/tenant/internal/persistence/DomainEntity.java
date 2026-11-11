package com.sma.admin.tenant.internal.persistence;

import com.baomidou.mybatisplus.annotation.TableName;
import com.sma.admin.common.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** Globally unique binding directory. ownerTenantId is assigned only by platform ownership checks. */
@Getter @Setter @TableName("tenant_domain")
public class DomainEntity extends BaseEntity {
    private Long ownerTenantId;
    private String hostname;
    private String portal;
    private String status;
    private Boolean primaryDomain;
    private String verificationStatus;
    private String tlsStatus;
    private String provenance;
    private Long configurationVersion=1L;
    private String dnsStatus="PENDING";
    private String challengeToken;
    private java.time.Instant challengeExpiresAt;
    private java.time.Instant verifiedAt;
    private String verifiedChallengeHash;
    private java.time.Instant tlsValidUntil;
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy=com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
    private Long lastCheckId;
    private String checkStatus="IDLE";
    private String checkFailure="NONE";
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy=com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
    private java.time.Instant checkLeaseUntil;
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy=com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
    private java.time.Instant checkCompletedAt;
}
