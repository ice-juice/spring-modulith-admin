package com.sma.admin.openapi.internal.persistence;
import com.baomidou.mybatisplus.annotation.*;
import com.sma.admin.common.persistence.TenantEntity;
import lombok.*;
@Getter @Setter @TableName("openapi_application")
public class ApplicationEntity extends TenantEntity {
 private String portal;private Long deptId;private String name;private Integer type;private String appId;private String secretCipher;private Boolean enabled;private Boolean signatureRequired;private Long credentialVersion;
 @TableField(updateStrategy=FieldStrategy.ALWAYS) private String description;
 @TableField(updateStrategy=FieldStrategy.ALWAYS) private String targetType;
 @TableField(updateStrategy=FieldStrategy.ALWAYS) private String targetId;
 @TableField(updateStrategy=FieldStrategy.ALWAYS) private String targetName;
}
