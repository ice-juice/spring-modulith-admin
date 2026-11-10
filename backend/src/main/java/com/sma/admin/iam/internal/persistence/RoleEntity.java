package com.sma.admin.iam.internal.persistence;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sma.admin.common.persistence.TenantEntity;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@TableName("iam_role")
public class RoleEntity extends TenantEntity {
    private Long parentId;
    private String portal;
    private String name;
    private String code;
    private Integer sort;
    private Boolean enabled;
    private String dataScope;
    private String description;
}
