package com.sma.admin.file.internal.persistence;
@lombok.Getter @lombok.Setter @com.baomidou.mybatisplus.annotation.TableName("file_storage_revision")
public class StorageRevisionEntity extends com.sma.admin.common.persistence.TenantEntity {
    private Long profileId;private Long configurationVersion;private Long credentialVersion;
    private String localRoot;private String region;private String endpoint;private String bucket;private String objectPrefix;
    private String applicationsJson;private String secretEnvelope;private String credentialHint;private java.time.Instant credentialExpiresAt;
}
