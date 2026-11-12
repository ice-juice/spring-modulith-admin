package com.sma.admin.defense.internal.persistence;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sma.admin.common.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;
@Getter @Setter @TableName("defense_capacity")
public class CapacityEntity extends BaseEntity {
    private Long records;
}
