package com.sma.admin.iam.internal.persistence;

import com.baomidou.mybatisplus.annotation.TableName;
import com.sma.admin.common.persistence.TenantEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("iam_department")
public class DepartmentEntity extends TenantEntity {
    private Long parentId;
    private String name;
    private String code;
    private String path;
    private Integer sort;
    private Boolean enabled;
}
