package com.sma.admin.file.internal.persistence;
@lombok.Getter @lombok.Setter @com.baomidou.mybatisplus.annotation.TableName("file_storage_profile")
public class StorageProfileEntity extends com.sma.admin.common.persistence.TenantEntity {
    private String code;private String name;private String provider;private Boolean uploadsEnabled;private Boolean readsEnabled;
    private Long version;private Long configurationVersion;private Long credentialVersion;
}
