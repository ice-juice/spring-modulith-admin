package com.sma.admin.identity.internal.persistence;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sma.admin.common.persistence.TenantEntity;
import lombok.Getter;
import lombok.Setter;
@Getter @Setter @TableName("identity_client_revision")
public class IdentityClientRevisionEntity extends TenantEntity {
    private Long clientId;
    private Long configurationVersion;
    private Long credentialVersion;
    private Boolean googleEnabled;
    private String googleClientId;
    private String secretEnvelope;
    private String callbacksJson;
    private String originsJson;
    private Integer accessTokenSeconds;
    private Integer refreshTokenSeconds;
    private String cookieSameSite;
    private String googleAuthorizedParty;
}
