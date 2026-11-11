package com.sma.admin.identity.internal.persistence;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sma.admin.common.persistence.TenantEntity;
import lombok.Getter;
import lombok.Setter;
@Getter @Setter @TableName("identity_subject")
public class IdentitySubjectEntity extends TenantEntity {
    private Long realmId;
    private Boolean enabled;
    private Long securityVersion;
}
