package com.sma.admin.identity.internal.persistence;
@lombok.Getter @lombok.Setter @com.baomidou.mybatisplus.annotation.TableName("identity_email_challenge")
public class IdentityEmailChallengeEntity extends com.sma.admin.common.persistence.TenantEntity {
    private String handleHash;private String proofHash;private String snapshotJson;private Long emailConfigurationId;private Long emailConfigurationVersion;
    private String language;private String purpose;private String origin;private String targetIndex;
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy=com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS) private String targetEnvelope;
    private Long subjectId;private Long subjectGeneration;
    private String bindingActorJson;
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy=com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS) private String codeFingerprint;
    private String status;private Integer failures;private Long deliveryId;
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy=com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS) private String grantHash;
    private java.time.Instant expiresAt;private java.time.Instant grantExpiresAt;private java.time.Instant retainUntil;
}
