package com.sma.admin.iam.internal.persistence;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sma.admin.common.persistence.TenantEntity;
import lombok.Getter;
import lombok.Setter;
@Getter @Setter @TableName("iam_navigation")
public class NavigationEntity extends TenantEntity {
    private String portal; private String name; private String icon; private Integer sort; private Boolean enabled;
}
