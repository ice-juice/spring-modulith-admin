package com.sma.admin.identity.internal.persistence;
import com.baomidou.mybatisplus.annotation.*;
import com.sma.admin.common.persistence.TenantEntity;
import lombok.Getter;
import lombok.Setter;
@Getter @Setter @TableName("identity_login_transaction")
public class IdentityLoginTransactionEntity extends TenantEntity {
    private String intent;
    private String operationJson;
    private String stateHash;
    private String proofHash;
    private String snapshotJson;
    private String callbackUri;
    private String origin;
    private String flow;
    private String pkceChallenge;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String nonceEnvelope;
    private String status;
    private String claimId;
    private String resultCode;
    private Long sessionId;
    private java.time.Instant expiresAt;
    private java.time.Instant retainUntil;
}
