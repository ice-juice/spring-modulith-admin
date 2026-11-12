package com.sma.admin.defense.internal.persistence;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sma.admin.common.persistence.TenantEntity;
import lombok.Getter;
import lombok.Setter;
@Getter @Setter @TableName("defense_tenant_capacity")
public class TenantCapacityEntity extends TenantEntity {
    private Long records;
}
