package com.sma.admin.tenant.internal.persistence;

import com.baomidou.mybatisplus.annotation.TableName;
import com.sma.admin.common.persistence.TenantEntity;
import lombok.Getter;
import lombok.Setter;

/** Native verification receipts are owned by the platform actor's tenant; target ownership is a separate directory reference. */
@Getter @Setter @TableName("tenant_domain_check")
public class DomainCheckEntity extends TenantEntity {
    private Long ownerTenantId;
    private Long domainId;
    private String operationId;
    private String intentHash;
    private Long domainVersion;
    private String challengeHash;
    private String status;
    private String failureCode="NONE";
    private Boolean ownershipVerified=false;
    private Boolean dnsReady=false;
    private Boolean tlsReady=false;
    private java.time.Instant tlsValidUntil;
    private String addressesJson="[]";
    private String fingerprintsJson="[]";
    private java.time.Instant leaseUntil;
    private java.time.Instant completedAt;
}
