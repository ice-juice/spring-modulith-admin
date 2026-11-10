package com.sma.admin.common.persistence;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.Setter;

/** Every business entity extends this class. The authenticated tenant overwrites any insert-time value. */
@Getter
@Setter
public abstract class TenantEntity extends BaseEntity {
    @TableField(fill = FieldFill.INSERT, updateStrategy = FieldStrategy.NEVER)
    private Long tenantId;
}
