package com.sma.admin.iam.internal.persistence;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sma.admin.common.persistence.TenantEntity;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @TableName("iam_permission")
public class PermissionEntity extends TenantEntity {
    private Long parentId;
    private String portal;
    private String permissionKey;
    private String name;
    private String nameKey;
    private String type;
    private String path;
    private String icon;
    private String openMode;
    private Integer sort;
    private Boolean enabled;
    private Boolean hidden;
    private Boolean builtin;
    private Boolean superAdminDefault;
}
