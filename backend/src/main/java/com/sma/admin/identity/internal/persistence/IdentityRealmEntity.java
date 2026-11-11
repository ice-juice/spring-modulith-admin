package com.sma.admin.identity.internal.persistence;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sma.admin.common.persistence.TenantEntity;
import lombok.Getter;
import lombok.Setter;
@Getter @Setter @TableName("identity_realm")
public class IdentityRealmEntity extends TenantEntity {
    private String code;
    private String name;
    private Boolean enabled;
    private Boolean registrationEnabled;
    private Boolean passwordEnabled;
    private Long version;
    private Long securityVersion;
}
