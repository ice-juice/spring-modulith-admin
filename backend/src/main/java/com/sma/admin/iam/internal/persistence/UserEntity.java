package com.sma.admin.iam.internal.persistence;
import java.time.Instant;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.sma.admin.common.persistence.TenantEntity;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @TableName("iam_user")
public class UserEntity extends TenantEntity {
    private String username;
    private String name;
    private String passwordHash;
    private String portal;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private Long deptId;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String email;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String mobile;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String gender;
    private Boolean enabled;
    private Boolean superAdmin;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String locale;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String timeZone;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private Long avatarFileId;
    private Instant passwordChangedAt;
    private Long credentialVersion;
}
