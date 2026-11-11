package com.sma.admin.common.internal.persistence;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sma.admin.common.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;
/** Platform allocation metadata, not tenant business data or a reusable business-table exemption. */
@Getter @Setter @TableName("common_id_block")
public class IdBlockEntity extends BaseEntity {
    private Long nextId;
}
