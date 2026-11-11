package com.sma.admin.identity.internal.persistence;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sma.admin.common.persistence.TenantEntity;
import lombok.Getter;
import lombok.Setter;
@Getter @Setter @TableName("identity_client")
public class IdentityClientEntity extends TenantEntity {
    private Long realmId;
    private String code;
    private String name;
    private String application;
    private String platform;
    private String loginHandle;
    private Boolean enabled;
    private Long version;
    private Long securityVersion;
    private Long configurationVersion;
    private Long credentialVersion;
}
