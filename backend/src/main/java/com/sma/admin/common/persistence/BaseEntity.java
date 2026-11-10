package com.sma.admin.common.persistence;

import java.time.Instant;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public abstract class BaseEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    @TableField(fill = FieldFill.INSERT, updateStrategy = FieldStrategy.NEVER)
    private Instant createdAt;
    @TableField(fill = FieldFill.INSERT, updateStrategy = FieldStrategy.NEVER)
    private Long createdBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;
    @TableLogic
    private Boolean deleted;
}
