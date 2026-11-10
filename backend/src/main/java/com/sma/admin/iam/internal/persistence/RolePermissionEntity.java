package com.sma.admin.iam.internal.persistence;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sma.admin.common.persistence.TenantEntity;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @TableName("iam_role_permission")
public class RolePermissionEntity extends TenantEntity {
    private Long roleId;
    private Long permissionId;
}
