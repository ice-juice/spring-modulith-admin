package com.sma.admin.extension.internal.persistence;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import lombok.Getter;
import lombok.Setter;

/** Platform node checkpoint, deliberately tenantless. Never exposes tenant business data or grants execution authority. */
@Getter @Setter
@TableName("extension_runtime_state")
public class RuntimeStateEntity {
    @TableId(type=IdType.INPUT) private String id;
    private Long revision;
}
