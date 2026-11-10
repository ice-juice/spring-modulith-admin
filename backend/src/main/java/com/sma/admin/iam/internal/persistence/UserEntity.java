package com.sma.admin.iam.internal.persistence;
import java.time.Instant;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sma.admin.common.persistence.TenantEntity;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @TableName("iam_user")
public class UserEntity extends TenantEntity {
    private String username;
    private String name;
    private String passwordHash;
    private String portal;
    private Long deptId;
    private String email;
    private String mobile;
    private String gender;
    private Boolean enabled;
    private Boolean superAdmin;
    private String locale;
    private String timeZone;
    private Instant passwordChangedAt;
    private Long credentialVersion;
}
