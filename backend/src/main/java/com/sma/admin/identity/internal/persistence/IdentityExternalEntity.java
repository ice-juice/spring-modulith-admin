package com.sma.admin.identity.internal.persistence;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sma.admin.common.persistence.TenantEntity;
import lombok.Getter;
import lombok.Setter;
@Getter @Setter @TableName("identity_external")
public class IdentityExternalEntity extends TenantEntity {
    private Long realmId;
    private Long subjectId;
    private String issuer;
    private String externalSubject;
}
